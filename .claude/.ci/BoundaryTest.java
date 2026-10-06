///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves the hook actually BLOCKS a forbidden import, on any OS.
// Without this, "cross-platform" and "enforcement active" are claims, not facts.
//
// Two input shapes, both checked. The payload on stdin is what the `PostToolUse` hook sends.
// `check <path>` is what a person and the bootstrap's boundary probe type: it must block the
// same file with stdin left open — before decision 0123 it read stdin first and hung until the
// tool timeout, then passed in silence once stdin closed (lessons-learned-020 § 3).

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BoundaryTest {

    static final Path HOOK = Paths.get(".claude/hooks/ArchHook.jar").toAbsolutePath();
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("archhook-ci");
        Path src = tmp.resolve("domain/src/main/java");
        Files.createDirectories(src);
        Files.createDirectories(tmp.resolve(".claude"));
        Files.writeString(tmp.resolve(".claude/forbidden-imports.txt"),
                "# ci\ndomain|org.springframework.\n");
        Path bad = src.resolve("Violator.java");
        Files.writeString(bad, """
                package d;
                import org.springframework.stereotype.Component;
                class Violator {}
                """);
        Path clean = src.resolve("Clean.java");
        Files.writeString(clean, """
                package d;
                class Clean {}
                """);

        String json = "{\"tool_input\":{\"file_path\":\""
                + bad.toAbsolutePath().toString().replace("\\", "\\\\") + "\"}}";

        expect("stdin payload blocks the forbidden import", tmp, json, 2, "check");
        expect("check <path> blocks it with stdin left open", tmp, null, 2,
                "check", "domain/src/main/java/Violator.java");
        expect("check <path> passes a clean file and says so", tmp, null, 0,
                "check", "domain/src/main/java/Clean.java");
        expect("check <path> on a missing file is not a pass", tmp, null, 1,
                "check", "domain/src/main/java/Missing.java");

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — enforcement is NOT working on this system.");
            System.exit(1);
        }
        System.out.println("✅ Boundary enforced: forbidden import blocked with exit 2, from the payload and from argv.");
    }

    /**
     * Runs the jar with {@code CLAUDE_PROJECT_DIR} at the throwaway project. A null payload leaves
     * stdin open and never writes to it — the shape that used to hang — so a hang is a failure
     * after 30 s instead of a stuck job.
     */
    static void expect(String name, Path project, String payload, int exit, String... modeArgs) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElse("java"), "-jar", HOOK.toString()));
        cmd.addAll(List.of(modeArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("CLAUDE_PROJECT_DIR", project.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        if (payload != null) {
            p.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
        }
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            System.out.println("  ✗ " + name + " — hung for 30 s waiting on stdin");
            failures++;
            return;
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.exitValue() != exit) {
            System.out.println("  ✗ " + name + " — expected exit " + exit + ", got " + p.exitValue());
            System.out.println(out.indent(6));
            failures++;
        } else {
            System.out.println("  ✓ " + name);
        }
        if (payload == null) p.getOutputStream().close();
    }
}
