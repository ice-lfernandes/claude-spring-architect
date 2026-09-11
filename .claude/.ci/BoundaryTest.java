///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves the hook actually BLOCKS a forbidden import, on any OS.
// Without this, "cross-platform" and "enforcement active" are claims, not facts.

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class BoundaryTest {
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

        Path hook = Paths.get(".claude/hooks/ArchHook.java").toAbsolutePath();
        String json = "{\"tool_input\":{\"file_path\":\""
                + bad.toAbsolutePath().toString().replace("\\", "\\\\") + "\"}}";

        ProcessBuilder pb = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                hook.toString(), "check");
        pb.environment().put("CLAUDE_PROJECT_DIR", tmp.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();

        System.out.println(out);
        if (exit != 2) {
            System.err.println("❌ Expected exit 2 (blocked), got " + exit
                    + " — enforcement is NOT working on this system.");
            System.exit(1);
        }
        System.out.println("✅ Boundary enforced: forbidden import blocked with exit 2.");
    }
}
