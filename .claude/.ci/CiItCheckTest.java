///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves the "integration tests actually ran" step of the generated project's CI
// (`project-bootstrap/templates/ci.yml.example` and `ci-gradle.yml.example`) reads the reports
// the build tools really write. It runs on a fresh Initializr project per build tool, wired with
// the IT block of `pom.parent.xml.example` or `build.gradle.parent.example`:
//   - green: `ForwardedHeadersIT` alone, every test in `@Nested` classes. Failsafe writes
//     `Tests run: 0` in the enclosing class's `.txt`, and Gradle names every report
//     `<Outer>$<Nested>`. Both once turned this step red on a build where every IT passed;
//   - red, by name: the same plus `GuardedIT`, a class switched off by the `@EnabledIf` Docker
//     guard. Both tools report it as skipped, never as zero tests, so the old step let it
//     through green.
//
// The step is read out of each template on every run and executed with the runner's flags
// (`bash --noprofile --norc -eo pipefail`), so the YAML cannot drift from what is tested.
// Design: .claude/decisions/0127-ci-it-check-reads-aggregates-and-fails-on-skip.md

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public class CiItCheckTest {

    static final Path BOOTSTRAP = Paths.get(".claude/skills/project-bootstrap/templates");
    static final Path TRANSPORT = Paths.get(".claude/skills/transport-security-setup/templates");
    static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
    static final Pattern FAILSAFE = Pattern.compile(
            "<plugin>\\s*<groupId>org\\.apache\\.maven\\.plugins</groupId>\\s*"
                    + "<artifactId>maven-failsafe-plugin</artifactId>.*?</plugin>", Pattern.DOTALL);
    static final String STEP_NAME = "- name: integration tests actually ran";
    static final String BASE_YML = "spring:\n  application:\n    name: minhaapi\n\n";
    static final String GUARDED_IT = """
            package com.exemplo.minhaapi;

            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.condition.EnabledIf;

            @EnabledIf("dockerAvailable")
            class GuardedIT {

                static boolean dockerAvailable() {
                    return false;
                }

                @Test
                void one() {
                }

                @Test
                void two() {
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        int failures = 0;
        failures += tool("Maven", "maven-project", "ci.yml.example",
                List.of("./mvnw", "-B", "-q", "verify"), CiItCheckTest::wireMaven);
        failures += tool("Gradle", "gradle-project", "ci-gradle.yml.example",
                List.of("./gradlew", "--no-daemon", "-q", "build"), CiItCheckTest::wireGradle);
        if (failures > 0) {
            System.out.println("❌ " + failures + " case(s) failed — the generated CI's IT check misreads the build's reports.");
            System.exit(1);
        }
        System.out.println("✅ Both build tools: @Nested ITs pass the IT check, a Docker-guard skip fails it by name.");
    }

    interface Wiring {
        void wire(Path app) throws IOException;
    }

    static int tool(String label, String type, String ci, List<String> build, Wiring wiring) throws Exception {
        Path step = Files.createTempFile("it-check-step", ".sh");
        Files.writeString(step, extractStep(read(BOOTSTRAP, ci)), StandardCharsets.UTF_8);

        Path app = initializrProject(Files.createTempDirectory("ci-it-check-test"), type);
        wiring.wire(app);
        Path yml = app.resolve("src/main/resources/application.yml");
        Files.writeString(yml, BASE_YML + read(TRANSPORT, "application-tls-edge.yml.example"), StandardCharsets.UTF_8);
        Files.deleteIfExists(app.resolve("src/main/resources/application.properties"));
        place(app, "src/main/java", "HstsHeaderFilter", read(TRANSPORT, "HstsHeaderFilter.java.example"));
        place(app, "src/test/java", "ForwardedHeadersIT", read(TRANSPORT, "ForwardedHeadersIT.java.example"));

        int failures = 0;
        if (!run(app, build, label + " build, @Nested ITs only").ok()) return 1;
        Result green = run(app, List.of("bash", "--noprofile", "--norc", "-eo", "pipefail", step.toString()), null);
        if (!green.ok() || !green.out().contains("✅")) {
            System.out.println(green.out());
            System.out.println("❌ " + label + ": " + ci + " failed a build whose only IT holds its tests in @Nested classes, all passing.");
            failures++;
        } else {
            System.out.println("✅ " + label + ": @Nested ITs only — " + green.out().strip());
        }

        place(app, "src/test/java", "GuardedIT", GUARDED_IT);
        if (!run(app, build, label + " build, plus a Docker-guarded IT").ok()) return failures + 1;
        Result red = run(app, List.of("bash", "--noprofile", "--norc", "-eo", "pipefail", step.toString()), null);
        if (red.ok() || !red.out().contains("GuardedIT")) {
            System.out.println(red.out());
            System.out.println("❌ " + label + ": " + ci + " passed, or failed without naming GuardedIT, on a build where the Docker guard skipped it.");
            failures++;
        } else {
            System.out.println("✅ " + label + ": Docker-guarded IT fails the check by name.");
        }
        return failures;
    }

    /** The `run: |` block of the named step, dedented — what the runner hands to bash. */
    static String extractStep(String yaml) {
        List<String> lines = yaml.lines().toList();
        int i = 0;
        while (i < lines.size() && !lines.get(i).strip().equals(STEP_NAME)) i++;
        while (i < lines.size() && !lines.get(i).strip().equals("run: |")) i++;
        if (i == lines.size()) fail("no `" + STEP_NAME + "` step with a `run: |` block");
        int keyIndent = indent(lines.get(i));
        List<String> block = new ArrayList<>();
        for (i++; i < lines.size(); i++) {
            String l = lines.get(i);
            if (!l.isBlank() && indent(l) <= keyIndent) break;
            block.add(l);
        }
        while (!block.isEmpty() && block.get(block.size() - 1).isBlank()) block.remove(block.size() - 1);
        int cut = block.stream().filter(l -> !l.isBlank()).mapToInt(CiItCheckTest::indent).min().orElse(0);
        StringBuilder sb = new StringBuilder();
        for (String l : block) sb.append(l.isBlank() ? "" : l.substring(cut)).append('\n');
        return sb.toString();
    }

    static int indent(String l) {
        int n = 0;
        while (n < l.length() && l.charAt(n) == ' ') n++;
        return n;
    }

    /** The failsafe `<plugin>` block of the parent POM template, into the Initializr POM. */
    static void wireMaven(Path app) throws IOException {
        Matcher m = FAILSAFE.matcher(read(BOOTSTRAP, "pom.parent.xml.example"));
        if (!m.find()) fail("pom.parent.xml.example: no maven-failsafe-plugin block");
        Path pom = app.resolve("pom.xml");
        String text = Files.readString(pom, StandardCharsets.UTF_8);
        if (!text.contains("<plugins>")) fail("Initializr pom.xml: no <plugins>");
        Files.writeString(pom, text.replaceFirst("<plugins>", Matcher.quoteReplacement("<plugins>\n" + m.group())),
                StandardCharsets.UTF_8);
    }

    /** The `integrationTest` task and its `check` wiring from the parent build template. */
    static void wireGradle(Path app) throws IOException {
        String parent = read(BOOTSTRAP, "build.gradle.parent.example");
        String blocks = "\n" + braceBlock(parent, "tasks.register('integrationTest'") + "\n"
                + braceBlock(parent, "tasks.named('check')") + "\n";
        Files.writeString(app.resolve("build.gradle"), blocks, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    static String braceBlock(String text, String start) {
        int from = text.indexOf(start);
        if (from < 0) fail("build.gradle.parent.example: no `" + start + "`");
        int depth = 0;
        for (int i = text.indexOf('{', from); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return text.substring(from, i + 1);
        }
        fail("build.gradle.parent.example: unbalanced braces after `" + start + "`");
        return null;
    }

    record Result(boolean ok, String out) {}

    static Result run(Path dir, List<String> cmd, String label) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean ok = p.waitFor() == 0;
        if (!ok && label != null) {
            System.out.println(tail(out, 60));
            System.out.println("❌ " + label + ": `" + String.join(" ", cmd) + "` failed before the IT check could run.");
        }
        return new Result(ok, out);
    }

    /** start.spring.io, same source `project-bootstrap` step 3 uses; web and actuator, as generated. */
    static Path initializrProject(Path work, String type) throws Exception {
        int java = Runtime.version().feature();
        String url = "https://start.spring.io/starter.zip?type=" + type + "&language=java"
                + "&dependencies=web,actuator&groupId=com.exemplo&artifactId=minhaapi&name=minhaapi"
                + "&packageName=com.exemplo.minhaapi&baseDir=app&javaVersion=" + java;
        HttpResponse<InputStream> r = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) fail("HTTP " + r.statusCode() + " from start.spring.io: " + url);
        try (ZipInputStream zip = new ZipInputStream(r.body())) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                Path target = work.resolve(e.getName()).normalize();
                if (!target.startsWith(work)) fail("zip entry escapes the work dir: " + e.getName());
                if (e.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Path app = work.resolve("app");
        for (String wrapper : List.of("mvnw", "gradlew")) {
            File f = app.resolve(wrapper).toFile();
            if (f.exists() && !f.setExecutable(true)) fail("could not mark " + wrapper + " executable");
        }
        return app;
    }

    static String read(Path dir, String template) throws IOException {
        return Files.readString(dir.resolve(template), StandardCharsets.UTF_8);
    }

    /** Under the directory the source's own `package` line names. */
    static void place(Path app, String root, String className, String source) throws IOException {
        Matcher m = PACKAGE.matcher(source);
        if (!m.find()) fail(className + ": no package declaration");
        Path file = app.resolve(root).resolve(m.group(1).replace('.', '/')).resolve(className + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    static String tail(String s, int lines) {
        List<String> all = s.lines().toList();
        return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
    }

    static void fail(String message) {
        System.out.println("❌ " + message);
        System.exit(1);
    }
}
