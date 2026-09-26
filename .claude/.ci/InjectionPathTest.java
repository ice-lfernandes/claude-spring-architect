///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves `ArchHook.java schema` BLOCKS a `!`command`` injection that resolves
// paths from the shell's cwd, and lets the project-root form through.
//
// Why this test exists: the bug it guards against is silent. A relative injection runs
// fine until an earlier Bash call has `cd`-ed somewhere else, and then it reports a file
// as absent while the file is present — arming the entry guard of the skill it belongs
// to. lessons-learned-010 § 5: twelve occurrences across ten skills before anyone noticed.

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;

public class InjectionPathTest {

    public static void main(String[] args) throws Exception {
        Path hook = Paths.get(".claude/hooks/ArchHook.java").toAbsolutePath();
        Path schema = Paths.get(".claude/schemas/extensions.json").toAbsolutePath();

        // The real schema, so the test proves what production reads — not a fixture that
        // could drift from it.
        String relative = """
                ---
                name: probe-skill
                description: >
                  A skill whose injection resolves paths from the cwd.
                ---

                ## State

                !`test -f docker-compose.yml && echo yes || echo no`
                """;
        String absolute = relative.replace(
                "test -f docker-compose.yml",
                "test -f \"${CLAUDE_PROJECT_DIR:-.}/docker-compose.yml\"");
        // An injection inside a fenced block is documentation, never executed.
        String fenced = """
                ---
                name: probe-skill
                description: >
                  A skill that only shows an injection as an example.
                ---

                ## How not to write one

                ```markdown
                !`test -f docker-compose.yml && echo yes`
                ```
                """;

        // Prose that mentions an injection writes it as an inline code span containing a
        // backtick, so the `!` is preceded by one. Flagging that would make every file
        // documenting the rule fail the rule.
        String prose = """
                ---
                name: probe-skill
                description: >
                  A skill that talks about injections without carrying one.
                ---

                ## Note

                Every `` !`test -f docker-compose.yml` `` injection is cwd-dependent.
                """;

        int failures = 0;
        failures += expect(hook, schema, relative, 2,
                "relative injection blocked");
        failures += expect(hook, schema, prose, 0,
                "injection mentioned in prose ignored");
        failures += expect(hook, schema, absolute, 0,
                "project-root injection accepted");
        failures += expect(hook, schema, fenced, 0,
                "injection inside a fenced example ignored");
        failures += expectUntyped(hook, schema, relative,
                "file outside every `types` match not scanned");

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — the injection lint is"
                    + " NOT enforcing what extensions.json declares.");
            System.exit(1);
        }
        System.out.println("✅ Injection paths enforced: cwd-relative blocked with exit 2;"
                + " ${CLAUDE_PROJECT_DIR} accepted; prose, fenced example and untyped file"
                + " ignored.");
    }

    /** Runs `schema` over a throwaway project holding one skill, and checks the exit code. */
    static int expect(Path hook, Path schema, String skill, int wanted, String label)
            throws Exception {
        Path tmp = Files.createTempDirectory("archhook-injection-ci");
        Files.createDirectories(tmp.resolve(".claude/schemas"));
        Files.createDirectories(tmp.resolve(".claude/skills/probe-skill"));
        Files.createDirectories(tmp.resolve(".claude/agents"));
        Files.copy(schema, tmp.resolve(".claude/schemas/extensions.json"));
        Files.writeString(tmp.resolve(".claude/skills/probe-skill/SKILL.md"), skill);
        stubExecutorAgents(schema, tmp);

        ProcessBuilder pb = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                hook.toString(), "schema");
        pb.environment().put("CLAUDE_PROJECT_DIR", tmp.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().close();                 // no stdin: sweeps the whole project
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();

        if (exit == wanted) {
            System.out.println("✅ " + label + " (exit " + exit + ")");
            return 0;
        }
        System.out.println("❌ " + label + " — expected exit " + wanted + ", got " + exit);
        System.out.println(out);
        return 1;
    }

    /**
     * A decision record or a lessons-learned file is prose about injections, not a file
     * the runtime executes them from. `schema` is handed one on every PostToolUse Edit
     * under `.claude/`, so scanning it would block writing the very document that explains
     * the rule. Feeds the same relative injection through `tool_input.file_path`, the
     * PostToolUse path, and requires exit 0.
     */
    static int expectUntyped(Path hook, Path schema, String body, String label)
            throws Exception {
        Path tmp = Files.createTempDirectory("archhook-injection-ci");
        Files.createDirectories(tmp.resolve(".claude/schemas"));
        Files.createDirectories(tmp.resolve(".claude/decisions"));
        Files.copy(schema, tmp.resolve(".claude/schemas/extensions.json"));
        Path record = tmp.resolve(".claude/decisions/0000-probe.md");
        Files.writeString(record, body);

        ProcessBuilder pb = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                hook.toString(), "schema");
        pb.environment().put("CLAUDE_PROJECT_DIR", tmp.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().write(("{\"tool_input\":{\"file_path\":\""
                + record.toAbsolutePath().toString().replace("\\", "\\\\") + "\"}}")
                .getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();

        if (exit == 0) {
            System.out.println("✅ " + label + " (exit 0)");
            return 0;
        }
        System.out.println("❌ " + label + " — expected exit 0, got " + exit);
        System.out.println(out);
        return 1;
    }

    /**
     * `schema` also cross-checks `guard.executor_agents` against the agent files on disk,
     * and the throwaway project has none. The names come from the real schema rather than
     * a copy here, so adding an executor agent doesn't break this test.
     */
    static void stubExecutorAgents(Path schema, Path tmp) throws IOException {
        String json = Files.readString(schema, StandardCharsets.UTF_8);
        Matcher block = Pattern.compile("\"executor_agents\"\\s*:\\s*\\[([^]]*)]").matcher(json);
        if (!block.find()) return;
        Matcher name = Pattern.compile("\"([^\"]+)\"").matcher(block.group(1));
        while (name.find()) {
            Files.writeString(tmp.resolve(".claude/agents/" + name.group(1) + ".md"), """
                    ---
                    name: %s
                    description: CI stub, exists only so the executor cross-check passes.
                    ---

                    ## Contract

                    **Executor:** yes
                    """.formatted(name.group(1)));
        }
    }
}
