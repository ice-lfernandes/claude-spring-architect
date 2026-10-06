///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves `audit genesis` fills a generated project's GENESIS record from the session's
// transcripts, exactly, once — and refuses, by name, what it cannot fill.
//
// Why this test exists: a mode that throws exits 0 through ArchHook's top-level catch and looks
// like it passed. GENESIS once recorded a start after its finish, written from a directory's
// birth time in local zone with a literal `Z`, and no cost at all (lessons-learned-020 §§ 4–5,
// decision 0123). The figures now come only from this mode, so a wrong sum, a turn counted from
// before `/init-project`, or a second run overwriting the record would land in every project.
//
// What it builds: a throwaway CLAUDE_CONFIG_DIR with one main transcript (a turn before the
// `/init-project` message, which must not count, and a turn written as two content blocks, which
// must count once, and a later tool result quoting the command tag, which must not move Started)
// and one subagent transcript with its `.meta.json`; a throwaway project with a
// pricing.json and a GENESIS carrying the four placeholders. `audit.genesis` is read from this
// repository's real extensions.json, through CLAUDE_PROJECT_DIR. Runs the jar, the bytes the
// skill invokes (0084).

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class GenesisTest {

    static final Path HOOK = Paths.get(".claude/hooks/ArchHook.jar").toAbsolutePath();
    static final Path REPO = Paths.get("").toAbsolutePath();
    static final String SID = "11111111-2222-3333-4444-555555555555";
    static final String GENESIS = """
            | Started | {{startIso}} |
            | Finished | {{endIso}} |
            | Tokens | {{tokens}} |
            | Cost | {{cost}} |
            """;
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path cfg = Files.createTempDirectory("genesis-cfg");
        Path session = cfg.resolve("projects/-tmp-nerviz");
        Files.createDirectories(session.resolve(SID + "/subagents"));
        Files.writeString(session.resolve(SID + ".jsonl"), String.join("\n",
                turn("2026-10-06T08:00:00Z", "m0", "claude-opus-5-5", 1_000_000, 0, 0, 0),
                "{\"type\":\"user\",\"timestamp\":\"2026-10-06T08:55:27.413Z\",\"message\":{\"role\":\"user\","
                        + "\"content\":\"<command-name>/init-project</command-name>\"}}",
                turn("2026-10-06T08:55:40Z", "m1", "claude-opus-5-5", 10_000, 100_000, 1_000_000, 200_000),
                turn("2026-10-06T08:55:41Z", "m1", "claude-opus-5-5", 10_000, 100_000, 1_000_000, 200_000),
                "{\"type\":\"user\",\"timestamp\":\"2026-10-06T09:30:00Z\",\"message\":{\"role\":\"user\",\"content\":"
                        + "[{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_9\",\"content\":"
                        + "\"a file citing <command-name>/init-project</command-name>\"}]}}") + "\n");
        Path sub = session.resolve(SID + "/subagents");
        Files.writeString(sub.resolve("agent-abc.jsonl"),
                turn("2026-10-06T09:00:00Z", "s1", "claude-sonnet-5", 2_000, 50_000, 5_000_000, 100_000) + "\n");
        Files.writeString(sub.resolve("agent-abc.meta.json"), "{\"toolUseId\":\"toolu_1\"}");

        Path project = Files.createTempDirectory("genesis-project");
        Path audit = project.resolve(".claude/audit-usage");
        Files.createDirectories(audit);
        Path genesis = audit.resolve("GENESIS.md");

        Files.writeString(audit.resolve("pricing.json"), pricing(true));
        Files.writeString(genesis, GENESIS);
        int exit = run(cfg, project, SID);
        String filled = Files.readString(genesis);
        check("fills the record", exit == 0, "exit " + exit);
        check("Started is the /init-project message, UTC, to the second",
                filled.contains("| Started | 2026-10-06T08:55:27Z |"), filled);
        check("Finished is a UTC instant, not before Started",
                filled.matches("(?s).*\\| Finished \\| 20\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ \\|.*"), filled);
        check("tokens: the main session from Started on plus the subagent, one turn per message id",
                filled.contains("12,000 input · 150,000 output · 6,000,000 cache read · 300,000 cache write — 2 requests"),
                filled);
        check("cost priced from the project's pricing.json, both models", filled.contains("| Cost | USD 4.99 |"), filled);
        check("no placeholder left", !filled.contains("{{"), filled);

        exit = run(cfg, project, SID);
        check("a filled record is refused, not overwritten", exit == 1 && Files.readString(genesis).equals(filled),
                "exit " + exit);

        Files.writeString(genesis, GENESIS);
        exit = run(cfg, project, "00000000-0000-0000-0000-000000000000");
        check("an unknown session is refused", exit == 1 && Files.readString(genesis).equals(GENESIS), "exit " + exit);

        Files.writeString(audit.resolve("pricing.json"), pricing(false));
        exit = run(cfg, project, SID);
        String unpriced = Files.readString(genesis);
        check("an unpriced model names itself, never a partial sum",
                exit == 0 && unpriced.contains("no price for `claude-sonnet-5`") && !unpriced.contains("USD"), unpriced);

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — GENESIS figures are not what the transcripts say.");
            System.exit(1);
        }
        System.out.println("✅ audit genesis fills Started, Finished, tokens and cost from the transcripts, once.");
    }

    static String turn(String iso, String id, String model, long in, long out, long cr, long cw) {
        return "{\"type\":\"assistant\",\"timestamp\":\"" + iso + "\",\"message\":{\"id\":\"" + id
                + "\",\"model\":\"" + model + "\",\"content\":[{\"type\":\"text\",\"text\":\"x\"}],"
                + "\"usage\":{\"input_tokens\":" + in + ",\"output_tokens\":" + out
                + ",\"cache_read_input_tokens\":" + cr + ",\"cache_creation_input_tokens\":" + cw + "}}}";
    }

    static String pricing(boolean withSonnet) {
        String opus = "\"claude-opus-5-5\":{\"input\":4,\"output\":20,\"cache_read\":0.2,\"cache_write\":5}";
        String sonnet = ",\"claude-sonnet-5\":{\"input\":2,\"output\":10,\"cache_read\":0.2,\"cache_write\":2.5}";
        return "{\"currency\":\"USD\",\"per\":1000000,\"models\":{" + opus + (withSonnet ? sonnet : "") + "}}";
    }

    static int run(Path cfg, Path project, String sid) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(ProcessHandle.current().info().command().orElse("java"),
                "-jar", HOOK.toString(), "audit", "genesis", project.toString(), sid);
        pb.environment().put("CLAUDE_CONFIG_DIR", cfg.toString());
        pb.environment().put("CLAUDE_PROJECT_DIR", REPO.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            System.out.println("  ✗ hung for 30 s — `audit genesis` must not read stdin");
            failures++;
            return -1;
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!out.isBlank()) System.out.println(out.strip().indent(6).stripTrailing());
        return p.exitValue();
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) { System.out.println("  ✓ " + name); return; }
        System.out.println("  ✗ " + name + "\n" + detail.indent(6).stripTrailing());
        failures++;
    }
}
