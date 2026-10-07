///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves the `tests` Stop gate defers while a writer subagent of the same session is
// still running, and runs Maven as before in every other case — a read-only agent, an agent
// no class lists, a writer that already stopped, another session's writer, a marker past
// `tests.writer_agent_max_minutes` — and between two chained writers, when the first stopped
// and the second already started.
//
// Why this test exists: a main-thread `Stop` that fired while `java-spring-boot-developer`
// worked in the background ran Maven over its half-written tree and blocked a thread that
// cannot write src/ (issue #76, decision 0116). Both failure directions are silent: a marker
// never written lets the misfire back in, and a marker never cleared turns the gate off — a
// hook that skipped looks exactly like a hook that passed, only the recorded call tells.
//
// Why a stub wrapper: same as ModuleMapTest — the claim is whether `mvnw` is called, not
// what Maven does. Each project gets a copy of the real extensions.json, so the writer set
// is what `agent_classes` says today.

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class TestsDeferTest {

    static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    static final Path HOOK = Paths.get(".claude/hooks/ArchHook.jar").toAbsolutePath();
    static final Path SCHEMA = Paths.get(".claude/schemas/extensions.json").toAbsolutePath();
    static final String WRITER = "java-spring-boot-developer";
    static final String READER = "issue-verifier";
    static final List<String> SESSIONS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        int failures = 0;
        try {
            failures += run("writer running — tests deferred, Maven not called", null, p -> {
                String s = session();
                hook(p, "agent-start", agent(s, "a1", WRITER));
                return s;
            }, false);

            failures += run("read-only agent running — Maven called", null, p -> {
                String s = session();
                hook(p, "agent-start", agent(s, "a1", READER));
                return s;
            }, true);

            failures += run("agent no class lists — Maven called", null, p -> {
                String s = session();
                hook(p, "agent-start", agent(s, "a1", "Explore"));
                return s;
            }, true);

            failures += run("writer started and stopped — Maven called", null, p -> {
                String s = session();
                hook(p, "agent-start", agent(s, "a1", WRITER));
                hook(p, "agent-end", agent(s, "a1", WRITER));
                return s;
            }, true);

            // /new-feature chains three executor groups; the next one starts in the turn that
            // receives the previous one's report, before that turn's Stop (decision 0130).
            failures += run("group 1 stopped, group 2 started — still deferred", null, p -> {
                String s = session();
                hook(p, "agent-start", agent(s, "g1", WRITER));
                hook(p, "agent-end", agent(s, "g1", WRITER));
                hook(p, "agent-start", agent(s, "g2", WRITER));
                return s;
            }, false);

            failures += run("every group stopped — Maven called", null, p -> {
                String s = session();
                for (String g : List.of("g1", "g2", "g3")) {
                    hook(p, "agent-start", agent(s, g, WRITER));
                    hook(p, "agent-end", agent(s, g, WRITER));
                }
                return s;
            }, true);

            failures += run("another agent_id stopped (internal agent) — still deferred", null, p -> {
                String s = session();
                hook(p, "agent-start", agent(s, "a1", WRITER));
                hook(p, "agent-end", agent(s, "internal-9", ""));
                return s;
            }, false);

            failures += run("writer of another session — Maven called", null, p -> {
                hook(p, "agent-start", agent(session(), "a1", WRITER));
                return session();
            }, true);

            failures += run("marker past writer_agent_max_minutes — Maven called", 0, p -> {
                String s = session();
                hook(p, "agent-start", agent(s, "a1", WRITER));
                return s;
            }, true);
        } finally {
            for (String s : SESSIONS) deleteTree(Paths.get(System.getProperty("java.io.tmpdir"),
                    "archhook-tests", s));
        }

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — the tests Stop gate does NOT"
                    + " defer exactly while a writer subagent of this session runs.");
            System.exit(1);
        }
        System.out.println("✅ tests: deferred while a writer subagent runs, Maven called otherwise.");
    }

    interface Setup { String apply(Path project) throws Exception; }

    /** {@code cap} null keeps the real value; {@code maven} says whether mvnw must be called. */
    static int run(String label, Integer cap, Setup setup, boolean maven) throws Exception {
        Path p = project(cap);
        String s = setup.apply(p);
        Path record = p.resolve("mvnw-args.txt");
        Files.deleteIfExists(record);
        Result r = hook(p, null, "{\"session_id\":\"" + s + "\"}");
        boolean called = Files.exists(record);
        String why = r.code != 0 ? "exit " + r.code
                : maven && !called ? "Maven never called"
                : !maven && called ? "Maven called with: " + Files.readString(record).strip()
                : !maven && !r.out.contains("Tests deferred") ? "no `Tests deferred` line"
                : null;
        if (why == null) {
            System.out.println("✅ " + label);
            return 0;
        }
        System.out.println("❌ " + label + " — " + why);
        System.out.println(r.out);
        return 1;
    }

    static String session() {
        String s = "ci-" + UUID.randomUUID();
        SESSIONS.add(s);
        return s;
    }

    static String agent(String session, String id, String type) {
        return "{\"session_id\":\"" + session + "\",\"agent_id\":\"" + id
                + "\",\"agent_type\":\"" + type + "\"}";
    }

    record Result(int code, String out) { }

    static Result hook(Path project, String phase, String stdin) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar", HOOK.toString(), "tests"));
        if (phase != null) cmd.add(phase);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(project.toFile());
        pb.environment().put("CLAUDE_PROJECT_DIR", project.toString());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        proc.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        proc.getOutputStream().close();
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(proc.waitFor(), out);
    }

    /** A single-module project with one untracked source, a recording wrapper, and the schema. */
    static Path project(Integer cap) throws Exception {
        Path dir = Files.createTempDirectory("archhook-testsdefer").toRealPath();
        Files.writeString(dir.resolve("pom.xml"),
                "<project><modelVersion>4.0.0</modelVersion></project>\n");
        Path src = dir.resolve("src/main/java/a/X.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "class X {}\n");
        String schema = Files.readString(SCHEMA, StandardCharsets.UTF_8);
        if (cap != null) {
            Matcher m = Pattern.compile("\"writer_agent_max_minutes\"\\s*:\\s*\\d+").matcher(schema);
            if (!m.find()) throw new IllegalStateException(
                    "extensions.json has no tests.writer_agent_max_minutes to override");
            schema = m.replaceFirst("\"writer_agent_max_minutes\": " + cap);
        }
        Files.createDirectories(dir.resolve(".claude/schemas"));
        Files.writeString(dir.resolve(".claude/schemas/extensions.json"), schema);
        if (WINDOWS) {
            Files.writeString(dir.resolve("mvnw.cmd"), "@echo %*>> \"%~dp0mvnw-args.txt\"\r\n");
        } else {
            Path w = dir.resolve("mvnw");
            Files.writeString(w,
                    "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"$(dirname \"$0\")/mvnw-args.txt\"\n");
            w.toFile().setExecutable(true);
        }
        Process g = new ProcessBuilder("git", "init", "-q").directory(dir.toFile())
                .inheritIO().start();
        if (g.waitFor() != 0) throw new IllegalStateException("git init");
        return dir;
    }

    static void deleteTree(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path f : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
        }
    }
}
