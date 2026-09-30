///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves `ArchHook.jar context subagent` hands the pattern catalog to an agent
// whose `agent_classes` entry declares `pattern_catalog: true`, keeps it under
// `subagent_context.max_chars`, and hands nothing to any other agent.
//
// Why this test exists: `SubagentStart` cannot block, and an empty stdout is a legal answer —
// so a catalog that stops arriving fails in total silence. The agent just writes Java without
// it, which is exactly the failure decision 0077 replaced the `skills:` preload for. The
// opposite drift is silent too: every agent paying ~9 000 characters of context it never
// uses. `schema` checks the render size; only running the mode checks who receives it.
//
// Uses the repository's real extensions.json and java-patterns/SKILL.md.

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;

public class SubagentContextTest {

    public static void main(String[] args) throws Exception {
        Path hook = Paths.get(".claude/hooks/ArchHook.jar").toAbsolutePath();
        String schema = Files.readString(Paths.get(".claude/schemas/extensions.json"));
        Matcher mm = Pattern.compile("\"max_chars\"\\s*:\\s*(\\d+)").matcher(schema);
        if (!mm.find()) {
            System.err.println("❌ subagent_context.max_chars not found in extensions.json");
            System.exit(1);
        }
        int max = Integer.parseInt(mm.group(1));
        int failures = 0;

        String out = context(hook, "{\"agent_type\":\"java-spring-boot-developer\"}");
        String text = additionalContext(out);
        if (text == null || !out.contains("\"hookEventName\":\"SubagentStart\"")) {
            System.out.println("❌ java-spring-boot-developer — no SubagentStart additionalContext");
            System.out.println(out);
            failures++;
        } else if (!text.contains("## Catalog")) {
            System.out.println("❌ java-spring-boot-developer — context has no `## Catalog` section");
            failures++;
        } else if (text.length() > max) {
            System.out.println("❌ java-spring-boot-developer — " + text.length()
                    + " chars, above max_chars " + max);
            failures++;
        } else {
            System.out.println("✅ java-spring-boot-developer receives the catalog ("
                    + text.length() + " ≤ " + max + " chars)");
        }

        for (String agent : new String[] {"archunit-installer", "project-initializer",
                "general-purpose"}) {
            String o = context(hook, "{\"agent_type\":\"" + agent + "\"}");
            if (o.isBlank()) {
                System.out.println("✅ " + agent + " receives nothing");
            } else {
                System.out.println("❌ " + agent + " received output:");
                System.out.println(o);
                failures++;
            }
        }

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — the pattern catalog is NOT"
                    + " reaching exactly the agents agent_classes marks pattern_catalog: true.");
            System.exit(1);
        }
        System.out.println("✅ context subagent: catalog to pattern_catalog agents only,"
                + " within max_chars.");
    }

    /** The unescaped `additionalContext` value, or null when absent. */
    static String additionalContext(String out) {
        int i = out.indexOf("\"additionalContext\":\"");
        if (i < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int k = i + "\"additionalContext\":\"".length(); k < out.length(); k++) {
            char c = out.charAt(k);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char n = out.charAt(++k);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'u' -> { sb.append((char) Integer.parseInt(out.substring(k + 1, k + 5), 16)); k += 4; }
                default -> sb.append(n);
            }
        }
        return null;
    }

    static String context(Path hook, String stdin) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar", hook.toString(), "context", "subagent");
        pb.environment().put("CLAUDE_PROJECT_DIR", Paths.get("").toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();                                 // SubagentStart never blocks: output is the signal
        return out;
    }
}
