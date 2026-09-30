///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves `ArchHook.java guard` keeps a skill inside the territory its class
// declares in `skill_classes`, refuses a build-class Skill call mid-design, sums territories
// when a skill chains another of its own class, and restricts nothing while no phase is open.
//
// Why this test exists: the failure it guards against was invisible in review. A design run
// wrote a `schema-registry` service into docker-compose.yml, inside a diff everybody read as
// a spec — the file is not under `src/**`, which is all the old denylist forbade
// (lessons-learned-012 §§ 12, 13). The territory is deny-by-default now, and "deny by
// default" is exactly the kind of claim that rots silently: it holds until one `write_allow`
// entry is widened by accident.
//
// Uses the repository's real extensions.json, so what is under test is production data, not
// a fixture that could drift from it. Needs no Docker and no project: `guard` reads the JSON
// and one state file in the OS temp dir.

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class SkillTerritoryTest {

    public static void main(String[] args) throws Exception {
        Path hook = Paths.get(".claude/hooks/ArchHook.jar").toAbsolutePath();
        String session = "ci-territory-" + System.nanoTime();

        int failures = 0;

        // No phase open: a person editing a file by hand is never blocked.
        failures += run(hook, "write", session,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"docker-compose.yml\"}}",
                0, "no phase open — nothing restricted");

        // `/new-feature` opens an orchestrator phase.
        failures += run(hook, "prompt", session,
                "{\"session_id\":\"%s\",\"prompt\":\"/new-feature add a use case\"}",
                0, "prompt opens the orchestrator phase");

        failures += run(hook, "write", session,
                "{\"session_id\":\"%s\",\"tool_input\":"
                        + "{\"file_path\":\"docs/use-cases/UC-001-x/00-caso-de-uso.md\"}}",
                0, "inside the territory — allowed");

        // The regression this file exists for.
        failures += run(hook, "write", session,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"docker-compose.yml\"}}",
                2, "compose file outside the territory — blocked");

        failures += run(hook, "write", session,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"src/main/java/A.java\"}}",
                2, "src/ outside the territory — blocked");

        // A subagent's write is judged by its own class, never by the caller's phase.
        // The territory of each agent is AgentTerritoryTest's subject; here the point is
        // only that an open skill phase does not reach it.
        failures += run(hook, "write", session,
                "{\"session_id\":\"%s\",\"agent_type\":\"java-spring-boot-developer\","
                        + "\"tool_input\":{\"file_path\":\"src/main/java/A.java\"}}",
                0, "agent_type wins over the open phase");

        // A build-class skill is unreachable from inside a design run.
        failures += run(hook, "call", session,
                "{\"session_id\":\"%s\",\"tool_name\":\"Skill\","
                        + "\"tool_input\":{\"skill\":\"docker-architect\"}}",
                2, "build-class Skill call refused mid-design");

        // A design-class skill is reachable, and narrows the phase to its own territory.
        failures += run(hook, "call", session,
                "{\"session_id\":\"%s\",\"tool_name\":\"Skill\","
                        + "\"tool_input\":{\"skill\":\"persistence-architect\"}}",
                0, "design-class Skill call opens its own phase");

        failures += run(hook, "write", session,
                "{\"session_id\":\"%s\",\"tool_input\":"
                        + "{\"file_path\":\"docs/lessons-learned/notes.md\"}}",
                2, "the callee's narrower territory is what applies");

        // An Agent call leaves the phase alone. It used to delete it, which silently
        // unrestricted the CALLER for the rest of the turn — the subagent never needed that,
        // since its own writes carry `agent_type`.
        failures += run(hook, "call", session,
                "{\"session_id\":\"%s\",\"tool_name\":\"Agent\","
                        + "\"tool_input\":{\"subagent_type\":\"java-spring-boot-developer\"}}",
                0, "Agent call is not refused");

        failures += run(hook, "write", session,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"src/main/java/A.java\"}}",
                2, "the caller's own phase survives the Agent call");

        // A new prompt is what ends it.
        failures += run(hook, "prompt", session,
                "{\"session_id\":\"%s\",\"prompt\":\"thanks\"}",
                0, "a plain prompt closes the phase");

        failures += run(hook, "write", session,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"src/main/java/A.java\"}}",
                0, "phase closed — nothing restricted again");

        // A same-class chain sums territories. `build` gives every skill its own override, so
        // keeping only the caller's — the rule before decision 0092 — refused arch-adopt's
        // chained sonarqube-setup the very writes it exists for, and sonarqube-setup's chained
        // docker-architect the compose file.
        String chain = "ci-territory-chain-" + System.nanoTime();
        failures += run(hook, "prompt", chain,
                "{\"session_id\":\"%s\",\"prompt\":\"/arch-adopt\"}",
                0, "prompt opens arch-adopt's phase");

        failures += run(hook, "write", chain,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"pom.xml\"}}",
                2, "pom.xml is not arch-adopt's — blocked");

        failures += run(hook, "call", chain,
                "{\"session_id\":\"%s\",\"tool_name\":\"Skill\","
                        + "\"tool_input\":{\"skill\":\"sonarqube-setup\"}}",
                0, "same-class Skill call joins the phase");

        failures += run(hook, "write", chain,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"pom.xml\"}}",
                0, "the callee's territory is added — pom.xml allowed");

        failures += run(hook, "write", chain,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\".claude/rules/x.md\"}}",
                0, "the caller's territory survives the join");

        failures += run(hook, "write", chain,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"docker-compose.yml\"}}",
                2, "a path neither skill owns stays blocked");

        failures += run(hook, "call", chain,
                "{\"session_id\":\"%s\",\"tool_name\":\"Skill\","
                        + "\"tool_input\":{\"skill\":\"docker-architect\"}}",
                0, "a third same-class skill joins too");

        failures += run(hook, "write", chain,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"docker-compose.yml\"}}",
                0, "compose allowed once docker-architect joined");

        failures += run(hook, "write", chain,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"src/main/java/A.java\"}}",
                2, "the union is still deny-by-default outside it");

        // `report` (decision 0100): the class default is the whole territory, with no
        // override. sonar-lessons runs a full build and publishes outward — the one file it
        // may leave behind is its lessons-learned, never the build file or code.
        String report = "ci-territory-report-" + System.nanoTime();
        failures += run(hook, "prompt", report,
                "{\"session_id\":\"%s\",\"prompt\":\"/sonar-lessons\"}",
                0, "prompt opens sonar-lessons' report phase");

        failures += run(hook, "write", report,
                "{\"session_id\":\"%s\",\"tool_input\":"
                        + "{\"file_path\":\"docs/lessons-learned/sonar-001.md\"}}",
                0, "docs/lessons-learned/ — allowed");

        failures += run(hook, "write", report,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"pom.xml\"}}",
                2, "pom.xml is not a report's — blocked");

        failures += run(hook, "write", report,
                "{\"session_id\":\"%s\",\"tool_input\":{\"file_path\":\"src/main/java/A.java\"}}",
                2, "src/ is not a report's — blocked");

        // `blocked_during_design`: a report is a full build, so a design run cannot reach it.
        String reportMidDesign = "ci-territory-report-design-" + System.nanoTime();
        failures += run(hook, "prompt", reportMidDesign,
                "{\"session_id\":\"%s\",\"prompt\":\"/new-feature add a use case\"}",
                0, "prompt opens the orchestrator phase");

        failures += run(hook, "call", reportMidDesign,
                "{\"session_id\":\"%s\",\"tool_name\":\"Skill\","
                        + "\"tool_input\":{\"skill\":\"sonar-lessons\"}}",
                2, "report-class Skill call refused mid-design");

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — the territory guard is NOT"
                    + " enforcing what skill_classes declares.");
            System.exit(1);
        }
        System.out.println("✅ Skill territory enforced: deny by default while a phase is open,"
                + " agent_type judged by its own class, build-class calls refused mid-design,"
                + " same-class chains summing territories, report-class runs held to"
                + " docs/lessons-learned/, and no restriction with no phase open.");
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
        int exit = p.waitFor();

        if (exit == wanted) {
            System.out.println("✅ " + label + " (exit " + exit + ")");
            return 0;
        }
        System.out.println("❌ " + label + " — expected exit " + wanted + ", got " + exit);
        System.out.println(out);
        return 1;
    }
}
