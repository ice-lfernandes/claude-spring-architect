///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves the two Checkstyle configs `project-bootstrap` writes into every project
// parse, and enforce what they claim — `checkstyle.xml.example` over production code,
// `checkstyle-test.xml.example` over `src/test`. A restricted identifier (`record`,
// `permits`) must fail both; a clean file must pass both.
//
// Why this test exists: the first version of the `IllegalIdentifierName` module relied on
// Checkstyle's default `format`, which a documentation page said rejects `record`. On the
// Checkstyle the projects actually pin (14.x) the default rejects `var` only: the module
// passed a test tree holding four `record` variables with 0 violations, and nothing in this
// repository could notice — the config is a template, it never runs here
// (lessons-learned-017 § 8, decision 0097). Asserting on real Checkstyle output is the only
// check that reads the value the way the build will.
//
// Why the latest release and not a pinned one: `project-bootstrap` resolves the Checkstyle
// version from Maven Central at generation time, never from memory (@CLAUDE.md invariant 8),
// so a new project gets the current release. This test resolves it the same way. The
// self-contained `-all` jar comes from the project's GitHub release, the artifact Checkstyle
// publishes for command-line use; Maven Central carries only the thin jar.

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class CheckstyleConfigTest {

    static final Path TEMPLATES = Paths.get(".claude/skills/project-bootstrap/templates");
    static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public static void main(String[] args) throws Exception {
        String version = latestCheckstyle();
        Path jar = download(version);
        Path work = Files.createTempDirectory("checkstyle-config-test");

        Path mainConfig = copy(TEMPLATES.resolve("checkstyle.xml.example"), work.resolve("checkstyle.xml"));
        Path testConfig = copy(TEMPLATES.resolve("checkstyle-test.xml.example"), work.resolve("checkstyle-test.xml"));

        // Written under src/ so the main config's exclusion filter (target/, generated-sources/)
        // does not skip them. Each one respects every size and naming limit of the main config,
        // so the only violation it can raise is the one it exists for.
        Path src = Files.createDirectories(work.resolve("src/example"));
        Path clean = write(src.resolve("Clean.java"), """
                package example;

                /** Holds one value. */
                public final class Clean {

                    private final int received;

                    public Clean(int received) {
                        this.received = received;
                    }

                    public int received() {
                        return received;
                    }
                }
                """);
        Path record = write(src.resolve("RecordName.java"), """
                package example;

                /** Names a local after a restricted identifier. */
                public final class RecordName {

                    private RecordName() {
                    }

                    public static int count() {
                        var record = 1;
                        return record;
                    }
                }
                """);
        Path permits = write(src.resolve("PermitsName.java"), """
                package example;

                /** Names a parameter after a restricted identifier. */
                public final class PermitsName {

                    private PermitsName() {
                    }

                    public static int twice(int permits) {
                        return permits + permits;
                    }
                }
                """);

        int failures = 0;
        for (Path config : List.of(mainConfig, testConfig)) {
            String name = config.getFileName().toString();
            failures += expectClean(jar, config, clean, name);
            failures += expectRestricted(jar, config, record, "record", name);
            failures += expectRestricted(jar, config, permits, "permits", name);
        }

        if (failures > 0) {
            System.out.println("❌ " + failures + " Checkstyle config assertion(s) failed (Checkstyle " + version + ").");
            System.exit(1);
        }
        System.out.println("✅ Both Checkstyle configs parse on Checkstyle " + version
                + ", pass a clean file, and reject `record` and `permits` as names.");
    }

    static int expectClean(Path jar, Path config, Path file, String configName) throws Exception {
        Run run = checkstyle(jar, config, file);
        if (run.exit == 0 && !run.out.contains("[ERROR]")) {
            System.out.println("  ✓ " + configName + " passes " + file.getFileName());
            return 0;
        }
        System.out.println("  ✗ " + configName + " should pass " + file.getFileName() + " — exit " + run.exit);
        System.out.println(indent(run.out));
        return 1;
    }

    static int expectRestricted(Path jar, Path config, Path file, String name, String configName) throws Exception {
        Run run = checkstyle(jar, config, file);
        boolean flagged = run.exit != 0
                && run.out.contains("[IllegalIdentifierName]")
                && run.out.contains("'" + name + "'");
        if (flagged) {
            System.out.println("  ✓ " + configName + " rejects `" + name + "` in " + file.getFileName());
            return 0;
        }
        System.out.println("  ✗ " + configName + " should reject `" + name + "` in " + file.getFileName()
                + " with IllegalIdentifierName — exit " + run.exit
                + ". A default `format` lets it through: keep the explicit value.");
        System.out.println(indent(run.out));
        return 1;
    }

    record Run(int exit, String out) {}

    static Run checkstyle(Path jar, Path config, Path file) throws Exception {
        Process p = new ProcessBuilder(javaBin(), "-jar", jar.toString(), "-c", config.toString(), file.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(p.waitFor(), out);
    }

    static String latestCheckstyle() throws Exception {
        String metadata = get("https://repo1.maven.org/maven2/com/puppycrawl/tools/checkstyle/maven-metadata.xml");
        Matcher m = Pattern.compile("<release>([^<]+)</release>").matcher(metadata);
        if (!m.find()) throw new IllegalStateException("no <release> in Checkstyle's maven-metadata.xml");
        return m.group(1);
    }

    static Path download(String version) throws Exception {
        Path jar = Files.createTempFile("checkstyle-" + version + "-all", ".jar");
        String url = "https://github.com/checkstyle/checkstyle/releases/download/checkstyle-"
                + version + "/checkstyle-" + version + "-all.jar";
        HttpResponse<Path> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofFile(jar));
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode() + " for " + url);
        return jar;
    }

    static String get(String url) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode() + " for " + url);
        return r.body();
    }

    static Path copy(Path from, Path to) throws IOException {
        return Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    static Path write(Path file, String content) throws IOException {
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    static String javaBin() {
        return Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    }

    static String indent(String s) {
        return s.lines().map(l -> "      " + l).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
