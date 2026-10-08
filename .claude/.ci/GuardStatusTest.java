///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves `ArchHook.jar guard status` prints the phase `guard prompt` and `guard call`
// opened — its skills, their class, the union of their `write_allow` — prints an empty phase
// once the next prompt closes it, hands over `mods.deny_markers` unchanged, and changes no
// state while doing so.
//
// Why this test exists: the nerviz-cockpit mod draws the band above the prompt from this one
// line of JSON and recognizes a guard refusal by its markers. The mod's own tests stub the
// line (`claude plugin test` has no process), so nothing else proves the jar prints what the
// stub claims: a renamed key empties the band with no error, and a status that wrote state
// would open phases nobody asked for. Decision 0131.
//
// Uses the repository's real extensions.json, like BashGuardTest. Runs the jar, not the
// source: that is what the mod launches.

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class GuardStatusTest {

    public static void main(String[] args) throws Exception {
        Path hook = Paths.get(".claude/hooks/ArchHook.jar").toAbsolutePath();
        String json = Files.readString(Paths.get(".claude/schemas/extensions.json"),
                StandardCharsets.UTF_8);
        String session = "ci-status-" + System.nanoTime();
        int failures = 0;

        guard(hook, "prompt", session, "{\"session_id\":\"%s\",\"prompt\":\"hi\"}");
        failures += expect(status(hook, session), "\"phase\":[]", "no phase — empty phase");
        failures += expect(status(hook, session), "\"class\":null", "no phase — no class");
        failures += expect(status(hook, session), "\"phase\":[]", "status opens nothing on its own");

        guard(hook, "prompt", session,
                "{\"session_id\":\"%s\",\"prompt\":\"/claude-code-architect-designer x\"}");
        String open = status(hook, session);
        failures += expect(open, "\"phase\":[\"claude-code-architect-designer\"]",
                "a slash command opens the phase");
        failures += expect(open, "\"class\":\"meta\"", "its class");
        failures += expect(open, "\".claude/**\"", "its write_allow");

        // `guard call` joins a second skill of the same class to the phase (0092); a Skill
        // call to another class would be refused there, which BashGuardTest's neighbours cover.
        guard(hook, "call", session,
                "{\"session_id\":\"%s\",\"tool_name\":\"Skill\",\"tool_input\":{\"skill\":\"claude-code-architect-designer\"}}");
        failures += expect(status(hook, session), "\"class\":\"meta\"", "a Skill call keeps the class");

        int start = json.indexOf("\"deny_markers\"");
        // Whitespace dropped on both sides: the file wraps the list, the jar prints one line.
        String markers = json.substring(json.indexOf('[', start), json.indexOf(']', start) + 1)
                .replaceAll("\\s+", "");
        failures += expect(open.replaceAll("\\s+", ""), "\"deny_markers\":" + markers,
                "deny_markers handed over unchanged");

        guard(hook, "prompt", session, "{\"session_id\":\"%s\",\"prompt\":\"hi\"}");
        failures += expect(status(hook, session), "\"phase\":[]", "the next prompt closes it");

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — `guard status` does NOT"
                    + " print the phase the guard holds, which is all the cockpit band shows.");
            System.exit(1);
        }
        System.out.println("✅ guard status: the open phase, its class and territory, the deny"
                + " markers — and an empty phase once it closes.");
    }

    static String status(Path hook, String session) throws Exception {
        return guard(hook, "status", session, "{\"session_id\":\"%s\"}").strip();
    }

    /** Runs one `guard <phase>` with the given stdin; returns stdout and stderr together. */
    static String guard(Path hook, String phase, String session, String jsonTemplate)
            throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar", hook.toString(), "guard", phase);
        pb.environment().put("CLAUDE_PROJECT_DIR", Paths.get("").toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().write(jsonTemplate.formatted(session).getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out;
    }

    static int expect(String out, String needle, String label) {
        if (out.contains(needle)) {
            System.out.println("✅ " + label);
            return 0;
        }
        System.out.println("❌ " + label + " — expected `" + needle + "` in:");
        System.out.println(out);
        return 1;
    }
}
