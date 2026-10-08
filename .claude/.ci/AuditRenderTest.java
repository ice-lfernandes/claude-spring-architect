///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves `ArchHook.jar audit` renders "where the run spent" from the transcripts —
// tool calls per piece, including a subagent's own transcript; the most expensive turns; the
// peak context; each tool error's first line, redacted — writes the matching ledger fields,
// that `audit summary` aggregates them next to a legacy pt-BR ledger row, that a model with
// no price is named in the report header and by `doctor` (lessons-learned-018), and that an
// observer — a piece whose class declares `audited: false` — leaves no report of its own, and
// that a project's audited.json sets a piece on or off over its class, except arch-adopt,
// fixed off by its own class override (decision 0111), and that a prompt the harness injects —
// a background agent's notice or its hand-back — leaves the run open (decisions 0041, 0119),
// and that each piece carries its cache split and its agent call's description, and the report
// ranks the tool calls whose results later requests re-read the most (decision 0132).
//
// Why this test exists: every number here is parsed out of a transcript layout that is
// observed, not documented, and a render that throws exits 0 through `main`'s catch — the
// report just stops updating, which is how decision 0041's crash went unseen. The redaction
// is the other half: an error line can echo a command holding a token, and the report is a
// versioned file (invariant 11). Decision 0085.
//
// Runs in a throwaway project holding a copy of the real extensions.json, so the redaction
// patterns under test are production data.

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

public class AuditRenderTest {

    static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path hook = Paths.get(".claude/hooks/ArchHook.jar").toAbsolutePath();
        Path repo = Files.createTempDirectory("archhook-audit-ci");
        Files.createDirectories(repo.resolve(".claude/schemas"));
        Files.copy(Paths.get(".claude/schemas/extensions.json"),
                repo.resolve(".claude/schemas/extensions.json"));
        Files.createDirectories(repo.resolve(".claude/skills/demo-skill"));
        Files.writeString(repo.resolve(".claude/skills/demo-skill/SKILL.md"),
                "---\nname: demo-skill\ndescription: fixture\n---\n\nbody\n");
        Files.createDirectories(repo.resolve(".claude/agents"));
        Files.writeString(repo.resolve(".claude/agents/demo-agent.md"),
                "---\nname: demo-agent\ndescription: fixture\n---\n\nbody\n");
        Path trail = repo.resolve(".claude/audit-usage");
        Files.createDirectories(trail);

        // A row written before 0085: Portuguese status, formatted pt-BR cost, no cost_usd.
        Files.writeString(trail.resolve("history.jsonl"),
                "{\"e\":\"run\",\"skill\":\"demo-skill\",\"kind\":\"skill\",\"origin\":\"user\","
                + "\"start\":\"2026-01-01T00:00:00Z\",\"duration_ms\":\"1000\",\"status\":\"❌ erro\","
                + "\"tokens_billable\":\"10\",\"tokens_self\":\"10\",\"cost\":\"USD 1.234,56\","
                + "\"files\":\"0\",\"failures\":\"0\",\"report\":\".claude/audit-usage/old--demo-skill.md\"}\n");

        // Prices one model, not the one the fixture runs on: the report must name the gap,
        // not send the reader to a file that looks complete (lessons-learned-018).
        Files.writeString(trail.resolve("pricing.json"), "{\"currency\":\"USD\",\"per\":1000000,"
                + "\"models\":{\"other-model\":{\"input\":1,\"output\":1,\"cache_read\":1,\"cache_write\":1}}}");

        Path transcripts = repo.resolve("transcripts");
        Path main = transcripts.resolve("s1.jsonl");
        Path subs = transcripts.resolve("s1/subagents");
        Files.createDirectories(subs);
        String tp = main.toString().replace("\\", "\\\\");

        run(hook, repo, "prompt", "{\"session_id\":\"s1\",\"prompt\":\"/demo-skill go\","
                + "\"transcript_path\":\"" + tp + "\"}");
        run(hook, repo, "call", "{\"session_id\":\"s1\",\"tool_name\":\"Agent\",\"tool_use_id\":\"toolu_agent\","
                + "\"tool_input\":{\"subagent_type\":\"demo-agent\",\"description\":\"work\"}}");

        // Timestamps after the run opened, so every turn falls inside its token window.
        // m1: one message, two content blocks — the usage repeats and must count once.
        String m1 = usage(100, 50, 1000, 200);
        Files.writeString(main, String.join("\n",
                assistant(at(10), "m1", m1, "{\"type\":\"tool_use\",\"id\":\"tu1\",\"name\":\"Bash\",\"input\":{}}"),
                assistant(at(10), "m1", m1, "{\"type\":\"tool_use\",\"id\":\"tu2\",\"name\":\"Read\",\"input\":{}}"),
                "{\"type\":\"user\",\"timestamp\":\"" + at(11) + "\",\"message\":{\"role\":\"user\",\"content\":"
                        + "[{\"type\":\"tool_result\",\"tool_use_id\":\"tu1\",\"is_error\":true,"
                        + "\"content\":\"Exit code 1\\nerror: token=supersecret123 rejected\\nthird line\"}]}}",
                assistant(at(12), "m2", usage(10, 900, 5000, 3000),
                        "{\"type\":\"tool_use\",\"id\":\"tu3\",\"name\":\"Edit\",\"input\":{}}")) + "\n");
        Files.writeString(subs.resolve("agent-abc.meta.json"), "{\"toolUseId\":\"toolu_agent\"}");
        Files.writeString(subs.resolve("agent-abc.jsonl"), String.join("\n",
                assistant(at(20), "a1", usage(20, 30, 100, 50),
                        "{\"type\":\"tool_use\",\"id\":\"ta1\",\"name\":\"Grep\",\"input\":{}}"),
                assistant(at(21), "a2", usage(5, 5, 200, 0),
                        "{\"type\":\"tool_use\",\"id\":\"ta2\",\"name\":\"Grep\",\"input\":{}}")) + "\n");

        run(hook, repo, "agent", "{\"session_id\":\"s1\",\"agent_type\":\"demo-agent\",\"agent_id\":\"abc\"}");
        // 0094: the answer event keeps each question and its answer, through the same redaction.
        run(hook, repo, "ask", "{\"session_id\":\"s1\",\"tool_name\":\"AskUserQuestion\"}");
        run(hook, repo, "answer", "{\"session_id\":\"s1\",\"tool_name\":\"AskUserQuestion\","
                + "\"tool_response\":{\"questions\":[{\"question\":\"Which prefix?\",\"header\":\"Context\"},"
                + "{\"question\":\"Credential | scope?\",\"header\":\"Auth\"}],"
                + "\"answers\":{\"Which prefix?\":\"banking\",\"Credential | scope?\":\"password=hunter2x\"}}}");
        run(hook, repo, "close", "{\"session_id\":\"s1\",\"transcript_path\":\"" + tp + "\"}");

        Path report;
        try (var s = Files.list(trail)) {
            report = s.filter(f -> f.getFileName().toString().endsWith("--demo-skill.md")).findFirst().orElse(null);
        }
        if (report == null) {
            System.out.println("❌ no report written — the render threw or the run never opened");
            System.exit(1);
        }
        String md = Files.readString(report);
        must(md, "## 🔎 Where the run spent", "report has the new section");
        must(md, "✅ Status | success", "status is in English");
        must(md, "Bash 1 · Edit 1 · Read 1", "root's tool calls, one per tool_use id, not per repeated line");
        must(md, "| `🤖 demo-agent (work)` | 2 | Grep 2 |", "agent's calls come from its own transcript, labelled by its call's description");
        must(md, "Largest single request: **8,010**", "peak context = input + cache read + cache write");
        must(md, "### 💸 Most expensive turns", "top turns listed");
        must(md, "exit 1 · error: token=[REDACTED] rejected", "Bash exit line joined with what failed, redacted");
        mustNot(md, "supersecret123", "no secret reaches the versioned report");
        mustNot(md, "third line", "one line of an error, not the whole output");
        must(md, "| 1 | Context | Which prefix? | banking |", "asked: question and answer kept (0094)");
        must(md, "| 2 | Auth | Credential / scope? | password=[REDACTED] |", "asked: redacted, pipe made table-safe");
        mustNot(md, "hunter2x", "no secret from an answer reaches the versioned report");

        String gap = "— (no price for `fixture-model` in `.claude/audit-usage/pricing.json`)";
        must(md, "| 💰 Estimated cost | " + gap + " |", "header shows the cost cell, naming the unpriced model (018)");
        must(md, "| 💰 estimated cost | " + gap + " |", "aggregate reuses the same cell");

        String history = Files.readString(trail.resolve("history.jsonl"));
        must(history, "\"tool_calls\":\"Grep:2,Bash:1,Edit:1,Read:1\"", "history: run total of tool calls");
        must(history, "\"tool_calls_self\":\"Bash:1,Edit:1,Read:1\"", "history: root's own tool calls");
        must(history, "\"peak_context\":\"8010\"", "history: run peak");
        must(history, "\"status\":\"✅ success\"", "history: English status");
        must(Files.readString(trail.resolve("nodes.jsonl")), "\"tool_calls\":\"Grep:2\"", "nodes: agent's own tool calls");

        String doctor = runMode(hook, repo, "doctor");
        must(doctor, "no price for fixture-model in pricing.json", "doctor names a recent model with no price (018)");

        String summary = run(hook, repo, "summary", "");
        must(summary, "closed runs: 2", "summary reads the legacy row and the new one");
        must(summary, "failure rate: 1/2", "legacy Portuguese status still classified by its emoji");
        must(summary, "USD 1234.56", "legacy pt-BR cost parsed, not multiplied by a hundred");
        must(summary, "Where the pieces spent", "summary aggregates tool calls per piece");
        must(summary, "Grep 2", "summary counts the agent's calls");

        // Exclusion is the class's (`observer` declares `audited: false`), never a list — 0086.
        // An observer typed mid-run closes that run and opens none; called by the model with
        // no run open, it opens none either.
        Files.createDirectories(repo.resolve(".claude/skills/audit-usage"));
        Files.writeString(repo.resolve(".claude/skills/audit-usage/SKILL.md"),
                "---\nname: audit-usage\ndescription: fixture\n---\n\nbody\n");
        run(hook, repo, "prompt", "{\"session_id\":\"s2\",\"prompt\":\"/demo-skill again\"}");
        run(hook, repo, "prompt", "{\"session_id\":\"s2\",\"prompt\":\"/audit-usage\"}");
        // The Stop that follows: it would render a run the observer wrongly opened.
        run(hook, repo, "flush", "{\"session_id\":\"s2\"}");
        run(hook, repo, "call", "{\"session_id\":\"s3\",\"tool_name\":\"Skill\",\"tool_use_id\":\"t3\","
                + "\"tool_input\":{\"skill\":\"audit-usage\"}}");
        long closedRuns = Files.readAllLines(trail.resolve("history.jsonl")).stream()
                .filter(l -> !l.isBlank()).count();
        boolean observerReport;
        try (var s = Files.list(trail)) {
            observerReport = s.anyMatch(f -> f.getFileName().toString().endsWith("--audit-usage.md"));
        }
        check(closedRuns == 3, "an observer typed mid-run closes the run in progress (3 ledger rows, got "
                + closedRuns + ")");
        check(!observerReport, "an observer leaves no report of its own — its class says `audited: false`");
        check(!Files.exists(trail.resolve(".state/s3.ndjson")),
                "a model call to an observer with no run open opens none");

        // `ops` declares `audited: false` too: git-publish's own report would land after its
        // commit and leave the tree dirty on every publish (lessons-learned-012 § 15).
        Files.createDirectories(repo.resolve(".claude/skills/git-publish"));
        Files.writeString(repo.resolve(".claude/skills/git-publish/SKILL.md"),
                "---\nname: git-publish\ndescription: fixture\n---\n\nbody\n");
        run(hook, repo, "prompt", "{\"session_id\":\"s4\",\"prompt\":\"/git-publish\"}");
        run(hook, repo, "flush", "{\"session_id\":\"s4\"}");
        run(hook, repo, "call", "{\"session_id\":\"s5\",\"tool_name\":\"Skill\",\"tool_use_id\":\"t5\","
                + "\"tool_input\":{\"skill\":\"git-publish\"}}");
        boolean publishReport;
        try (var s = Files.list(trail)) {
            publishReport = s.anyMatch(f -> f.getFileName().toString().endsWith("--git-publish.md"));
        }
        check(!publishReport && !Files.exists(trail.resolve(".state/s5.ndjson")),
                "git-publish opens no run of its own, typed or called by the model");

        // The project's audited.json sets a piece on or off over its class default; arch-adopt's
        // own class override is fixed, out of the file's reach — 0111.
        for (String n : new String[] {"arch-adopt", "report-issue"}) {
            Files.createDirectories(repo.resolve(".claude/skills/" + n));
            Files.writeString(repo.resolve(".claude/skills/" + n + "/SKILL.md"),
                    "---\nname: " + n + "\ndescription: fixture\n---\n\nbody\n");
        }
        run(hook, repo, "prompt", "{\"session_id\":\"s6\",\"prompt\":\"/arch-adopt\"}");
        run(hook, repo, "flush", "{\"session_id\":\"s6\"}");
        check(!reportOf(trail, "arch-adopt"), "arch-adopt is never audited, with no project file");

        Files.writeString(trail.resolve("audited.json"),
                "{\"skills\":{\"report-issue\":false,\"audit-usage\":true,\"arch-adopt\":true}}");
        run(hook, repo, "prompt", "{\"session_id\":\"s7\",\"prompt\":\"/report-issue\"}");
        run(hook, repo, "flush", "{\"session_id\":\"s7\"}");
        run(hook, repo, "call", "{\"session_id\":\"s8\",\"tool_name\":\"Skill\",\"tool_use_id\":\"t8\","
                + "\"tool_input\":{\"skill\":\"report-issue\"}}");
        check(!reportOf(trail, "report-issue") && !Files.exists(trail.resolve(".state/s8.ndjson")),
                "audited.json `false` takes an audited piece out, typed or called by the model");
        run(hook, repo, "prompt", "{\"session_id\":\"s9\",\"prompt\":\"/audit-usage\"}");
        run(hook, repo, "flush", "{\"session_id\":\"s9\"}");
        check(reportOf(trail, "audit-usage"), "audited.json `true` puts back a piece its class leaves out");
        run(hook, repo, "prompt", "{\"session_id\":\"s10\",\"prompt\":\"/arch-adopt\"}");
        run(hook, repo, "flush", "{\"session_id\":\"s10\"}");
        check(!reportOf(trail, "arch-adopt"), "audited.json cannot turn arch-adopt back on");
        must(runMode(hook, repo, "doctor"), "`arch-adopt` is fixed by skill_classes.classes.build.overrides",
                "doctor refuses arch-adopt in audited.json");

        Files.writeString(trail.resolve("audited.json"),
                "{\"skills\":{\"nope\":false,\"report-issue\":\"no\"},\"redact\":{}}");
        String bad = runMode(hook, repo, "doctor");
        must(bad, "`nope` — no .claude/skills/nope/SKILL.md", "doctor names a piece that does not exist");
        must(bad, "`report-issue` must be true or false", "doctor names a non-boolean value");
        must(bad, "`redact` — only `skills` and `agents` are allowed", "doctor refuses any other key");

        Files.writeString(trail.resolve("audited.json"), "{\"skills\":{\"report-issue\":false}}");
        must(runMode(hook, repo, "doctor"), "✅ report-issue off", "doctor lists what the file sets");

        // A background agent's notice and its hand-back are prompts the harness injects, not
        // user intent: neither closes the run that launched the agent, nor overwrites the
        // observer file. The next prompt the user types does close it — 0041, 0119.
        run(hook, repo, "prompt", "{\"session_id\":\"s11\",\"prompt\":\"/demo-skill background\"}");
        long before = rows(trail);
        run(hook, repo, "prompt", "{\"session_id\":\"s11\",\"prompt\":\"<agent-message from=\\\"a1\\\">\\n"
                + "[Subagent hand-back] PROGRESS UPDATE (not final)\\n</agent-message>\"}");
        check(rows(trail) == before && Files.exists(trail.resolve(".state/s11.ndjson")),
                "an <agent-message> hand-back leaves the run open (0119)");
        run(hook, repo, "prompt", "{\"session_id\":\"s11\",\"prompt\":\"<task-notification>\\n"
                + "<task-id>a1</task-id>\\n<status>completed</status>\\n</task-notification>\"}");
        check(rows(trail) == before && Files.exists(trail.resolve(".state/s11.ndjson")),
                "a <task-notification> leaves the run open (0041)");
        check(!Files.exists(trail.resolve(".state/s11.prompt.json")),
                "neither harness prompt overwrites the observer file");
        run(hook, repo, "prompt", "{\"session_id\":\"s11\",\"prompt\":\"thanks, looks good\"}");
        check(rows(trail) == before + 1, "the user's next prompt closes the run");

        growth(hook, repo, trail, transcripts);

        if (failures > 0) {
            System.err.println("❌ " + failures + " check(s) failed — `audit` is NOT rendering where the run"
                    + " spent, is leaking an unredacted error into the versioned trail, or is recording"
                    + " a piece its class, its fixed override or the project's audited.json takes out of the trail,"
                    + " or is closing a run on a prompt the harness injected, or is misplacing what grew the context.");
            System.exit(1);
        }
        System.out.println("✅ audit: tool calls, top turns, peak context and redacted errors rendered;"
                + " ledgers and summary carry them next to legacy rows; cache split and context growth per piece.");
    }

    /**
     * A chained executor group whose context grows by known amounts, then compacts: each piece
     * carries its cache split and its call's description, and the call whose result later
     * requests re-read the most ranks first, its re-reads stopping at the compaction — 0132.
     * Context per request (input + cache read + cache write), output 100 / 100 / 100 / 10 / 10:
     * 1,000 → 6,100 → 6,400 → 6,550 → 6,570 → 1,000. The Read at request 1 adds 6,100 − 1,000 −
     * 100 = 5,000, re-read by requests 3–5 (the 6th compacted): 15,000. The Bash at request 2
     * adds 200, re-read twice; the Edit at 3 adds 50, once. Requests 4 and 5 call no tool.
     */
    static void growth(Path hook, Path repo, Path trail, Path transcripts) throws Exception {
        Path main = transcripts.resolve("s12.jsonl");
        Path subs = transcripts.resolve("s12/subagents");
        Files.createDirectories(subs);
        Files.writeString(main, "");
        String tp = main.toString().replace("\\", "\\\\");
        String big = repo.resolve("src/Big.java").toString().replace("\\", "\\\\");
        run(hook, repo, "prompt", "{\"session_id\":\"s12\",\"prompt\":\"/demo-skill growth\","
                + "\"transcript_path\":\"" + tp + "\"}");
        run(hook, repo, "call", "{\"session_id\":\"s12\",\"tool_name\":\"Agent\",\"tool_use_id\":\"toolu_g\","
                + "\"tool_input\":{\"subagent_type\":\"demo-agent\",\"description\":\"group tests\"}}");
        Files.writeString(subs.resolve("agent-def.meta.json"), "{\"toolUseId\":\"toolu_g\"}");
        String text = "{\"type\":\"text\",\"text\":\"…\"}";
        Files.writeString(subs.resolve("agent-def.jsonl"), String.join("\n",
                assistant(at(30), "g1", usage(0, 100, 0, 1000),
                        "{\"type\":\"tool_use\",\"id\":\"tg1\",\"name\":\"Read\",\"input\":{\"file_path\":\"" + big + "\"}}"),
                assistant(at(31), "g2", usage(0, 100, 1000, 5100),
                        "{\"type\":\"tool_use\",\"id\":\"tg2\",\"name\":\"Bash\",\"input\":{\"command\":"
                        + "\"./mvnw -q verify --token=growthsecret9\\nsecond line\"}}"),
                assistant(at(32), "g3", usage(0, 100, 6100, 300),
                        "{\"type\":\"tool_use\",\"id\":\"tg3\",\"name\":\"Edit\",\"input\":{\"file_path\":\"" + big + "\"}}"),
                assistant(at(33), "g4", usage(0, 10, 6400, 150), text),
                assistant(at(34), "g5", usage(0, 10, 6550, 20), text),
                assistant(at(35), "g6", usage(0, 10, 0, 1000), text)) + "\n");
        run(hook, repo, "agent", "{\"session_id\":\"s12\",\"agent_type\":\"demo-agent\",\"agent_id\":\"def\"}");
        run(hook, repo, "close", "{\"session_id\":\"s12\",\"transcript_path\":\"" + tp + "\"}");

        String md = null;
        try (var s = Files.list(trail)) {
            for (Path f : s.filter(f -> f.getFileName().toString().endsWith("--demo-skill.md")).toList()) {
                String c = Files.readString(f);
                if (c.contains("group tests")) md = c;
            }
        }
        if (md == null) { check(false, "growth: a report names the `group tests` agent call"); return; }
        must(md, "| Piece | Origin | 🧮 Own billable | ♻️ Cache read | 💾 Cache write |", "per piece: cache columns");
        must(md, "| `🤖 demo-agent (group tests)` | nested | 7,900 | 20,050 | 7,570 |",
                "per piece: the agent's own cache read and write, labelled by its call's description");
        must(md, "### 📈 What grew the context", "growth section rendered");
        must(md, "| 1 | `🤖 demo-agent (group tests)` | Read | `src/Big.java` | 5,000 | 3 | 15,000 |",
                "growth: the Read whose result was re-read most ranks first, re-reads stop at the compaction");
        must(md, "| 2 | `🤖 demo-agent (group tests)` | Bash | `./mvnw -q verify --token=[REDACTED]` | 200 | 2 | 400 |",
                "growth: a command's first line, redacted");
        mustNot(md, "growthsecret9", "growth: no secret from a command reaches the versioned report");
        must(md, "Read +5,000 → 15,000 · Bash +200 → 400 · Edit +50 → 50", "growth: per-tool line of the piece");
        String nodes = Files.readString(trail.resolve("nodes.jsonl"));
        must(nodes, "\"detail\":\"group tests\"", "nodes: the call's description, so a group is a field, not a row position");
        must(nodes, "\"cache_read\":\"20050\",\"cache_write\":\"7570\"", "nodes: the piece's cache split");
        must(nodes, "\"reread\":\"Read:15000,Bash:400,Edit:50\"", "nodes: re-read tokens per tool");
    }

    static long rows(Path trail) throws Exception {
        return Files.readAllLines(trail.resolve("history.jsonl")).stream().filter(l -> !l.isBlank()).count();
    }

    static boolean reportOf(Path trail, String piece) throws Exception {
        try (var s = Files.list(trail)) {
            return s.anyMatch(f -> f.getFileName().toString().endsWith("--" + piece + ".md"));
        }
    }

    static String at(int seconds) { return Instant.now().plusSeconds(seconds).toString(); }

    static String usage(int in, int out, int cacheRead, int cacheWrite) {
        return "{\"input_tokens\":" + in + ",\"output_tokens\":" + out + ",\"cache_read_input_tokens\":"
                + cacheRead + ",\"cache_creation_input_tokens\":" + cacheWrite + "}";
    }

    static String assistant(String ts, String id, String usage, String block) {
        return "{\"type\":\"assistant\",\"timestamp\":\"" + ts + "\",\"message\":{\"model\":\"fixture-model\","
                + "\"id\":\"" + id + "\",\"role\":\"assistant\",\"content\":[" + block + "],\"usage\":" + usage + "}}";
    }

    static void must(String text, String needle, String label) {
        if (text.contains(needle)) { System.out.println("✅ " + label); return; }
        System.out.println("❌ " + label + " — missing: " + needle);
        failures++;
    }

    static void check(boolean ok, String label) {
        if (ok) { System.out.println("✅ " + label); return; }
        System.out.println("❌ " + label);
        failures++;
    }

    static void mustNot(String text, String needle, String label) {
        if (!text.contains(needle)) { System.out.println("✅ " + label); return; }
        System.out.println("❌ " + label + " — found: " + needle);
        failures++;
    }

    static String run(Path hook, Path repo, String phase, String stdin) throws Exception {
        return exec(hook, repo, stdin, "audit", phase);
    }

    static String runMode(Path hook, Path repo, String mode) throws Exception {
        return exec(hook, repo, "", mode);
    }

    static String exec(Path hook, Path repo, String stdin, String... argv) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(
                ProcessHandle.current().info().command().orElse("java"), "-jar", hook.toString()));
        cmd.addAll(java.util.List.of(argv));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(repo.toFile());
        pb.environment().put("CLAUDE_PROJECT_DIR", repo.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();                                 // audit never blocks: the files are the signal
        return out;
    }
}
