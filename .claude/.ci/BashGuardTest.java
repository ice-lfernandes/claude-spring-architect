///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves `ArchHook.jar guard bash` refuses a force push in every spelling
// `guard.force_push` claims to read, applies the open phase's territory to the targets
// `guard.bash_write_shapes` names, and lets an ordinary command through.
//
// Why this test exists: `guard bash` runs at PreToolUse on EVERY shell command, so it can
// fail in two directions and both are expensive. Too loose, and a `-uf` or `sh -c "…"` walks
// past the one refusal decision 0076 promised — the `deny` line it replaced only caught the
// literal `--force`. Too tight, and it blocks `ls` for every session in every generated
// project. Neither shows up in review: the parser is data-driven, and a data edit to
// `extensions.json` changes behaviour without touching a line of Java.
//
// Uses the repository's real extensions.json, like SkillTerritoryTest. Runs the jar, not the
// source: that is what the hooks launch, and `build --verify` earlier in the job proves the
// two are the same bytes.

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class BashGuardTest {

    public static void main(String[] args) throws Exception {
        Path hook = Paths.get(".claude/hooks/ArchHook.jar").toAbsolutePath();
        String session = "ci-bash-" + System.nanoTime();
        int failures = 0;

        // No phase open: only the force-push refusal applies.
        failures += run(hook, "prompt", session, "{\"session_id\":\"%s\",\"prompt\":\"hi\"}",
                0, "plain prompt — no phase");
        failures += bash(hook, session, "git push origin main", 0, "plain push — allowed");
        failures += bash(hook, session, "git push -f origin main", 2, "`-f` — blocked");
        failures += bash(hook, session, "git push -uf origin main", 2, "`-uf` — blocked");
        failures += bash(hook, session, "git push --force-with-lease", 2,
                "`--force-with-lease` — blocked");
        failures += bash(hook, session, "git push origin +main", 2, "`+ref` — blocked");
        failures += bash(hook, session, "git -C . push --force", 2, "`git -C . push` — blocked");
        failures += bash(hook, session, "sh -c \\\"git push --force\\\"", 2,
                "`sh -c \"…\"` — blocked");
        failures += bash(hook, session, "echo x > src/main/java/A.java", 0,
                "redirect with no phase open — allowed");

        // A design phase is docs-only: the same shell spellings now hit the territory.
        failures += run(hook, "prompt", session,
                "{\"session_id\":\"%s\",\"prompt\":\"/persistence-architect design it\"}",
                0, "prompt opens a design phase");
        failures += bash(hook, session, "echo x > src/main/java/A.java", 2,
                "redirect outside the territory — blocked");
        failures += bash(hook, session, "sed -i s/a/b/ src/main/java/A.java", 2,
                "`sed -i` outside the territory — blocked");
        failures += bash(hook, session, "sed s/a/b/ src/main/java/A.java", 0,
                "`sed` without `-i` reads — allowed");
        failures += bash(hook, session, "echo x > docs/use-cases/UC-001-x/notes.md", 0,
                "redirect inside the territory — allowed");
        failures += bash(hook, session, "echo x > $OUT/A.java", 0,
                "unresolvable target — skipped, by design");
        failures += bash(hook, session, "ls -la", 0, "innocent command — allowed");

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — `guard bash` is NOT"
                    + " enforcing what guard.force_push and guard.bash_write_shapes declare.");
            System.exit(1);
        }
        System.out.println("✅ guard bash: every force-push spelling refused, write shapes"
                + " held to the open phase, ordinary commands let through.");
    }

    static int bash(Path hook, String session, String command, int wanted, String label)
            throws Exception {
        return run(hook, "bash", session,
                "{\"session_id\":\"%s\",\"tool_input\":{\"command\":\""
                        + command.replace("%", "%%") + "\"}}",
                wanted, label);
    }

    /** Runs one `guard <phase>` with the given stdin and checks the exit code. */
    static int run(Path hook, String phase, String session, String jsonTemplate,
                   int wanted, String label) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar", hook.toString(), "guard", phase);
        pb.environment().put("CLAUDE_PROJECT_DIR", Paths.get("").toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().write(jsonTemplate.formatted(session)
                .getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code == wanted) {
            System.out.println("✅ " + label);
            return 0;
        }
        System.out.println("❌ " + label + " — expected exit " + wanted + ", got " + code);
        System.out.println(out);
        return 1;
    }
}
