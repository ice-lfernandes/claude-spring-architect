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
import java.util.*;
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

                ## Why this is a fixture

                It exists only inside this test's throwaway project. The class marker and this
                section are what `skill_classes` requires of every skill body.

                ## Contract

                **Class:** ci_probe
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

                ## Why this is a fixture

                It exists only inside this test's throwaway project.

                ## Contract

                **Class:** ci_probe
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

                ## Why this is a fixture

                It exists only inside this test's throwaway project.

                ## Contract

                **Class:** ci_probe
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
        stubAgents(schema, tmp);
        stubSkillClass(tmp);

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
     * `schema` also requires every agent named in `agent_classes` to exist on disk, with the
     * body, sections and frontmatter its class asks for — and the throwaway project has no
     * agents at all. The stub is generated from the real schema rather than from a copy here,
     * so adding an agent or a required section doesn't break this test. Only the classes that
     * grant `executor: true` get the marker; a class that doesn't would fail on its presence.
     */
    static void stubAgents(Path schema, Path tmp) throws IOException {
        String json = Files.readString(schema, StandardCharsets.UTF_8);
        Matcher cls = Pattern.compile("\"([a-z][a-z0-9_-]*)\"\\s*:\\s*\\{([^{]*?)"
                + "\"agents\"\\s*:\\s*\\[([^]]*)]", Pattern.DOTALL).matcher(json);
        while (cls.find()) {
            boolean executor = cls.group(2).contains("\"executor\": true");
            List<String> sections = new ArrayList<>();
            Matcher sec = Pattern.compile("\"(## [^\"]+)\"").matcher(cls.group(2));
            while (sec.find()) sections.add(sec.group(1));
            Matcher name = Pattern.compile("\"([^\"]+)\"").matcher(cls.group(3));
            while (name.find()) {
                StringBuilder b = new StringBuilder();
                b.append("---\nname: ").append(name.group(1))
                        .append("\ndescription: CI stub, exists only so the agent_classes"
                                + " cross-check passes.\nmodel: sonnet\ntools: Read, Write\n"
                                + "effort: medium\n---\n\n# `").append(name.group(1))
                        .append("` — CI stub\n\n## Why this is an agent (Form 3)\n\nStub.\n\n"
                                + "## Contract\n\n**Class:** ").append(cls.group(1)).append("\n");
                if (executor) b.append("\n**Executor:** yes\n");
                b.append("\n## Procedure\n\nStub.\n");
                for (String s : sections) b.append("\n").append(s).append("\n\nStub.\n");
                Files.writeString(tmp.resolve(".claude/agents/" + name.group(1) + ".md"), b);
            }
        }
    }

    /**
     * `schema` also requires every skill on disk to belong to a `skill_classes` class, and
     * `probe-skill` is synthetic — it exists only inside this test's throwaway project, so it
     * cannot be listed in the repository's own data. The copy of the schema gets one extra
     * class whose only member is the probe, with an empty territory and no required section
     * beyond the universal ones, which the fixtures above carry. Everything else in the copy
     * stays verbatim: what is under test is the injection lint, and it must be the production
     * one.
     */
    static void stubSkillClass(Path tmp) throws IOException {
        Path copy = tmp.resolve(".claude/schemas/extensions.json");
        String json = Files.readString(copy, StandardCharsets.UTF_8);
        String probe = """
                "classes": {
                    "ci_probe": {
                      "write_allow": [],
                      "required_sections": [],
                      "skills": ["probe-skill"]
                    },""";
        Files.writeString(copy, json.replaceFirst("\"classes\"\\s*:\\s*\\{", probe),
                StandardCharsets.UTF_8);
    }
}
