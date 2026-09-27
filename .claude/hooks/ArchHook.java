///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// ArchHook — project hooks in a single Java file.
//
// Why Java and not bash+PowerShell: this template targets Java developers,
// so the JDK is the only dependency that can be assumed on any operating system.
// Writing the logic twice (.sh and .ps1) would violate the "each rule has a single
// owner" rule this repository enforces everywhere else — and the two copies would drift.
//
// Execution (Java 11+, single-file source): java ArchHook.java <mode>
//   check   PostToolUse — forbidden imports + incremental compile     (blocks)
//   format  PostToolUse — spotless on the touched module              (never blocks)
//   tests   Stop        — tests of the changed modules                (blocks)
//   schema  Pre/PostToolUse + Stop — frontmatter, injection paths, skill bodies (blocks)
//   audit   lifecycle   — execution trail of every project skill and agent (never blocks)
//   guard   PreToolUse  — a skill writes only its class's territory, approved specs
//                         frozen, build skills unreachable mid-design       (blocks)
//   compose manual      — every compose service up, no foreign container on our ports,
//                         compose image tags equal to the ones src/test pins (never blocks)
//   doctor  manual      — diagnoses the setup on this machine         (never blocks)
//
// Dependencies: JDK. Nothing else.

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class ArchHook {

    static final Path ROOT = Paths.get(
            Optional.ofNullable(System.getenv("CLAUDE_PROJECT_DIR")).orElse("."))
            .toAbsolutePath().normalize();
    static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** Windows consoles default to cp1252: forcing UTF-8 avoids broken accented characters. */
    static final PrintStream ERR = new PrintStream(
            new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "check";
        // Only the modes the runtime invokes as hooks are handed a JSON payload on
        // stdin. Reading it for every mode made `export`, `doctor` and `compose` block
        // forever whenever stdin was neither a closed pipe nor a TTY — a command that
        // hangs with no output, which is how lessons-learned-011 § 2 found it. The list
        // is here and not in extensions.json on purpose: it is the dispatch itself, the
        // same place the mode names already live.
        String stdin = switch (mode) {
            case "check", "format", "tests", "schema", "audit", "guard" -> readAll(System.in);
            default -> "";
        };
        try {
            switch (mode) {
                case "check"  -> check(filePath(stdin));
                case "format" -> format(filePath(stdin));
                case "tests"  -> tests(stdin);
                case "schema" -> schema(stdin);
                case "audit"  -> audit(args.length > 1 ? args[1] : "flush", stdin);
                case "guard"  -> guard(args.length > 1 ? args[1] : "write", stdin);
                case "compose" -> compose();
                case "export" -> export(args);
                case "doctor" -> doctor();
                default -> { err("Unknown mode: " + mode); System.exit(0); }
            }
        } catch (Exception e) {
            // A hook must never crash the session because of its own error.
            err("⚠️  ArchHook (" + mode + ") failed: " + e);
            System.exit(0);
        }
        System.exit(0);
    }

    // ── check ────────────────────────────────────────────────────────────────
    static void check(String file) throws Exception {
        if (file == null || !file.endsWith(".java")) return;
        Path abs = Paths.get(file);
        if (!Files.isRegularFile(abs)) return;
        String rel = relative(abs);

        // 1. forbidden imports
        Path map = ROOT.resolve(".claude/forbidden-imports.txt");
        if (Files.isRegularFile(map)) {
            String src = Files.readString(abs, StandardCharsets.UTF_8);
            List<String> viol = new ArrayList<>();
            for (String line : Files.readAllLines(map, StandardCharsets.UTF_8)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int bar = line.indexOf('|');
                if (bar < 0) continue;
                String module = line.substring(0, bar).strip();
                String prefix = line.substring(bar + 1).strip();
                if (prefix.isEmpty() || !rel.startsWith(module + "/")) continue;
                Pattern p = Pattern.compile(
                        "(?m)^\\s*import\\s+(static\\s+)?" + Pattern.quote(prefix));
                if (p.matcher(src).find()) {
                    viol.add("  " + prefix + "  (forbidden in module '" + module + "')");
                }
            }
            if (!viol.isEmpty()) {
                err("❌ Architectural boundary violation — " + rel);
                err("Forbidden imports found:");
                viol.forEach(ArchHook::err);
                err("");
                err("The responsibility is in the wrong module. Move the code, not the rule.");
                err("Rule: .claude/rules/architecture-ddd.md");
                System.exit(2);
            }
        } else {
            err("⚠️  .claude/forbidden-imports.txt does not exist.");
            err("   Boundary enforcement OFF — nothing was checked.");
            err("   This file is generated by /init-project from the active blueprint.");
        }

        // 2. incremental compile of the touched module
        String module = moduleOf(rel);
        if (module == null) return;                       // no POM yet: mid-generation
        String mvnw = wrapper();
        if (mvnw == null) {
            err("⚠️  Maven wrapper missing — compilation NOT checked.");
            err("   Run /init-project (starter.tgz already brings the mvnw).");
            return;
        }
        Proc r = run(mvnw, "-q", "-o", "-pl", module, "-am", "test-compile");
        if (r.exit != 0) {
            err("❌ Compilation failed in " + module);
            tail(r.out, 30).forEach(ArchHook::err);
            System.exit(2);
        }
    }

    // ── format ───────────────────────────────────────────────────────────────
    static void format(String file) throws Exception {
        if (file == null || !file.endsWith(".java")) return;
        Path abs = Paths.get(file);
        if (!Files.isRegularFile(abs)) return;
        String module = moduleOf(relative(abs));
        String mvnw = wrapper();
        if (module == null || mvnw == null) return;
        run(mvnw, "-q", "-o", "-pl", module, "spotless:apply");   // failure is ignored
    }

    // ── tests ────────────────────────────────────────────────────────────────
    static void tests(String stdin) throws Exception {
        // If the Stop hook already blocked before, don't block again: avoids cycles.
        if (Pattern.compile("\"stop_hook_active\"\\s*:\\s*true").matcher(stdin).find()) return;

        String mvnw = wrapper();
        if (mvnw == null) return;

        Proc diff = run(gitCmd(), "diff", "--name-only", "HEAD");
        Proc untracked = run(gitCmd(), "ls-files", "--others", "--exclude-standard");
        if (diff.exit != 0 && untracked.exit != 0) return;        // no git or no HEAD

        Set<String> modules = Stream.concat(diff.out.stream(), untracked.out.stream())
                .filter(f -> f.endsWith(".java"))
                .map(ArchHook::moduleOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (modules.isEmpty()) return;

        String list = String.join(",", modules);
        err("▶ Tests for affected modules: " + list);
        Proc r = run(mvnw, "-q", "-o", "-pl", list, "-am", "test");
        if (r.exit != 0) {
            err("❌ Tests failed");
            List<String> relevant = r.out.stream()
                    .filter(l -> l.matches(".*(ERROR|FAIL|Tests run).*"))
                    .collect(Collectors.toList());
            tail(relevant.isEmpty() ? r.out : relevant, 25).forEach(ArchHook::err);
            System.exit(2);
        }
        err("✅ Tests green");
    }

    // ── doctor ───────────────────────────────────────────────────────────────
    /** Answers "does this work on my machine?" without having to guess. */
    static void doctor() {
        err("ArchHook doctor");
        err("  OS ................ " + System.getProperty("os.name")
                + (WINDOWS ? "  (Windows — no shell is used, exec form)" : ""));
        err("  Java .............. " + System.getProperty("java.version"));
        err("  Project root ...... " + ROOT);
        report("CLAUDE_PROJECT_DIR", System.getenv("CLAUDE_PROJECT_DIR") != null,
                "set", "NOT set — hooks use the current directory");
        String w = wrapper();
        report("Maven wrapper", w != null, w == null ? "" : w,
                "missing — run /init-project (starter.tgz already brings the mvnw)");
        Path map = ROOT.resolve(".claude/forbidden-imports.txt");
        long rules = 0;
        try {
            if (Files.isRegularFile(map)) {
                rules = Files.readAllLines(map).stream()
                        .map(String::strip)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#") && l.contains("|"))
                        .count();
            }
        } catch (IOException ignored) { }
        report("Boundaries", rules > 0, rules + " active rules",
                "0 rules — ENFORCEMENT OFF. Generated by /init-project");
        long badSchema = -1;
        if (Files.isRegularFile(ROOT.resolve(SCHEMA_FILE))) {
            List<String> errs = new ArrayList<>();
            try {
                sweep(asMap(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE)))), errs);
                badSchema = errs.size();
            } catch (IOException ignored) { }
        }
        report("Schema", badSchema == 0,
                "all extension files pass",
                badSchema < 0 ? "no " + SCHEMA_FILE + " — validation OFF"
                              : badSchema + " files with invalid frontmatter");
        Path auditDir = auditDir();
        if (Files.isDirectory(auditDir)) {
            long runs = 0;
            try (Stream<Path> s = Files.list(auditDir)) {
                runs = s.filter(f -> f.toString().endsWith(".md")).count();
            } catch (IOException ignored) { }
            boolean priced = Files.isRegularFile(auditDir.resolve("pricing.json"));
            report("Audit", true, runs + " execution(s) recorded"
                    + (priced ? "" : " — pricing.json missing, no cost estimate"), "");

            // A synthetic touched file that matches a real rule's `paths` — the exact
            // shape that once threw `ArrayIndexOutOfBoundsException` inside rule
            // inference and froze every report from that point on, silently (the
            // top-level catch in `main` exits 0). Cheap enough to run every `doctor`.
            boolean auditRulesOk;
            try {
                auditRules(Set.of("src/main/java/example/domain/model/Sample.java"));
                auditRulesOk = true;
            } catch (Exception ex) {
                auditRulesOk = false;
            }
            report("Audit rule inference", auditRulesOk,
                    "renders without error on a touched .java file",
                    "auditRules() throws — every report freezes once a run touches"
                            + " src/**. See ArchHook.java's auditRules()");
        } else {
            err("  Audit ............. ⚪ no " + AUDIT_DIR + " — execution trail OFF (optional)");
        }

        Path mcpFile = ROOT.resolve(".mcp.json");
        if (Files.isRegularFile(mcpFile)) {
            List<String> mcpErrs = new ArrayList<>();
            Map<String, Object> schRoot = asMap(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE))));
            String mcpContent = readOrNull(mcpFile);
            checkMcp(schRoot == null ? null : asMap(schRoot.get("mcp")), ".mcp.json",
                    mcpContent, mcpErrs);
            Map<String, Object> mcpRoot = asMap(Json.parse(mcpContent));
            Map<String, Object> servers = mcpRoot == null ? null : asMap(mcpRoot.get("mcpServers"));
            int n = servers == null ? 0 : servers.size();
            report("MCP", mcpErrs.isEmpty(), n + " server(s) declared in .mcp.json",
                    mcpErrs.size() + " problem(s) — run `java ArchHook.java schema`");
        } else {
            err("  MCP ................ no .mcp.json — nothing declared (optional)");
        }
        Path settingsFile = ROOT.resolve(".claude/settings.json");
        if (Files.isRegularFile(settingsFile)) {
            List<String> hookErrs = new ArrayList<>();
            Map<String, Object> schRoot = asMap(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE))));
            String settingsContent = readOrNull(settingsFile);
            if (schRoot != null) {
                checkSettings(schRoot, ".claude/settings.json", settingsContent, hookErrs);
            }
            Map<String, Object> sRoot = asMap(Json.parse(settingsContent));
            Map<String, Object> hookMap = sRoot == null ? null : asMap(sRoot.get("hooks"));
            int entries = 0;
            if (hookMap != null) {
                for (Object groups : hookMap.values()) {
                    for (Object group : asList(groups)) {
                        Map<String, Object> g = asMap(group);
                        if (g != null) entries += asList(g.get("hooks")).size();
                    }
                }
            }
            int evts = hookMap == null ? 0 : hookMap.size();
            report("Hooks", hookErrs.isEmpty(),
                    entries + " registration(s) across " + evts + " event(s)",
                    hookErrs.size() + " problem(s) — run `java ArchHook.java schema`");
        } else {
            err("  Hooks .............. no .claude/settings.json — no hook registered");
        }

        provenance();

        ComposeReport comp = composeReport();
        report("Compose", comp.ok(), comp.summary(), comp.summary());
        for (String d : comp.detail()) err("    " + d);

        boolean git = false;
        try { git = run("git", "rev-parse", "HEAD").exit == 0; } catch (Exception ignored) { }
        report("git HEAD", git, "exists",
                "no commits — the tests hook does not run (git diff HEAD fails)");

        if (git) {
            String ucRoot = ucReferenceRoot();
            if (ucRoot != null && !Files.isDirectory(ROOT.resolve(ucRoot))) {
                report("UC references", true, "no " + ucRoot + " — nothing to check (optional)", "");
            } else {
                List<String> orphans = orphanUseCaseRefs();
                report("UC references", orphans.isEmpty(), "every cited use-case folder exists",
                        orphans.size() + " citation(s) point at a folder that is gone");
                for (String o : orphans) err("    " + o);
            }
        }
        err("");
        err(rules > 0 && w != null
                ? "✅ Setup operational."
                : "⚠️  Setup incomplete — see marked lines above.");
    }

    /**
     * Where this `.claude/` came from, and what has been edited since. Reads the stamp
     * `export` writes; in the repository that produces one there is no stamp and none is
     * expected, which is a third state and not a failure. The comparison is local: an
     * update overwrites (D54), so the list of locally edited files is the list of what
     * that update would discard, and it is the only thing a person can act on before
     * running it. How far behind the source is cannot be answered without reaching the
     * source — `arch-adopt` does that when it fetches; `doctor` does not open the
     * network.
     */
    static void provenance() {
        Path stamp = ROOT.resolve(".claude/.arch-provenance.json");
        boolean origin = Files.isDirectory(ROOT.resolve(".claude/blueprints"))
                && get(asMap(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE)))), "export") != null;
        if (!Files.isRegularFile(stamp)) {
            err("  Provenance ........ " + (origin
                    ? "⚪ origin repository — writes stamps, carries none"
                    : "⚠️  no stamp — this .claude/ was not written by `export`"));
            return;
        }
        Map<String, Object> s = asMap(Json.parse(readOrNull(stamp)));
        if (s == null) {
            report("Provenance", false, "", ".arch-provenance.json is not valid JSON");
            return;
        }
        Map<String, Object> files = asMap(s.get("files"));
        List<String> changed = new ArrayList<>();
        int missing = 0;
        if (files != null) {
            for (Map.Entry<String, Object> e : files.entrySet()) {
                String cur = readOrNull(ROOT.resolve(e.getKey()));
                if (cur == null) { missing++; changed.add(e.getKey() + " (missing)"); }
                else if (!sha256(cur).equals(asStr(e.getValue()))) changed.add(e.getKey());
            }
        }
        int tracked = files == null ? 0 : files.size();
        report("Provenance", changed.isEmpty(),
                "blueprint " + orDash(asStr(s.get("blueprint"))) + " · ref "
                        + orDash(asStr(s.get("ref"))) + " · " + orDash(asStr(s.get("exported_at")))
                        + " · " + tracked + " file(s) unchanged since",
                changed.size() + " of " + tracked + " exported file(s) edited locally"
                        + (missing > 0 ? " (" + missing + " missing)" : "")
                        + " — an update overwrites them");
        for (String c : changed.stream().sorted().limit(8).collect(Collectors.toList())) {
            err("    " + c);
        }
        if (changed.size() > 8) err("    … and " + (changed.size() - 8) + " more");
    }

    /**
     * Versioned files citing a `docs/use-cases/UC-NNN-slug/` folder that no longer exists.
     * Deleting a use case folder leaves its citations behind — a `docker-compose.yml`
     * comment pointing at `25-mensageria.md` of a case renamed in the next run
     * (lessons-learned-012 § 14) — and nothing sweeps for them, because each citation is
     * correct in the commit that wrote it. Reported, never blocking: a stale reference is
     * a documentation defect, and `doctor` is where a person is already reading.
     * Pattern, scanned extensions and exemptions are data — `doctor.uc_references` in
     * .claude/schemas/extensions.json (invariant 10).
     */
    static Map<String, Object> ucReferenceSpec() {
        try {
            return asMap(get(asMap(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE)))),
                    "doctor", "uc_references"));
        } catch (Exception e) { return null; }
    }

    /** The directory whose absence means there is nothing to sweep, or null. */
    static String ucReferenceRoot() {
        Map<String, Object> spec = ucReferenceSpec();
        return spec == null ? null : asStr(spec.get("root"));
    }

    static List<String> orphanUseCaseRefs() {
        List<String> found = new ArrayList<>();
        Map<String, Object> spec = ucReferenceSpec();
        if (spec == null) return found;
        String pat = asStr(spec.get("pattern"));
        if (pat == null || pat.isEmpty()) return found;
        // No use-case directory, nothing a citation can be stale against. That is this
        // meta-repository: the check belongs to the generated project.
        String root = asStr(spec.get("root"));
        if (root != null && !root.isEmpty() && !Files.isDirectory(ROOT.resolve(root))) return found;
        List<String> exts = asStrList(spec.get("extensions"));
        List<String> skip = asStrList(spec.get("exempt_paths"));

        List<String> files;
        try {
            Proc p = run("git", "ls-files");
            if (p.exit() != 0) return found;
            files = p.out();
        } catch (Exception e) { return found; }

        Pattern re = Pattern.compile(pat);
        for (String f : files) {
            if (!exts.isEmpty() && exts.stream().noneMatch(f::endsWith)) continue;
            if (skip.stream().anyMatch(f::startsWith)) continue;
            String body = readOrNull(ROOT.resolve(f));
            if (body == null) continue;
            Set<String> seen = new LinkedHashSet<>();
            Matcher m = re.matcher(body);
            while (m.find()) {
                String folder = m.group(m.groupCount() >= 1 ? 1 : 0);
                if (!seen.add(folder)) continue;
                if (!Files.isDirectory(ROOT.resolve(folder))) {
                    found.add(f + " cites " + folder + ", which does not exist");
                }
            }
        }
        return found;
    }

    static void report(String label, boolean ok, String yes, String no) {
        String pad = "                  ".substring(Math.min(label.length(), 17));
        err("  " + label + " " + pad.replace(' ', '.') + " " + (ok ? "✅ " + yes : "❌ " + no));
    }

    // ── compose ──────────────────────────────────────────────────────────────
    //
    // Three questions `docker compose up -d` does not answer, all cheap:
    //   1. Is every service of this project actually running — not `created`, not
    //      `exited`?
    //   2. Is a container from ANOTHER project publishing a host port this project's
    //      compose file also declares?
    //   3. Does every `image:` of the compose file carry the same tag the test suite
    //      pins for that same repository in `DockerImageName.parse(...)`?
    //
    // Question 3 needs no Docker at all — it compares two files — and it is here because
    // this mode already owns `docker-compose.yml`. Two skills used to promise the match in
    // prose (`docker-architect` step 4, `test-architect`'s setup mode), with a YAML comment
    // as the only link between the halves and the execution order deciding which side led.
    // A promise that has to hold is a hook, not a paragraph — `@CLAUDE.md` invariant 6.
    // The mismatch is silent: the test suite passes against an engine version nobody runs.
    //
    // Why this is a hook and not a paragraph in docker-architect/SKILL.md: `docker
    // compose up -d` exits 0 even when an individual service never starts. A container
    // that cannot bind its published host port stays in `Created`, and the command still
    // reports success. It happened for real — an `otel-collector` left running for eight
    // days by a sibling project generated from the same blueprint held host port 4318;
    // this project's collector sat in `Created`; the application shipped every span and
    // metric to the wrong container, which answered with its own older config. Two
    // investigations, one `docker ps` away from the answer. Prose only helps whoever
    // reads it at the right moment. This runs.
    //
    // Never blocks, and Docker is not a dependency of this repository: no compose file,
    // no `docker` on PATH, or a daemon that is down all report and return.

    /** Seconds each `docker` call gets before it is given up on. See {@link #runTimed}. */
    static final int DOCKER_TIMEOUT = 10;

    record ComposeReport(boolean ok, String summary, List<String> detail) {}

    static void compose() {
        ComposeReport r = composeReport();
        err("ArchHook compose");
        err("  Project root ...... " + ROOT);
        report("Compose", r.ok(), r.summary(), r.summary());
        for (String d : r.detail()) err("    " + d);
        err("");
        err(r.ok() ? "✅ Compose healthy." : "⚠️  See the marked lines above.");
    }

    /**
     * Diagnoses this project's compose services. Shared by `compose` and `doctor` so the
     * two can never disagree about what "healthy" means.
     */
    static ComposeReport composeReport() {
        Path file = Stream.of("docker-compose.yml", "docker-compose.yaml", "compose.yml",
                        "compose.yaml")
                .map(ROOT::resolve).filter(Files::isRegularFile).findFirst().orElse(null);
        if (file == null) {
            return new ComposeReport(true, "no compose file — nothing to check (optional)",
                    List.of());
        }

        Map<String, Set<String>> declared = composeHostPorts(readOrNull(file));

        // Files only, no daemon: computed before the first `docker` call so it survives
        // every early return below. A machine with Docker off still gets this answer.
        List<String> tagIssues = imageTagMismatches(file);

        Proc ps;
        try {
            ps = runTimed(DOCKER_TIMEOUT, "docker", "compose", "ps", "-a", "--format", "json");
        } catch (Exception e) {
            return withTags(tagIssues,
                    "docker not on PATH — service state not checked (optional)");
        }
        if (ps.exit() == -1) {
            return withTags(tagIssues, "`docker compose ps` did not answer in "
                    + DOCKER_TIMEOUT + "s (daemon starting?) — not checked");
        }
        if (ps.exit() != 0) {
            return withTags(tagIssues,
                    "`docker compose ps` failed (daemon down?) — not checked");
        }

        List<String> detail = new ArrayList<>();
        Set<String> ours = new LinkedHashSet<>();
        int running = 0, total = 0;
        for (Object o : composeEntries(ps.out())) {
            Map<String, Object> m = asMap(o);
            if (m == null) continue;
            total++;
            String name = orDash(asStr(m.get("Name")));
            String svc = orDash(asStr(m.get("Service")));
            String state = Optional.ofNullable(asStr(m.get("State"))).orElse("?")
                    .toLowerCase(Locale.ROOT);
            ours.add(name);
            if (state.startsWith("running")) { running++; continue; }
            detail.add("service `" + svc + "` is " + state + ", not running"
                    + ("created".equals(state)
                            ? " — a `created` container usually failed to bind a"
                              + " published port; see the port collisions below"
                            : "")
                    + "  →  docker compose logs " + svc);
        }

        // A foreign container holding one of our host ports. This is the check that would
        // have answered lessons-learned-008 in seconds, and it runs even when every
        // service above is fine: the collision is what stops a service from starting.
        Set<String> wanted = declared.values().stream().flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!wanted.isEmpty()) {
            try {
                Proc all = runTimed(DOCKER_TIMEOUT, "docker", "ps",
                        "--format", "{{.Names}}\t{{.Ports}}");
                if (all.exit() == 0) {
                    for (String line : all.out()) {
                        int tab = line.indexOf('\t');
                        if (tab < 0) continue;
                        String name = line.substring(0, tab).strip();
                        if (ours.contains(name)) continue;
                        Set<String> held = publishedPorts(line.substring(tab + 1));
                        held.retainAll(wanted);
                        for (String p : held) {
                            detail.add("host port " + p + " is held by `" + name
                                    + "`, a container of ANOTHER project — "
                                    + declaredBy(declared, p) + " here cannot bind it"
                                    + "  →  docker stop " + name);
                        }
                    }
                }
            } catch (Exception ignored) {
                // `docker compose ps` worked, so this failing is not worth a line.
            }
        }

        detail.addAll(tagIssues);
        boolean ok = detail.isEmpty();
        String summary = ok
                ? running + "/" + total
                        + " service(s) running, no port collision, image tags match"
                : detail.size() + " problem(s) — " + running + "/" + total + " running";
        return new ComposeReport(ok, summary, detail);
    }

    /**
     * A report whose service-state half could not be checked. The tag comparison reads
     * files only, so it still counts — and still fails the report on a mismatch, however
     * unreachable the daemon is.
     */
    static ComposeReport withTags(List<String> tagIssues, String summary) {
        if (tagIssues.isEmpty()) return new ComposeReport(true, summary, List.of());
        return new ComposeReport(false,
                tagIssues.size() + " image tag mismatch(es); " + summary, tagIssues);
    }

    /** Accepts both shapes `docker compose ps --format json` emits: an array, or one object per line. */
    static List<Object> composeEntries(List<String> out) {
        String joined = String.join("\n", out).strip();
        if (joined.isEmpty()) return List.of();
        Object arr = Json.parse(joined);
        if (arr instanceof List) return asList(arr);
        List<Object> entries = new ArrayList<>();
        for (String line : out) {
            String s = line.strip();
            if (!s.startsWith("{")) continue;
            Object o = Json.parse(s);
            if (o != null) entries.add(o);
        }
        return entries;
    }

    /**
     * Host ports each service publishes, read from the compose file itself rather than
     * from `docker ps`: a service that never started publishes nothing, and that is
     * precisely the one whose port is being held by someone else.
     */
    static Map<String, Set<String>> composeHostPorts(String yaml) {
        Map<String, Set<String>> byService = new LinkedHashMap<>();
        if (yaml == null) return byService;
        boolean inServices = false, inPorts = false;
        String service = null;
        for (String raw : yaml.split("\r?\n", -1)) {
            String line = raw.stripTrailing();
            if (line.isBlank() || line.strip().startsWith("#")) continue;
            int indent = line.length() - line.stripLeading().length();
            String body = line.strip();

            if (indent == 0) {                       // top-level key
                inServices = body.startsWith("services:");
                inPorts = false;
                service = null;
                continue;
            }
            if (!inServices) continue;
            if (indent == 2 && body.endsWith(":")) { // a service name
                service = body.substring(0, body.length() - 1).strip();
                inPorts = false;
                continue;
            }
            if (service == null) continue;
            if (indent == 4) {                       // a key inside the service
                inPorts = body.startsWith("ports:");
                continue;
            }
            if (inPorts && body.startsWith("- ")) {
                String p = hostPort(body.substring(2).strip());
                if (p != null) byService.computeIfAbsent(service, k -> new LinkedHashSet<>()).add(p);
            }
        }
        return byService;
    }

    /**
     * The host side of one compose `ports:` entry, or null when there is none to collide
     * over — the short form `"4318"` asks Docker for an ephemeral port, which is the
     * whole point of writing it that way.
     */
    static String hostPort(String entry) {
        String s = entry.replace("\"", "").replace("'", "").strip();
        // ${VAR:-16686} resolves to its default; a bare ${VAR} has no value to compare.
        s = Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*:-([^}]*)}").matcher(s).replaceAll("$1");
        if (s.contains("${")) return null;
        int slash = s.indexOf('/');                  // strip /tcp, /udp
        if (slash >= 0) s = s.substring(0, slash);
        String[] parts = s.split(":");
        if (parts.length < 2) return null;           // ephemeral short form
        String host = parts[parts.length - 2].strip();
        return host.matches("\\d+(-\\d+)?") ? host : null;
    }

    /** One image reference pinned by a test file: repository, tag, and where it was read. */
    record ImagePin(String repo, String tag, String where) {}

    /**
     * Compose services whose `image:` tag disagrees with the tag `src/test` pins for the
     * same repository. Compared per repository, reported per tag: `postgres:16-alpine` in
     * the compose file against `postgres:15` in `TestcontainersConfiguration.java` is the
     * whole failure mode — the suite proves nothing about the engine that actually runs.
     *
     * <p>A repository that appears on only one side is not a mismatch: a Testcontainers-only
     * dependency needs no compose service, and a compose service can exist with no test
     * touching it.
     */
    static List<String> imageTagMismatches(Path composeFile) {
        Map<String, String> images = composeImages(readOrNull(composeFile));
        if (images.isEmpty()) return List.of();
        List<ImagePin> pins = testImagePins();
        if (pins.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();
        String compose = relative(composeFile);
        for (Map.Entry<String, String> e : images.entrySet()) {
            String[] img = splitImage(e.getValue());
            if (img == null) continue;
            for (ImagePin pin : pins) {
                if (!pin.repo().equals(img[0]) || pin.tag().equals(img[1])) continue;
                out.add("image tag mismatch for `" + img[0] + "`: " + compose + " service `"
                        + e.getKey() + "` pins `" + img[1] + "`, " + pin.where() + " pins `"
                        + pin.tag() + "` — the tests then run against a different version"
                        + " than `docker compose up` does  →  make both the same tag");
            }
        }
        return out;
    }

    /** The `image:` value of each compose service. Same walk as {@link #composeHostPorts}. */
    static Map<String, String> composeImages(String yaml) {
        Map<String, String> byService = new LinkedHashMap<>();
        if (yaml == null) return byService;
        boolean inServices = false;
        String service = null;
        for (String raw : yaml.split("\r?\n", -1)) {
            String line = raw.stripTrailing();
            if (line.isBlank() || line.strip().startsWith("#")) continue;
            int indent = line.length() - line.stripLeading().length();
            String body = line.strip();

            if (indent == 0) {                       // top-level key
                inServices = body.startsWith("services:");
                service = null;
                continue;
            }
            if (!inServices) continue;
            if (indent == 2 && body.endsWith(":")) { // a service name
                service = body.substring(0, body.length() - 1).strip();
                continue;
            }
            if (service != null && indent == 4 && body.startsWith("image:")) {
                byService.put(service, body.substring("image:".length()).strip());
            }
        }
        return byService;
    }

    /**
     * Every `DockerImageName.parse("…")` literal under a `src/test` tree, with the file it
     * came from. Read from the source rather than from a running container: the mismatch
     * has to be visible before anyone starts anything.
     */
    static List<ImagePin> testImagePins() {
        List<ImagePin> pins = new ArrayList<>();
        Pattern re = Pattern.compile("DockerImageName\\s*\\.\\s*parse\\s*\\(\\s*\"([^\"]+)\"");
        try {
            Files.walkFileTree(ROOT, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes a) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    // Build output and VCS metadata hold copies and no source of truth.
                    return Set.of("target", "build", ".git", "node_modules", ".idea")
                            .contains(name) ? FileVisitResult.SKIP_SUBTREE
                                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, java.nio.file.attribute.BasicFileAttributes a) {
                    String rel = relative(f);
                    if (!rel.endsWith(".java") || !rel.contains("src/test/")) {
                        return FileVisitResult.CONTINUE;
                    }
                    String content = readOrNull(f);
                    if (content == null) return FileVisitResult.CONTINUE;
                    Matcher m = re.matcher(content);
                    while (m.find()) {
                        String[] img = splitImage(m.group(1));
                        if (img != null) pins.add(new ImagePin(img[0], img[1], rel));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;   // unreadable file is not a failure
                }
            });
        } catch (IOException ignored) {
            // No `src/test` yet, or an unreadable tree: nothing to compare, not a failure.
        }
        return pins;
    }

    /**
     * An image reference split into repository and tag, or null when there is nothing to
     * compare. A digest (`repo@sha256:…`) and an unresolved `${VAR}` both return null: the
     * first pins something stronger than a tag, the second has no value to read here.
     * `${VAR:-17-alpine}` resolves to its default, same as {@link #hostPort} does.
     */
    static String[] splitImage(String ref) {
        String s = ref.replace("\"", "").replace("'", "").strip();
        s = Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*:-([^}]*)}").matcher(s).replaceAll("$1");
        if (s.isEmpty() || s.contains("${") || s.contains("@")) return null;
        int colon = s.lastIndexOf(':');
        // A colon before the last slash is a registry port (`localhost:5000/postgres`),
        // not a tag: such a reference carries no tag at all.
        if (colon < 0 || s.indexOf('/', colon) >= 0) return new String[] {s, "latest"};
        return new String[] {s.substring(0, colon), s.substring(colon + 1)};
    }

    /** Host ports out of a `docker ps` Ports column: `0.0.0.0:4318->4318/tcp, [::]:4318->4318/tcp`. */
    static Set<String> publishedPorts(String ports) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(\\d+)(?:-(\\d+))?->").matcher(ports);
        while (m.find()) found.add(m.group(2) == null ? m.group(1) : m.group(1) + "-" + m.group(2));
        return found;
    }

    static String declaredBy(Map<String, Set<String>> declared, String port) {
        List<String> svcs = declared.entrySet().stream()
                .filter(e -> e.getValue().contains(port)).map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return svcs.isEmpty() ? "a service" : "`" + String.join("`, `", svcs) + "`";
    }

    // ── export ───────────────────────────────────────────────────────────────
    //
    // Writes a target project's `.claude/` from this repository's, transformed for one
    // blueprint: rules with their `paths` derived from `packages.map`, skills and agents
    // without the subdirectories that only serve the meta-repo, and every citation to
    // `decisions/` or `blueprints/` cut, since neither exists inside a project.
    //
    // Form 7c of `claude-code-architect-designer`, motivated by axis 7 of its interview:
    // the transformation runs unattended on other people's machines — `arch-adopt` pulls
    // this repository and invokes this mode — so the same input has to produce the same
    // tree. The closest rejected form was a skill in prose, which is what steps 6.6 to 7
    // of `project-bootstrap` are today: a model re-deriving the copy on every run, at the
    // reader's expense and with no way for the CI to check it. No lifecycle event invokes
    // this mode; like `compose`, it is called by hand and by a skill.
    //
    // WHAT it copies and HOW each piece is transformed is not here: it is the `export`
    // block of .claude/schemas/extensions.json — invariant 10 — and `schema` cross-checks
    // that block against what is on disk.
    // Design: .claude/decisions/0054-deterministic-export-provenance-plugin.md

    record Blueprint(String id, Map<String, String> packages, List<String> archPaths,
                     List<String> vocabulary) {}

    static void export(String[] args) throws Exception {
        String dest = null, blueprintId = null, ref = "working-tree";
        boolean dry = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--dry-run")) dry = true;
            else if (a.equals("--blueprint") && i + 1 < args.length) blueprintId = args[++i];
            else if (a.equals("--ref") && i + 1 < args.length) ref = args[++i];
            else if (a.startsWith("--")) { err("❌ Unknown option: " + a); exportUsage(); System.exit(2); }
            else if (dest == null) dest = a;
            else { err("❌ Only one destination is accepted, got also: " + a); exportUsage(); System.exit(2); }
        }
        if (dest == null || blueprintId == null) { exportUsage(); System.exit(2); }

        Map<String, Object> sch = asMap(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE))));
        Map<String, Object> exp = sch == null ? null : asMap(sch.get("export"));
        if (exp == null) {
            err("❌ No `export` block in " + SCHEMA_FILE + " — there is nothing to copy.");
            err("   This mode reads its lists from that file; it hardcodes none.");
            System.exit(2);
        }

        Path destRoot = Paths.get(dest).toAbsolutePath().normalize();
        if (destRoot.equals(ROOT)) {
            err("❌ The destination is this repository itself: " + destRoot);
            err("   Export writes a project's .claude/ from this one; give it another path.");
            System.exit(2);
        }
        Blueprint bp = loadBlueprint(exp, blueprintId);

        Map<String, String> out = new TreeMap<>();
        List<String> notes = new ArrayList<>();
        exportBlueprint(exp, bp, out);
        exportRules(exp, bp, out);
        exportTree(exp, "skills", out);
        exportTree(exp, "agents", out);
        exportFiles(exp, bp, out, notes);
        String stampFile = asStr(get(sch, "source", "stamp_file")) == null
                ? ".claude/.arch-provenance.json"
                : asStr(get(sch, "source", "stamp_file"));
        out.put(stampFile, stamp(bp, ref, out));

        List<String> residue = new ArrayList<>();
        List<String> exempt = asStrList(get(exp, "body_transforms", "residue_exempt"));
        for (String marker : asStrList(get(exp, "body_transforms", "residue_markers"))) {
            for (Map.Entry<String, String> e : out.entrySet()) {
                // The stamp is a list of the paths just written, one of which is now the
                // blueprint's — a path, not a citation, and this mode wrote it.
                if (e.getKey().equals(stampFile) || exempt.contains(e.getKey())) continue;
                if (e.getValue().contains(marker)) residue.add(e.getKey() + " still carries `" + marker + "`");
            }
        }

        err("ArchHook export");
        err("  Source ............ " + ROOT);
        err("  Destination ....... " + destRoot);
        err("  Blueprint ......... " + bp.id() + " (" + bp.packages().size() + " packages, "
                + bp.archPaths().size() + " architecture paths)");
        err("  Files ............. " + out.size());
        for (String n : notes) err("  " + n);
        if (dry) {
            err("");
            out.keySet().forEach(p -> err("    " + p));
            err("");
            err("⚪ --dry-run: nothing was written.");
        } else {
            for (Map.Entry<String, String> e : out.entrySet()) {
                Path target = destRoot.resolve(e.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, e.getValue(), StandardCharsets.UTF_8);
            }
            for (String d : asStrList(exp.get("ensure_dirs"))) Files.createDirectories(destRoot.resolve(d));
            appendGitignore(destRoot, asStrList(exp.get("gitignore_lines")));
            err("");
            err("✅ Written. Review with `git -C " + destRoot + " diff`.");
        }
        if (!residue.isEmpty()) {
            err("");
            err("⚠️  " + residue.size() + " dead citation(s) survived the transform:");
            residue.forEach(r -> err("    " + r));
            err("   Neither decisions/ nor blueprints/ exists inside a project — fix the");
            err("   sentence at the source, or add its shape to body_transforms.");
        }
    }

    static void exportUsage() {
        err("usage: java .claude/hooks/ArchHook.java export <dest> --blueprint <id>"
                + " [--ref <label>] [--dry-run]");
        err("  <dest>        the target project's root (never this repository)");
        err("  --blueprint   id of a blueprint under .claude/blueprints/");
        err("  --ref         label recorded in the provenance stamp (default: working-tree)");
    }

    /** Reads `packages.map`, `architecture_paths` and the naming-convention comment block. */
    static Blueprint loadBlueprint(Map<String, Object> exp, String id) throws IOException {
        String tpl = asStr(get(exp, "blueprint", "path"));
        if (tpl == null) tpl = ".claude/blueprints/{id}/{id}.yaml";
        Path file = ROOT.resolve(tpl.replace("{id}", id));
        if (!Files.isRegularFile(file)) {
            err("❌ Blueprint `" + id + "` not found at " + relative(file));
            err("   Available: " + availableBlueprints());
            System.exit(2);
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> mapPath = asStrList(get(exp, "blueprint", "packages_map_path"));
        Map<String, String> pkgs = yamlNestedMap(lines,
                mapPath.isEmpty() ? List.of("packages", "map") : mapPath);
        String apKey = asStr(get(exp, "blueprint", "architecture_paths_key"));
        List<String> arch = yamlList(lines, apKey == null ? "architecture_paths" : apKey);
        String vocKey = asStr(get(exp, "blueprint", "vocabulary_anchor_key"));
        List<String> voc = yamlCommentBlockAbove(lines, vocKey == null ? "dependency_rules" : vocKey);

        if (pkgs.isEmpty() || arch.isEmpty()) {
            err("❌ Blueprint `" + id + "` has no packages.map or no architecture_paths.");
            err("   Both are required by .claude/blueprints/_schema.md; without them every");
            err("   rule with a territory would be copied with a dead glob.");
            System.exit(2);
        }
        return new Blueprint(id, pkgs, arch, voc);
    }

    static String availableBlueprints() throws IOException {
        Path dir = ROOT.resolve(".claude/blueprints");
        if (!Files.isDirectory(dir)) return "(none)";
        try (Stream<Path> walk = Files.list(dir)) {
            return walk.filter(Files::isDirectory)
                    .filter(p -> Files.isRegularFile(p.resolve(p.getFileName() + ".yaml")))
                    .map(p -> p.getFileName().toString()).sorted()
                    .collect(Collectors.joining(", "));
        }
    }

    // ── export · the three sets ──────────────────────────────────────────────

    static void exportRules(Map<String, Object> exp, Blueprint bp, Map<String, String> out)
            throws IOException {
        Map<String, Object> block = asMap(exp.get("rules"));
        if (block == null) return;
        String from = asStr(block.get("from")), to = asStr(block.get("to"));
        List<String> exclude = asStrList(block.get("exclude"));
        Path dir = ROOT.resolve(from);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.list(dir)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".md")).sorted()
                    .collect(Collectors.toList())) {
                String name = f.getFileName().toString();
                if (exclude.contains(name)) continue;
                String body = Files.readString(f, StandardCharsets.UTF_8);
                body = derivePaths(exp, bp, name, body);
                body = transform(exp, bp, to + "/" + name, body);
                out.put(to + "/" + name, body);
            }
        }
    }

    /**
     * Writes the active blueprint into the project. The catalog stays here — a project
     * chooses an architecture once — but the yaml it chose has to travel: the stamp
     * records the id, and an update resolves that id against the source, which for a
     * blueprint written during adoption never had it. Its `references/` citations are
     * dropped on the way: they point at files that do not travel.
     */
    static void exportBlueprint(Map<String, Object> exp, Blueprint bp, Map<String, String> out)
            throws IOException {
        String tpl = asStr(exp.get("blueprint_copy"));
        if (tpl == null || bp == null) return;
        String srcTpl = asStr(get(exp, "blueprint", "path"));
        Path src = ROOT.resolve((srcTpl == null ? ".claude/blueprints/{id}/{id}.yaml" : srcTpl)
                .replace("{id}", bp.id()));
        if (!Files.isRegularFile(src)) return;
        String body = Files.readString(src, StandardCharsets.UTF_8)
                .replaceAll("(?m)^#[ \\t]*@?\\.claude/blueprints/[^\\n]*\\n", "");
        out.put(tpl.replace("{id}", bp.id()), body);
    }

    /** Copies `keep` entries of each included skill, or the single file of each agent. */
    static void exportTree(Map<String, Object> exp, String key, Map<String, String> out)
            throws IOException {
        Map<String, Object> block = asMap(exp.get(key));
        if (block == null) return;
        String from = asStr(block.get("from")), to = asStr(block.get("to"));
        List<String> keep = asStrList(block.get("keep"));
        for (String name : asStrList(block.get("include"))) {
            Path base = ROOT.resolve(from).resolve(name);
            if (keep.isEmpty()) {                                  // agents: one flat file
                Path f = ROOT.resolve(from).resolve(name + ".md");
                if (Files.isRegularFile(f)) {
                    String rel = to + "/" + name + ".md";
                    out.put(rel, transform(exp, null, rel, Files.readString(f, StandardCharsets.UTF_8)));
                }
                continue;
            }
            for (String entry : keep) {
                Path p = base.resolve(entry);
                if (Files.isRegularFile(p)) {
                    String rel = to + "/" + name + "/" + entry;
                    out.put(rel, transform(exp, null, rel, Files.readString(p, StandardCharsets.UTF_8)));
                } else if (Files.isDirectory(p)) {
                    try (Stream<Path> walk = Files.walk(p)) {
                        for (Path f : walk.filter(Files::isRegularFile).sorted()
                                .collect(Collectors.toList())) {
                            String rel = to + "/" + name + "/" + base.relativize(f).toString()
                                    .replace(File.separatorChar, '/');
                            out.put(rel, transform(exp, null, rel,
                                    Files.readString(f, StandardCharsets.UTF_8)));
                        }
                    }
                }
            }
        }
    }

    static void exportFiles(Map<String, Object> exp, Blueprint bp, Map<String, String> out,
                            List<String> notes) throws IOException {
        for (String group : List.of("copy", "overwrite", "optional_copy")) {
            for (Object e : asList(exp.get(group))) {
                String from = asStr(get(e, "from")), to = asStr(get(e, "to"));
                if (from == null || to == null) continue;
                Path p = ROOT.resolve(from);
                if (!Files.isRegularFile(p)) {
                    if (!group.equals("optional_copy")) {
                        err("❌ export." + group + " names `" + from + "` — no such file.");
                        System.exit(2);
                    }
                    continue;
                }
                out.put(to, transform(exp, bp, from, Files.readString(p, StandardCharsets.UTF_8)));
                if (group.equals("overwrite")) {
                    notes.add("Overwrites ........ " + to + " (its previous content is in the diff)");
                }
            }
        }
    }

    // ── export · transforms ──────────────────────────────────────────────────

    /**
     * Cuts what has no counterpart inside a project and applies the rewrites the manifest
     * names for this file. A sentence whose only job is to cite a decision record goes
     * whole; a citation inside a sentence that says something else loses only its clause.
     */
    static String transform(Map<String, Object> exp, Blueprint bp, String sourceRel, String body) {
        Map<String, Object> bt = asMap(exp.get("body_transforms"));
        if (bt == null) return body;

        String cite = asStr(bt.get("citation_pattern"));
        if (cite != null) {
            // Shapes where the citation IS the content: a parenthetical, or a list item
            // whose whole line describes the record. Cutting only the clause there would
            // leave `()` or a bullet with a dangling dash.
            for (String shape : asStrList(bt.get("cut_shapes"))) {
                body = body.replaceAll(shape.replace("{cite}", cite), "");
            }
            // A lead-in is matched across line breaks: the sentence it opens is wrapped
            // at 90 columns like every other, and "Full\nrecord in `@…`." is the common
            // case, not the exception.
            // A lead-in may open a list of records — "Design: X, Y and Z" — and the
            // sentence says the same thing about all of them, so the whole list goes with
            // it. Without the tail, the first citation is cut and the rest survive as an
            // orphan ", and Z" the next step then turns into prose.
            for (String lead : asStrList(bt.get("sentence_lead_ins"))) {
                String words = Arrays.stream(lead.trim().split("\\s+")).map(Pattern::quote)
                        .collect(Collectors.joining("\\s+"));
                body = body.replaceAll("(?s)" + words + "[^.]{0,200}?" + cite
                        + "(?:(?:,|,? and|,? e)[ \n]+" + cite + ")*\\.?[ ]?", "");
            }
            // What survives both shapes above is a citation inside a sentence that says
            // something else. Deleting it there leaves the sentence mangled — `- **** —`,
            // `Inherits D15 —.` — which no residue marker looking for a path can see, so
            // six of them shipped (lessons-learned-012 § 3). The path is replaced by prose
            // instead: the reader inside a project cannot follow it either way, but the
            // sentence still says what it was written to say.
            String replacement = asStr(bt.get("citation_replacement"));
            body = body.replaceAll(cite, Matcher.quoteReplacement(
                    replacement == null ? "" : replacement));
            // Three records cited in a row become the same phrase three times. The list
            // collapses to one: "Design: X, Y and Z" said the same thing about all three.
            if (replacement != null && !replacement.isEmpty()) {
                String r = Pattern.quote(replacement);
                body = body.replaceAll("(?s)" + r + "(?:(?:,|,? and|,? e)[ \n]+" + r + ")+",
                        Matcher.quoteReplacement(replacement));
            }
        }
        for (Object r : asList(bt.get("replace"))) {
            String find = asStr(get(r, "find")), with = asStr(get(r, "with"));
            if (find != null && with != null) body = body.replace(find, with);
        }
        for (Object r : asList(bt.get("rewrite"))) {
            if (!sourceRel.equals(asStr(get(r, "file")))) continue;
            body = switch (String.valueOf(asStr(get(r, "op")))) {
                case "blueprint_vocabulary" -> replaceBetween(body, asStr(get(r, "anchor_start")),
                        asStr(get(r, "anchor_end")), vocabularyMarkdown(bp));
                case "replace_paragraph" -> replaceParagraph(body, asStr(get(r, "anchor")),
                        String.valueOf(asStr(get(r, "replacement")))
                                .replace("{blueprint}", bp == null ? "" : bp.id()));
                case "drop_block" -> dropJsonBlock(body, asStr(get(r, "block")));
                default -> body;
            };
        }
        return body.replaceAll("(?m)[ \t]+$", "").replaceAll("\n{3,}", "\n\n");
    }

    /** Rewrites a rule's `paths:` from the blueprint, never from the original file. */
    static String derivePaths(Map<String, Object> exp, Blueprint bp, String rule, String body) {
        Map<String, Object> spec = asMap(get(exp, "derived_paths", rule));
        if (spec == null) return body;

        List<String> globs = new ArrayList<>();
        if (asStr(spec.get("from_blueprint")) != null) {
            globs.addAll(bp.archPaths());
        } else {
            String shape = asStr(spec.get("glob"));
            for (String suffix : asStrList(spec.get("suffix"))) {
                for (Map.Entry<String, String> e : bp.packages().entrySet()) {
                    if (e.getKey().endsWith(suffix)) globs.add(shape.replace("{}", e.getValue().replace('.', '/')));
                }
            }
            for (String key : asStrList(spec.get("key"))) {
                String v = bp.packages().get(key);
                if (v != null) { globs.add(shape.replace("{}", v.replace('.', '/'))); break; }
            }
            // An architecture that doesn't name the layer as a package — vertical-slice
            // puts REST and persistence inside each slice — still has a territory, and it
            // is one of its own `architecture_paths`. Selected by token, never by union
            // of every path: a rule that loads on domain edits is noise, not coverage.
            if (globs.isEmpty()) {
                for (String token : asStrList(spec.get("fallback_architecture_paths_containing"))) {
                    for (String p : bp.archPaths()) if (p.contains(token)) globs.add(p);
                }
            }
            globs.addAll(asStrList(spec.get("extra")));
        }
        if (globs.isEmpty()) {
            if (!Boolean.TRUE.equals(spec.get("optional"))) {
                err("❌ Rule `" + rule + "` derives no glob from blueprint `" + bp.id() + "`.");
                err("   A rule copied with a dead glob never enters context, in silence.");
                err("   Mark it `optional` in export.derived_paths, or fix packages.map.");
                System.exit(2);
            }
            return stripPathsBlock(body);            // optional: travels without `paths`
        }
        StringBuilder b = new StringBuilder("paths:\n");
        for (String g : globs) b.append("  - \"").append(g).append("\"\n");
        return replacePathsBlock(body, b.toString());
    }

    /** Replaces (or inserts) the `paths:` block inside the leading frontmatter. */
    static String replacePathsBlock(String body, String block) {
        String stripped = stripPathsBlock(body);
        int open = stripped.indexOf("---\n");
        if (open != 0) return stripped;                        // no frontmatter: nothing to do
        return "---\n" + block + stripped.substring(4);
    }

    static String stripPathsBlock(String body) {
        return body.replaceAll("(?m)^paths:\\n(?:[ \\t]+-[^\\n]*\\n)+", "");
    }

    static String replaceBetween(String body, String start, String end, String replacement) {
        if (start == null || end == null) return body;
        int a = body.indexOf(start);
        if (a < 0) return body;
        int b = body.indexOf(end, a);
        if (b < 0) return body;
        return body.substring(0, a) + replacement + body.substring(b + end.length());
    }

    static String replaceParagraph(String body, String anchor, String replacement) {
        if (anchor == null) return body;
        int a = body.indexOf(anchor);
        if (a < 0) return body;
        int start = body.lastIndexOf("\n\n", a);
        start = start < 0 ? 0 : start + 2;
        int end = body.indexOf("\n\n", a);
        if (end < 0) end = body.length();
        return body.substring(0, start) + replacement + body.substring(end);
    }

    /** Removes a top-level block of a two-space-indented JSON object, braces balanced. */
    static String dropJsonBlock(String body, String key) {
        if (key == null) return body;
        String needle = "\n  \"" + key + "\": {";
        int a = body.indexOf(needle);
        if (a < 0) return body;
        int depth = 0, i = a + needle.length() - 1;
        for (; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) break;
        }
        int end = i + 1;
        if (end < body.length() && body.charAt(end) == ',') end++;
        while (end < body.length() && (body.charAt(end) == '\n' || body.charAt(end) == ' ')) end++;
        return body.substring(0, a + 1) + body.substring(end);
    }

    /** The blueprint's naming-convention comment block, as a markdown list. */
    static String vocabularyMarkdown(Blueprint bp) {
        if (bp == null || bp.vocabulary().isEmpty()) return "";
        List<String> prose = new ArrayList<>(), entries = new ArrayList<>();
        int base = Integer.MAX_VALUE;
        List<String> body = new ArrayList<>();
        for (String raw : bp.vocabulary()) {
            String l = raw.replaceFirst("^#\\s?", "");
            if (l.trim().toLowerCase(Locale.ROOT).startsWith("reference")) break;
            body.add(l);
            int ind = l.length() - l.stripLeading().length();
            if (!l.isBlank() && ind > 0) base = Math.min(base, ind);
        }
        for (String l : body) {
            int ind = l.length() - l.stripLeading().length();
            if (l.isBlank()) continue;
            if (ind == 0) prose.add(l.trim());
            else if (ind == base) entries.add(l.trim());
            else if (!entries.isEmpty()) entries.set(entries.size() - 1,
                    entries.get(entries.size() - 1) + " " + l.trim());
        }
        StringBuilder b = new StringBuilder();
        if (!prose.isEmpty()) b.append(String.join(" ", prose)).append("\n\n");
        for (String e : entries) {
            // The comment aligns its columns with runs of spaces; markdown doesn't, and
            // the role before the first colon is what a reader scans for.
            String flat = e.replaceAll(" {2,}", " ");
            int colon = flat.indexOf(':');
            b.append("- ").append(colon > 0
                    ? "**" + flat.substring(0, colon) + ":**" + flat.substring(colon + 1)
                    : flat).append('\n');
        }
        return b.toString().stripTrailing();
    }

    // ── export · the provenance stamp and the YAML it reads ──────────────────

    /**
     * The provenance stamp. `files` is what makes it answer a question offline: an
     * update overwrites, so before running one the person needs to know which of these
     * files they have edited since the last export — `doctor` recomputes the digests and
     * names them. Everything else in the stamp answers "from where, and how old".
     */
    static String stamp(Blueprint bp, String ref, Map<String, String> files) throws Exception {
        String commit = firstLine(run("git", "-C", ROOT.toString(), "rev-parse", "HEAD"));
        String origin = firstLine(run("git", "-C", ROOT.toString(), "config", "--get", "remote.origin.url"));
        String manifest = readOrNull(ROOT.resolve(SCHEMA_FILE));
        StringBuilder b = new StringBuilder("{\n"
                + "  \"source\": \"" + jsonEscape(origin == null ? ROOT.toString() : origin) + "\",\n"
                + "  \"ref\": \"" + jsonEscape(ref) + "\",\n"
                + "  \"commit\": \"" + jsonEscape(commit == null ? "unknown" : commit) + "\",\n"
                + "  \"exported_at\": \"" + Instant.now() + "\",\n"
                + "  \"blueprint\": \"" + jsonEscape(bp.id()) + "\",\n"
                + "  \"manifest_digest\": \"" + (manifest == null ? "unknown" : sha256(manifest)) + "\",\n"
                + "  \"files\": {\n");
        int i = 0;
        for (Map.Entry<String, String> e : files.entrySet()) {
            b.append("    \"").append(jsonEscape(e.getKey())).append("\": \"")
             .append(sha256(e.getValue())).append(++i < files.size() ? "\",\n" : "\"\n");
        }
        return b.append("  }\n}\n").toString();
    }

    static String firstLine(Proc p) {
        return p.exit() == 0 && !p.out().isEmpty() ? p.out().get(0).trim() : null;
    }

    static void appendGitignore(Path destRoot, List<String> lines) throws IOException {
        if (lines.isEmpty()) return;
        Path gi = destRoot.resolve(".gitignore");
        String cur = Files.isRegularFile(gi) ? Files.readString(gi, StandardCharsets.UTF_8) : "";
        StringBuilder add = new StringBuilder();
        for (String l : lines) if (!cur.contains(l)) add.append(l).append('\n');
        if (add.length() == 0) return;
        Files.writeString(gi, cur.isEmpty() || cur.endsWith("\n") ? cur + add : cur + "\n" + add,
                StandardCharsets.UTF_8);
    }

    static int indentOf(String l) { return l.length() - l.stripLeading().length(); }

    static int indexOfTopKey(List<String> lines, String key) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).matches("^" + Pattern.quote(key) + ":.*")) return i;
        return -1;
    }

    static String unquote(String v) {
        v = v.trim();
        if (v.startsWith("\"") && v.indexOf('"', 1) > 0) return v.substring(1, v.indexOf('"', 1));
        if (v.startsWith("'") && v.indexOf('\'', 1) > 0) return v.substring(1, v.indexOf('\'', 1));
        int hash = v.indexOf(" #");
        return (hash > 0 ? v.substring(0, hash) : v).trim();
    }

    /** The list of scalars under a top-level key. */
    static List<String> yamlList(List<String> lines, String key) {
        List<String> out = new ArrayList<>();
        int i = indexOfTopKey(lines, key);
        if (i < 0) return out;
        for (int j = i + 1; j < lines.size(); j++) {
            String l = lines.get(j);
            if (l.trim().startsWith("#")) continue;
            if (l.isBlank()) { if (!out.isEmpty()) break; continue; }
            Matcher m = Pattern.compile("^\\s+-\\s*(.+?)\\s*$").matcher(l);
            if (!m.matches()) break;
            out.add(unquote(m.group(1)));
        }
        return out;
    }

    /** The scalar entries of a nested map, e.g. `packages` → `map`. */
    static Map<String, String> yamlNestedMap(List<String> lines, List<String> path) {
        int i = indexOfTopKey(lines, path.get(0));
        if (i < 0) return Map.of();
        int indent = 0;
        i++;
        for (int d = 1; d < path.size(); d++) {
            int found = -1;
            for (int j = i; j < lines.size(); j++) {
                String l = lines.get(j);
                if (l.isBlank() || l.trim().startsWith("#")) continue;
                if (indentOf(l) <= indent) break;
                if (l.trim().startsWith(path.get(d) + ":")) { found = j; indent = indentOf(l); break; }
            }
            if (found < 0) return Map.of();
            i = found + 1;
        }
        Map<String, String> map = new LinkedHashMap<>();
        Pattern entry = Pattern.compile("^\\s*([A-Za-z0-9_.\\-]+)\\s*:\\s*(.+?)\\s*$");
        for (int j = i; j < lines.size(); j++) {
            String l = lines.get(j);
            if (l.isBlank() || l.trim().startsWith("#")) continue;
            if (indentOf(l) <= indent) break;
            Matcher m = entry.matcher(l);
            if (m.matches()) map.put(m.group(1), unquote(m.group(2)));
        }
        return map;
    }

    /** The contiguous comment block sitting immediately above a top-level key. */
    static List<String> yamlCommentBlockAbove(List<String> lines, String key) {
        int i = indexOfTopKey(lines, key);
        if (i < 0) return List.of();
        int j = i - 1;
        while (j >= 0 && lines.get(j).isBlank()) j--;
        List<String> block = new ArrayList<>();
        while (j >= 0 && lines.get(j).startsWith("#")) block.add(lines.get(j--));
        Collections.reverse(block);
        return block;
    }

    // ── schema ───────────────────────────────────────────────────────────────
    //
    // Guards the frontmatter fields of Claude Code's extension files.
    // The runtime silently ignores an unknown field and `claude plugin validate`
    // lets it through: without this mode, a wrong field looks like behavior and is
    // just decoration. The field list does NOT live here — it lives in
    // .claude/schemas/extensions.json, so that adding a field doesn't require
    // touching Java. Rationale and design:
    // .claude/decisions/0001-schema-frontmatter-extensions.md

    static final String SCHEMA_FILE = ".claude/schemas/extensions.json";

    static void schema(String stdin) throws Exception {
        // If the Stop hook already blocked before, don't block again: avoids cycles.
        if (Pattern.compile("\"stop_hook_active\"\\s*:\\s*true").matcher(stdin).find()) return;

        Map<String, Object> sch = asMap(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE))));
        if (sch == null) {
            err("⚠️  " + SCHEMA_FILE + " missing or invalid.");
            err("   Frontmatter validation OFF — nothing was checked.");
            return;
        }

        Map<String, Object> in = asMap(get(Json.parse(stdin), "tool_input"));
        String file = in != null ? asStr(in.get("file_path")) : filePath(stdin);

        List<String> errors = new ArrayList<>();
        if (file == null) {
            sweep(sch, errors);                       // Stop, or manual invocation
        } else {
            String rel = relative(Paths.get(file));
            // Write brings the whole file in tool_input.content and is validated before
            // writing. Edit brings old_string/new_string — there only disk works.
            String content = in != null ? asStr(in.get("content")) : null;
            if (content == null) content = readOrNull(Paths.get(file));
            if (content == null) return;              // file deleted or unreadable
            checkOne(sch, rel, content, errors);
        }

        if (file == null) {
            checkExecutorAgents(sch, errors);
            checkSkillClasses(sch, errors);
            checkExportManifest(sch, errors);
            checkSourceBlock(sch, errors);
        }

        if (!errors.isEmpty()) {
            err("❌ Invalid extension file — " + errors.size()
                    + (errors.size() == 1 ? " problem" : " problems"));
            errors.forEach(ArchHook::err);
            err("");
            err("Valid fields by type: " + SCHEMA_FILE);
            err("What each field is for: .claude/skills/claude-code-architect-designer"
                    + "/references/frontmatter-fields.md");
            System.exit(2);
        }
    }

    /**
     * Cross-checks `guard.executor_agents` against every `.claude/agents/*.md` file's own
     * "**Executor:** yes" marker — both directions. A name in the list with no matching
     * agent file (or whose file never claims the role) means the guard trusts an
     * `agent_type` that can never actually arrive; an agent file that claims the role but
     * is missing from the list means the guard blocks its writes during a design phase.
     * lessons-learned-006 § 2: commons-logging-installer shipped without either side
     * agreeing for one full session before the gap was noticed by hand.
     */
    static void checkExecutorAgents(Map<String, Object> sch, List<String> errors) throws IOException {
        List<String> listed = asStrList(get(sch, "guard", "executor_agents"));
        if (listed.isEmpty()) return;   // no `guard` block: nothing to cross-check

        Path agentsDir = ROOT.resolve(".claude/agents");
        Set<String> claimed = new HashSet<>();
        if (Files.isDirectory(agentsDir)) {
            try (Stream<Path> walk = Files.list(agentsDir)) {
                for (Path f : walk.filter(p -> p.toString().endsWith(".md")).collect(Collectors.toList())) {
                    String content = readOrNull(f);
                    if (content != null && content.contains("**Executor:** yes")) {
                        String name = f.getFileName().toString().replaceFirst("\\.md$", "");
                        claimed.add(name);
                    }
                }
            }
        }

        // `project-initializer` executes `/init-project` and, like the creation skills, does
        // not travel into a generated project — so "listed but absent" is an error only in the
        // source repository. See isSourceRepo.
        boolean here = isSourceRepo(sch);
        for (String name : listed) {
            if (!Files.isRegularFile(agentsDir.resolve(name + ".md"))) {
                if (here) {
                    errors.add("  guard.executor_agents lists `" + name
                            + "` — no `.claude/agents/" + name + ".md` file exists");
                }
            } else if (!claimed.contains(name)) {
                errors.add("  guard.executor_agents lists `" + name
                        + "` — its agent file has no `**Executor:** yes` marker in ## Contract");
            }
        }
        for (String name : claimed) {
            if (!listed.contains(name)) {
                errors.add("  .claude/agents/" + name
                        + ".md claims `**Executor:** yes` — missing from guard.executor_agents");
            }
        }
    }

    // ── skill classes ────────────────────────────────────────────────────────
    //
    // The class of a skill is data (`skill_classes` in extensions.json) and it decides two
    // separate things: the sections its body must carry, checked here, and the paths it may
    // write, enforced by the `guard` mode. Both failures used to be silent — nine skills
    // said `## Contract`, project-bootstrap said `## Skill contract`, arch-doctor and
    // init-project said nothing, and `claude plugin validate` printed `✔ Validation passed`
    // over all of it; the territory half let a design run write a service block into
    // docker-compose.yml while every skill involved forbade it in prose.
    //
    // Form 7c of `claude-code-architect-designer`, motivated by axis 7 (prose had already
    // failed) and axis 16 (both checks land on modes that already walk these files). The
    // closest rejected form was a frontmatter field per skill — silently ignored by the
    // runtime, so it would have been decoration.
    // Design: .claude/decisions/0058-skill-classes-territory-schema.md

    /**
     * Whether this tree is the repository `extensions.json`'s lists were written for. Two
     * conditions, and both are needed: the `export` block is present at all — the exported
     * copy drops it, which is the cheapest tell — and `export.source_marker` still resolves
     * to a directory here, which is what separates the source from a tree that merely copied
     * the manifest (the CI sandbox). A list naming a skill or an agent that deliberately does
     * not travel (`export.skills.exclude`, `export.agents.exclude`) is an error only where it
     * should exist; reporting it inside a generated project says nothing but "this is not that
     * repository".
     */
    static boolean isSourceRepo(Map<String, Object> sch) {
        Map<String, Object> exp = asMap(sch.get("export"));
        if (exp == null) return false;
        String marker = asStr(exp.get("source_marker"));
        return marker == null || Files.isDirectory(ROOT.resolve(marker));
    }

    /** The class that lists this skill in `skill_classes`, or null when none does. */
    static String skillClassOf(Map<String, Object> sch, String skill) {
        Map<String, Object> classes = asMap(get(sch, "skill_classes", "classes"));
        if (classes == null || skill == null) return null;
        for (Map.Entry<String, Object> e : classes.entrySet()) {
            Map<String, Object> c = asMap(e.getValue());
            if (c != null && asStrList(c.get("skills")).contains(skill)) return e.getKey();
        }
        return null;
    }

    /** The skill's own `overrides` territory when it has one, else its class default. */
    static List<String> writeAllowOf(Map<String, Object> sch, String skill) {
        String cls = skillClassOf(sch, skill);
        if (cls == null) return List.of();
        Map<String, Object> c = asMap(get(sch, "skill_classes", "classes", cls));
        Map<String, Object> ov = asMap(get(c, "overrides", skill));
        if (ov != null && ov.containsKey("write_allow")) return asStrList(ov.get("write_allow"));
        return asStrList(c == null ? null : c.get("write_allow"));
    }

    /** `<name>` of `.claude/skills/<name>/SKILL.md`. */
    static String skillNameOf(String rel) {
        Matcher m = Pattern.compile("skills/([^/]+)/SKILL\\.md$").matcher(rel);
        return m.find() ? m.group(1) : null;
    }

    /**
     * One skill body against its class: the `**Class:** <c>` line agrees with the data, and
     * every required section is present. A section is matched as a PREFIX of an H2 line, so
     * `## Procedure — design mode` satisfies `## Procedure`; order is not checked.
     */
    static void checkSkillBody(Map<String, Object> sch, String rel, String content,
                               List<String> errors) {
        Map<String, Object> sc = asMap(sch.get("skill_classes"));
        if (sc == null) return;
        String skill = skillNameOf(rel);
        if (skill == null) return;

        Map<String, Object> classes = asMap(sc.get("classes"));
        if (classes == null) return;
        String cls = skillClassOf(sch, skill);
        if (cls == null) {
            errors.add("  " + rel + " — `" + skill + "` is in no skill_classes class."
                    + " Add it to one of " + classes.keySet() + " in " + SCHEMA_FILE);
            return;
        }

        String marker = orEmpty(asStr(sc.get("class_marker")));
        if (!marker.isEmpty()) {
            // `- ` prefix allowed: audit-usage writes its whole contract as a bullet list.
            Matcher m = Pattern.compile("(?m)^[-*]?[ \\t]*" + Pattern.quote(marker)
                            + "\\s*`?([a-z][a-z0-9_-]*)`?")
                    .matcher(content);
            if (!m.find()) {
                errors.add("  " + rel + " — no `" + marker + " " + cls + "` line in the body."
                        + " The class is what the guard enforces; state it in ## Contract");
            } else if (!cls.equals(m.group(1))) {
                errors.add("  " + rel + " — body says `" + marker + " " + m.group(1)
                        + "`, " + SCHEMA_FILE + " lists it under `" + cls + "`");
            }
        }

        List<String> required = new ArrayList<>(asStrList(sc.get("universal_sections")));
        required.addAll(asStrList(get(sch, "skill_classes", "classes", cls, "required_sections")));
        List<String> headings = content.lines().filter(l -> l.startsWith("## "))
                .map(String::strip).collect(Collectors.toList());
        for (String req : required) {
            if (headings.stream().noneMatch(h -> h.startsWith(req))) {
                errors.add("  " + rel + " — class `" + cls + "` requires the section `"
                        + req + "`");
            }
        }

        String why = asStr(sc.get("why_section"));
        if (why != null && !Pattern.compile(why).matcher(content).find()) {
            errors.add("  " + rel + " — no `## Why …` section. Three sentences: the form,"
                    + " the interview axis, the closest rejected form");
        }
    }

    /**
     * The `skill_classes` block against the skills on disk. Coverage is total by design:
     * a skill no class lists has no territory, so the guard would let it write anything.
     * Runs only at sweep time (Stop, or a manual `schema`) — it lists a directory.
     */
    static void checkSkillClasses(Map<String, Object> sch, List<String> errors) throws IOException {
        Map<String, Object> classes = asMap(get(sch, "skill_classes", "classes"));
        if (classes == null) return;   // no block: nothing to cross-check

        // The block travels whole into every generated project, and three creation skills
        // deliberately do not (`export.skills.exclude`). So "listed but absent" is only an
        // error in the repository the block was written for. Coverage in the other direction
        // (a skill on disk that no class lists) is checked everywhere: a project that adds a
        // skill of its own needs a class for it just as much.
        boolean here = isSourceRepo(sch);

        Map<String, String> owner = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : classes.entrySet()) {
            Map<String, Object> c = asMap(e.getValue());
            if (c == null) continue;
            if (!c.containsKey("write_allow")) {
                errors.add("  skill_classes." + e.getKey() + " has no `write_allow` key."
                        + " An empty list is a decision; a missing key is an omission");
            }
            for (String s : asStrList(c.get("skills"))) {
                String previous = owner.put(s, e.getKey());
                if (previous != null) {
                    errors.add("  skill_classes lists `" + s + "` in both `" + previous
                            + "` and `" + e.getKey() + "` — one class per skill");
                }
                if (here && !Files.isRegularFile(ROOT.resolve(".claude/skills/" + s + "/SKILL.md"))) {
                    errors.add("  skill_classes." + e.getKey() + " lists `" + s
                            + "` — no `.claude/skills/" + s + "/SKILL.md` file exists");
                }
            }
            Map<String, Object> ov = asMap(c.get("overrides"));
            if (ov != null) {
                for (String s : ov.keySet()) {
                    if (!asStrList(c.get("skills")).contains(s)) {
                        errors.add("  skill_classes." + e.getKey() + ".overrides names `" + s
                                + "` — not a skill of that class");
                    }
                }
            }
        }

        Path skillsDir = ROOT.resolve(".claude/skills");
        if (!Files.isDirectory(skillsDir)) return;
        try (Stream<Path> walk = Files.list(skillsDir)) {
            for (Path d : walk.filter(Files::isDirectory).sorted().collect(Collectors.toList())) {
                String name = d.getFileName().toString();
                if (!Files.isRegularFile(d.resolve("SKILL.md"))) continue;
                if (!owner.containsKey(name)) {
                    errors.add("  `.claude/skills/" + name + "` is in no skill_classes class"
                            + " — add it to one in " + SCHEMA_FILE);
                }
            }
        }
    }

    /**
     * Cross-checks the `export` manifest against what is actually on disk. The mode that
     * reads the manifest writes a project's whole `.claude/`, so an entry naming a file
     * this repo no longer has produces a dead citation inside every project exported
     * afterwards, and a skill or agent listed in neither `include` nor `exclude` simply
     * never travels — both silent, which is why they are checked here and not left to
     * review. Runs only at sweep time (Stop, or a manual `schema`): it walks three
     * directories, and paying that on every Edit would buy nothing.
     *
     * <p>Form 7c of `claude-code-architect-designer`, motivated by axis 7 — the export
     * runs unattended on other people's machines, so its input cannot be trusted to a
     * reviewer's memory. The closest rejected form was a line in a rule: prose that had
     * already gone stale twice in `project-bootstrap`'s copy tables, which this manifest
     * replaces. Design: .claude/decisions/0054-deterministic-export-provenance-plugin.md
     */
    static void checkExportManifest(Map<String, Object> sch, List<String> errors) throws IOException {
        Map<String, Object> exp = asMap(sch.get("export"));
        if (exp == null) return;                      // no `export` block: nothing to check

        // The manifest describes the repository it lives in. A tree that merely copied
        // extensions.json — the CI injection sandbox, or a project that kept the block —
        // has none of the files it names, and reporting all of them as missing says only
        // that this is not that repository. `source_marker` is the directory that answers
        // it: present here, travels nowhere.
        String marker = asStr(exp.get("source_marker"));
        if (marker != null && !Files.isDirectory(ROOT.resolve(marker))) return;

        for (String group : List.of("copy", "overwrite")) {
            for (Object e : asList(exp.get(group))) {
                String from = asStr(get(e, "from"));
                if (from == null) {
                    errors.add("  export." + group + " has an entry without `from`");
                } else if (!Files.isRegularFile(ROOT.resolve(from))) {
                    errors.add("  export." + group + " names `" + from + "` — no such file");
                }
            }
        }

        checkExportSet(exp, "skills", ".claude/skills", "/SKILL.md", errors);
        checkExportSet(exp, "agents", ".claude/agents", ".md", errors);

        String rulesDir = asStr(get(exp, "rules", "from"));
        if (rulesDir != null && !Files.isDirectory(ROOT.resolve(rulesDir))) {
            errors.add("  export.rules.from is `" + rulesDir + "` — no such directory");
        }
        for (String r : asStrList(get(exp, "rules", "exclude"))) {
            if (rulesDir != null && !Files.isRegularFile(ROOT.resolve(rulesDir).resolve(r))) {
                errors.add("  export.rules.exclude names `" + r + "` — no such rule");
            }
        }
        List<String> derived = new ArrayList<>();
        for (String r : asMapKeys(exp.get("derived_paths"))) {
            if (r.startsWith("$") || r.equals("derived_paths_exempt_globs")) continue;
            derived.add(r);
            if (rulesDir != null && !Files.isRegularFile(ROOT.resolve(rulesDir).resolve(r))) {
                errors.add("  export.derived_paths names `" + r + "` — no such rule");
            }
        }
        checkRuleTerritories(exp, rulesDir, derived, errors);
        for (Object e : asList(get(exp, "body_transforms", "rewrite"))) {
            String f = asStr(get(e, "file"));
            if (f != null && !Files.isRegularFile(ROOT.resolve(f))) {
                errors.add("  export.body_transforms.rewrite names `" + f + "` — no such file");
            }
        }

    }

    /**
     * The `source` block: where a project pulls this `.claude/` from. Checked apart from
     * the `export` manifest and not inside it, because the exported copy keeps this block
     * and drops that one — a project validates its own update path, which is the only
     * path it has. An indirection, never a credential: invariant 11, scanned with the
     * same prefix list the `mcp` block already owns.
     */
    static void checkSourceBlock(Map<String, Object> sch, List<String> errors) {
        Map<String, Object> src = asMap(sch.get("source"));
        if (src == null) return;
        String base = asStr(src.get("base_url"));
        if (base != null) {
            if (!base.startsWith("https://")) {
                errors.add("  source.base_url is not https — `" + base + "`");
            }
            if (!base.contains("{ref}")) {
                errors.add("  source.base_url has no `{ref}` placeholder"
                        + " — every fetch would pull the same content");
            }
            for (String p : asStrList(get(sch, "mcp", "secret_scan", "value_prefixes"))) {
                if (base.contains(p)) {
                    errors.add("  source.base_url carries a literal credential"
                            + " (`" + p + "…`) — use source.auth_env, invariant 11");
                }
            }
        }
        String git = asStr(src.get("git_url"));
        if (git != null && !git.startsWith("https://")) {
            errors.add("  source.git_url is not https — `" + git + "`");
        }
        String authEnv = asStr(src.get("auth_env"));
        if (authEnv != null && !authEnv.matches("[A-Z][A-Z0-9_]*")) {
            errors.add("  source.auth_env is `" + authEnv
                    + "` — it names an environment variable, never its value");
        }
    }

    /**
     * The other direction of `derived_paths`: a rule whose `paths` names a package has a
     * territory, and that territory changes with the architecture. Missing from the map,
     * it travels with the glob of whichever blueprint happened to be written into the
     * file, and in every other architecture it never enters context — gap 8 of
     * lessons-learned-001, which cost a norm that silently applied to one blueprint out
     * of three. The globs that name no package are data, in `derived_paths_exempt_globs`.
     */
    static void checkRuleTerritories(Map<String, Object> exp, String rulesDir,
                                     List<String> derived, List<String> errors) throws IOException {
        if (rulesDir == null) return;
        Path dir = ROOT.resolve(rulesDir);
        if (!Files.isDirectory(dir)) return;
        List<String> exempt = asStrList(get(exp, "derived_paths", "derived_paths_exempt_globs"));
        Pattern glob = Pattern.compile("(?m)^[ \t]+-[ \t]+\"([^\"]+)\"");

        try (Stream<Path> walk = Files.list(dir)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".md")).sorted()
                    .collect(Collectors.toList())) {
                String name = f.getFileName().toString();
                if (derived.contains(name)) continue;
                String body = readOrNull(f);
                if (body == null) continue;
                int at = body.indexOf("\npaths:");
                if (!body.startsWith("---\npaths:") && at < 0) continue;
                int from = body.startsWith("---\npaths:") ? 4 : at + 1;
                int to = body.indexOf("\nstatus:", from);
                Matcher m = glob.matcher(body.substring(from, to < 0 ? body.length() : to));
                while (m.find()) {
                    if (exempt.contains(m.group(1))) continue;
                    errors.add("  .claude/rules/" + name + " has the territory glob `" + m.group(1)
                            + "` and no entry in export.derived_paths — it would travel naming"
                            + " this repo's package, and never load where that package differs");
                    break;
                }
            }
        }
    }

    /**
     * One side of {@link #checkExportManifest}: every directory entry under {@code dir}
     * must appear in `include` or in `exclude`, and every listed name must resolve to a
     * real file. Coverage is the point — a new skill nobody added to either list is the
     * failure this catches, and it looks exactly like a working repository.
     */
    static void checkExportSet(Map<String, Object> exp, String key, String defaultDir,
                               String suffix, List<String> errors) throws IOException {
        Map<String, Object> block = asMap(exp.get(key));
        if (block == null) return;

        String dir = asStr(block.get("from")) != null ? asStr(block.get("from")) : defaultDir;
        Path base = ROOT.resolve(dir);
        if (!Files.isDirectory(base)) {
            errors.add("  export." + key + ".from is `" + dir + "` — no such directory");
            return;
        }

        List<String> include = asStrList(block.get("include"));
        List<String> exclude = asStrList(block.get("exclude"));
        for (String name : include) {
            if (!Files.isRegularFile(base.resolve(name + suffix))) {
                errors.add("  export." + key + ".include names `" + name
                        + "` — no `" + dir + "/" + name + suffix + "`");
            }
        }
        for (String name : exclude) {
            if (!Files.isRegularFile(base.resolve(name + suffix))) {
                errors.add("  export." + key + ".exclude names `" + name
                        + "` — no `" + dir + "/" + name + suffix + "`, stale entry");
            }
        }
        for (String name : include) {
            if (exclude.contains(name)) {
                errors.add("  export." + key + " lists `" + name + "` in include AND exclude");
            }
        }

        try (Stream<Path> walk = Files.list(base)) {
            List<String> onDisk = walk.map(p -> p.getFileName().toString())
                    .map(n -> n.endsWith(".md") ? n.replaceFirst("\\.md$", "") : n)
                    .sorted().collect(Collectors.toList());
            for (String name : onDisk) {
                if (!Files.isRegularFile(base.resolve(name + suffix))) continue;
                if (!include.contains(name) && !exclude.contains(name)) {
                    errors.add("  `" + dir + "/" + name + "` is in neither export." + key
                            + ".include nor .exclude — it would silently never travel");
                }
            }
        }
    }

    static List<String> asMapKeys(Object o) {
        Map<String, Object> m = asMap(o);
        return m == null ? List.of() : new ArrayList<>(m.keySet());
    }

    /** Sweeps every file that some schema `match` captures. */
    static void sweep(Map<String, Object> sch, List<String> errors) throws IOException {
        List<String> globs = new ArrayList<>();
        Map<String, Object> types = asMap(sch.get("types"));
        if (types != null) {
            for (Object t : types.values()) {
                String g = asStr(asMap(t) == null ? null : asMap(t).get("match"));
                if (g != null) globs.add(g);
            }
        }
        globs.addAll(asStrList(get(sch, "settings", "match")));
        globs.addAll(asStrList(get(sch, "mcp", "match")));

        for (String g : globs) {
            Path base = ROOT.resolve(g.substring(0, Math.max(0, g.indexOf('*'))))
                    .normalize();
            if (!Files.isDirectory(base)) {
                if (Files.isRegularFile(base)) {       // glob without `*`: settings.json
                    checkOne(sch, relative(base), readOrNull(base), errors);
                }
                continue;
            }
            Pattern re = glob(g);
            try (Stream<Path> walk = Files.walk(base)) {
                List<Path> hits = walk.filter(Files::isRegularFile)
                        .filter(f -> re.matcher(relative(f)).matches())
                        .sorted().collect(Collectors.toList());
                for (Path f : hits) checkOne(sch, relative(f), readOrNull(f), errors);
            }
        }
    }

    /** Validates one file. A path that no `match` captures exits silently. */
    static void checkOne(Map<String, Object> sch, String rel, String content,
                         List<String> errors) {
        if (content == null) return;

        checkInjections(sch, rel, content, errors);   // every visited file, any type
        checkArguments(sch, rel, content, errors);    // idem
        if (matches(asStr(get(sch, "skill_classes", "match")), rel)) {
            checkSkillBody(sch, rel, content, errors);   // class marker + required sections
        }

        if (matchesAny(asStrList(get(sch, "settings", "match")), rel)) {
            checkSettings(sch, rel, content, errors);
            return;
        }

        if (matchesAny(asStrList(get(sch, "mcp", "match")), rel)) {
            checkMcp(asMap(sch.get("mcp")), rel, content, errors);
            return;
        }

        Map<String, Object> types = asMap(sch.get("types"));
        if (types == null) return;
        for (Map.Entry<String, Object> e : types.entrySet()) {
            Map<String, Object> t = asMap(e.getValue());
            if (t == null || !matches(asStr(t.get("match")), rel)) continue;
            checkFrontmatter(sch, e.getKey(), t, rel, content, errors);
            return;
        }
    }

    static final Pattern INJECTION = Pattern.compile("!`([^`\\n]+)`");
    /** A fenced block holds examples, not injections the runtime executes. */
    static final Pattern FENCE = Pattern.compile("(?s)```.*?```");
    /**
     * A double-backtick span is how markdown quotes something that itself contains a
     * backtick — which is exactly how prose has to write an injection when it talks about
     * one. Dropped before scanning, so documenting the rule doesn't break the rule.
     */
    static final Pattern TICK_SPAN = Pattern.compile("``.*?``");

    /**
     * Requires every `` !`command` `` injection to resolve its paths from the project
     * root instead of the shell's cwd. The injection runs in the session's persistent
     * shell: a `cd` in an earlier Bash call silently poisons every later one, and a
     * relative `test -f` then reports a file as absent while it exists — which is worse
     * than no information, because it arms the entry guard of the skill it belongs to.
     * lessons-learned-010 § 5: twelve occurrences across ten skills, one of them
     * `docker-architect` aborting on a file that was present and complete.
     * The required substring and the exemptions are data — `injections` in
     * .claude/schemas/extensions.json, never here (invariant 10).
     */
    static void checkInjections(Map<String, Object> sch, String rel, String content,
                                List<String> errors) {
        Map<String, Object> inj = asMap(sch.get("injections"));
        if (inj == null) return;                       // block absent: check is off
        String require = asStr(inj.get("require"));
        if (require == null || require.isEmpty()) return;
        // Only files a `types` match captures — a skill, an agent, or a rule. Everything
        // else the mode may be handed (a decision record, a lessons-learned) is prose
        // about injections, not a file the runtime executes them from.
        if (!typedFile(sch, rel)) return;

        List<Pattern> exempt = new ArrayList<>();
        for (String p : asStrList(inj.get("exempt_patterns"))) {
            try { exempt.add(Pattern.compile(p)); }
            catch (PatternSyntaxException e) {
                errors.add("  " + SCHEMA_FILE + " — injections.exempt_patterns has an"
                        + " invalid regex `" + p + "`: " + e.getDescription());
            }
        }

        String scanned = TICK_SPAN.matcher(FENCE.matcher(content).replaceAll(""))
                .replaceAll("");
        Matcher m = INJECTION.matcher(scanned);
        while (m.find()) {
            String cmd = m.group(1);
            if (cmd.contains(require)) continue;
            if (exempt.stream().anyMatch(p -> p.matcher(cmd).find())) continue;
            errors.add("  " + rel + ":" + lineOf(content, "!`" + cmd + "`")
                    + " — injection `" + shorten(cmd) + "` resolves paths from the shell's"
                    + " cwd. Use \"" + require + ":-.}/<path>\" — a `cd` in an earlier Bash"
                    + " call makes it report a file as absent while it exists."
                    + " Genuinely cwd-independent: add a regex to injections.exempt_patterns");
        }
    }

    /**
     * Rejects the argument marker written in prose. The runtime interpolates every
     * occurrence, not only the one under `## Target`: a sentence that *talks about* the
     * argument ("with empty $ARGUMENTS", "$ARGUMENTS empty") arrives at the model with the
     * real value substituted into it, and reads as the opposite of what it says —
     * lessons-learned-012 § 2, where `/new-feature` ended up ordering `test-architect`
     * into setup mode while naming the argument that means design mode. The interpolation
     * point itself is a line holding nothing but the marker, and that is the only
     * occurrence allowed. Marker, the field that makes a file eligible, the shape of the
     * interpolation line and the exemptions are data — `arguments` in
     * .claude/schemas/extensions.json, never here (invariant 10).
     */
    static void checkArguments(Map<String, Object> sch, String rel, String content,
                               List<String> errors) {
        Map<String, Object> arg = asMap(sch.get("arguments"));
        if (arg == null) return;                       // block absent: check is off
        String marker = asStr(arg.get("marker"));
        if (marker == null || marker.isEmpty()) return;
        if (!typedFile(sch, rel)) return;

        String needs = asStr(arg.get("requires_field"));
        if (needs != null && !needs.isEmpty()) {
            Map<String, String> fm = frontmatter(content);
            if (fm == null || !fm.containsKey(needs)) return;
        }

        List<Pattern> exempt = new ArrayList<>();
        for (String p : asStrList(arg.get("exempt_patterns"))) {
            try { exempt.add(Pattern.compile(p)); }
            catch (PatternSyntaxException e) {
                errors.add("  " + SCHEMA_FILE + " — arguments.exempt_patterns has an"
                        + " invalid regex `" + p + "`: " + e.getDescription());
            }
        }
        String point = asStr(arg.get("interpolation_line"));
        Pattern alone = Pattern.compile(point == null || point.isEmpty()
                ? "^[ \\t]*" + Pattern.quote(marker) + "[ \\t]*$" : point);

        // Same two escapes `checkInjections` grants, for the same reason: a fenced block
        // and a double-backtick span are how this repository documents the marker without
        // the runtime ever interpolating what it wrote.
        String scanned = TICK_SPAN.matcher(FENCE.matcher(content).replaceAll(""))
                .replaceAll("");
        for (String l : scanned.split("\n", -1)) {
            if (!l.contains(marker) || alone.matcher(l).matches()) continue;
            if (exempt.stream().anyMatch(p -> p.matcher(l).find())) continue;
            errors.add("  " + rel + ":" + lineOf(content, l.strip()) + " — `" + marker
                    + "` written in prose. The runtime substitutes it here too, so the"
                    + " sentence reaches the model with the real argument inside it."
                    + " Write \"the argument\" or \"the target above\"; the only allowed"
                    + " occurrence is a line holding nothing else."
                    + " Genuinely safe: add a regex to arguments.exempt_patterns");
        }
    }

    /** True when some `types` entry's `match` captures this path. */
    static boolean typedFile(Map<String, Object> sch, String rel) {
        Map<String, Object> types = asMap(sch.get("types"));
        if (types == null) return false;
        for (Object t : types.values()) {
            Map<String, Object> m = asMap(t);
            if (m != null && matches(asStr(m.get("match")), rel)) return true;
        }
        return false;
    }

    /** 1-based line of `needle` in `content`, or 0 when it isn't there. */
    static int lineOf(String content, String needle) {
        int at = content.indexOf(needle);
        if (at < 0) return 0;
        int line = 1;
        for (int i = 0; i < at; i++) if (content.charAt(i) == '\n') line++;
        return line;
    }

    static String shorten(String s) {
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() <= 60 ? one : one.substring(0, 57) + "...";
    }

    static void checkFrontmatter(Map<String, Object> sch, String type,
                                 Map<String, Object> t, String rel, String content,
                                 List<String> errors) {
        Map<String, String> fm = frontmatter(content);
        if (fm == null) {
            errors.add("  " + rel + " — no frontmatter block closed by `---`"
                    + " (type '" + type + "')");
            return;
        }

        for (String req : asStrList(t.get("required"))) {
            if (!fm.containsKey(req)) {
                errors.add("  " + rel + " — missing required field `" + req + "`");
            }
        }

        List<String> allowed = asStrList(t.get("allowed"));
        List<String> forbidden = asStrList(sch.get("forbidden_everywhere"));
        for (String key : fm.keySet()) {
            if (forbidden.contains(key)) {
                errors.add("  " + rel + " — `" + key + "` is not a native field."
                        + " What it used to carry lives in the `## Contrato` section of the body");
                continue;
            }
            if (allowed.contains(key)) continue;
            String hint = nearest(key, allowed);
            errors.add("  " + rel + " — `" + key + "` is not a field of '" + type + "'"
                    + (hint != null
                        ? ". It is written `" + hint + "` (skills use kebab-case,"
                          + " agents use camelCase)"
                        : ". The runtime silently ignores it"));
        }

        Map<String, Object> enums = asMap(t.get("enum"));
        if (enums == null) return;
        for (Map.Entry<String, Object> e : enums.entrySet()) {
            String v = fm.get(e.getKey());
            List<String> ok = asStrList(e.getValue());
            if (v == null || v.isEmpty() || ok.contains(v)) continue;
            errors.add("  " + rel + " — `" + e.getKey() + ": " + v + "` invalid."
                    + " Values: " + String.join(", ", ok));
        }
    }

    /**
     * Validates the top-level keys and every hook registration of a settings file —
     * this repo's `.claude/settings.json` and the generated project's template, both
     * listed in extensions.json's `settings.match`.
     *
     * <p>What it catches is the set of mistakes the runtime accepts in silence: an
     * event name that exists nowhere, a `matcher` on an event that never reads one, a
     * `type` this repo has no handler for, a shell string written where the executable
     * goes, and a non-positive `timeout`. None of these fails at startup; the hook
     * simply never runs, or runs without the filter it appears to have. Every list
     * lives in extensions.json — invariant 10, @CLAUDE.md.
     */
    static void checkSettings(Map<String, Object> sch, String rel, String content,
                              List<String> errors) {
        Map<String, Object> root = asMap(Json.parse(content));
        if (root == null) { errors.add("  " + rel + " — invalid JSON"); return; }

        List<String> topAllowed = asStrList(get(sch, "settings", "allowed"));
        if (!topAllowed.isEmpty()) {
            for (String key : root.keySet()) {
                if (!topAllowed.contains(key)) {
                    errors.add("  " + rel + " — `" + key + "` is not a recognized top-level key");
                }
            }
        }

        Map<String, Object> hooks = asMap(root.get("hooks"));
        if (hooks == null) return;

        List<String> req = asStrList(get(sch, "settings", "hook_entry", "required"));
        List<String> allowed = asStrList(get(sch, "settings", "hook_entry", "allowed"));
        List<String> events = new ArrayList<>(asStrList(get(sch, "settings", "hook_events")));
        events.addAll(asStrList(get(sch, "settings", "hook_events_extra")));
        List<String> matcherEvents = asStrList(get(sch, "settings", "matcher_events"));
        List<String> groupAllowed = asStrList(get(sch, "settings", "group_allowed"));
        List<String> entryTypes = asStrList(get(sch, "settings", "entry_types"));
        List<String> badChars = asStrList(get(sch, "settings", "command_forbidden_chars"));

        for (Map.Entry<String, Object> ev : hooks.entrySet()) {
            String event = ev.getKey();
            String where = rel + " › " + event;

            if (!events.isEmpty() && !events.contains(event)) {
                errors.add("  " + rel + " — `" + event + "` is not a lifecycle event."
                        + " It never fires. Known events in"
                        + " .claude/schemas/extensions.json › settings.hook_events");
            }

            for (Object group : asList(ev.getValue())) {
                Map<String, Object> g = asMap(group);
                if (g == null) continue;

                for (String k : g.keySet()) {
                    if (!groupAllowed.isEmpty() && !groupAllowed.contains(k)) {
                        errors.add("  " + where + " — `" + k
                                + "` is not a hook group field");
                    }
                }
                if (g.containsKey("matcher") && !matcherEvents.isEmpty()
                        && !matcherEvents.contains(event)) {
                    errors.add("  " + where + " — `matcher` is ignored on this event."
                            + " It reads as a filter and filters nothing");
                }

                for (Object entry : asList(g.get("hooks"))) {
                    Map<String, Object> h = asMap(entry);
                    if (h == null) continue;
                    for (String r : req) {
                        if (!h.containsKey(r)) {
                            errors.add("  " + where + " — hook entry missing `" + r + "`");
                        }
                    }
                    for (String k : h.keySet()) {
                        if (!allowed.contains(k)) {
                            errors.add("  " + where + " — `" + k
                                    + "` is not a hook entry field");
                        }
                    }
                    checkHookEntry(where, h, entryTypes, badChars, errors);
                }
            }
        }
    }

    /** Type, exec form, and timeout of one hook entry. Lists come from extensions.json. */
    static void checkHookEntry(String where, Map<String, Object> h, List<String> entryTypes,
                               List<String> badChars, List<String> errors) {
        String type = asStr(h.get("type"));
        if (type != null && !entryTypes.isEmpty() && !entryTypes.contains(type)) {
            errors.add("  " + where + " — `type: " + type + "` has no handler here."
                    + " This repo runs command hooks only: "
                    + String.join(", ", entryTypes));
        }

        String command = asStr(h.get("command"));
        if (command != null) {
            for (String bad : badChars) {
                if (command.contains(bad)) {
                    errors.add("  " + where + " — `command` contains `" + bad
                            + "`: that is a shell string where the executable goes."
                            + " Exec form: `command` is the binary, `args` the arguments");
                    break;
                }
            }
        }

        Object timeout = h.get("timeout");
        if (timeout != null && (!(timeout instanceof Number) || num(timeout) <= 0)) {
            errors.add("  " + where + " — `timeout` must be a positive number of seconds");
        }
    }

    /**
     * Validates .mcp.json (and its project-bootstrap template): unknown top-level or
     * per-server fields, missing field the declared transport requires, a reserved or
     * malformed server name, and a literal secret in `headers`/`env` — invariant 11,
     * @CLAUDE.md. The field list lives in extensions.json's `mcp` block, same
     * single-owner discipline as the frontmatter tables above.
     */
    static void checkMcp(Map<String, Object> mcpSchema, String rel, String content,
                        List<String> errors) {
        if (mcpSchema == null) return;

        Map<String, Object> root = asMap(Json.parse(content));
        if (root == null) { errors.add("  " + rel + " — invalid JSON"); return; }

        List<String> rootAllowed = asStrList(mcpSchema.get("root_allowed"));
        for (String key : root.keySet()) {
            if (!rootAllowed.contains(key)) {
                errors.add("  " + rel + " — `" + key
                        + "` is not a valid top-level key. Only `mcpServers`");
            }
        }

        Map<String, Object> servers = asMap(root.get("mcpServers"));
        if (servers == null) return;

        List<String> serverAllowed = asStrList(mcpSchema.get("server_allowed"));
        Map<String, Object> requiredByType = asMap(mcpSchema.get("required_by_type"));
        String namePattern = asStr(mcpSchema.get("name_pattern"));
        Pattern nameRe = namePattern == null ? null : Pattern.compile(namePattern);
        List<String> reserved = asStrList(mcpSchema.get("reserved_names"));
        Map<String, Object> secretScan = asMap(mcpSchema.get("secret_scan"));
        List<String> secretFields = secretScan == null ? List.of() : asStrList(secretScan.get("fields"));
        Pattern keyRe = secretScan == null || asStr(secretScan.get("key_pattern")) == null
                ? null : Pattern.compile(asStr(secretScan.get("key_pattern")));
        List<String> valuePrefixes = secretScan == null
                ? List.of() : asStrList(secretScan.get("value_prefixes"));

        for (Map.Entry<String, Object> e : servers.entrySet()) {
            String name = e.getKey();
            Map<String, Object> server = asMap(e.getValue());
            if (server == null) {
                errors.add("  " + rel + " — server `" + name + "` is not an object");
                continue;
            }
            if (reserved.contains(name)) {
                errors.add("  " + rel + " — server name `" + name
                        + "` is reserved by the runtime");
            }
            if (nameRe != null && !nameRe.matcher(name).matches()) {
                errors.add("  " + rel + " — server name `" + name
                        + "` doesn't match `" + namePattern + "`");
            }
            for (String key : server.keySet()) {
                if (!serverAllowed.contains(key)) {
                    errors.add("  " + rel + " — server `" + name
                            + "` has unknown field `" + key + "`");
                }
            }

            String type = asStr(server.get("type"));
            if (type == null) {
                errors.add("  " + rel + " — server `" + name + "` is missing `type`");
            } else if (requiredByType != null && !requiredByType.containsKey(type)) {
                errors.add("  " + rel + " — server `" + name + "` has unknown `type: "
                        + type + "`. Allowed: " + String.join(", ", requiredByType.keySet()));
            } else if (requiredByType != null) {
                for (String req : asStrList(requiredByType.get(type))) {
                    if (!server.containsKey(req)) {
                        errors.add("  " + rel + " — server `" + name + "` (type `" + type
                                + "`) is missing required field `" + req + "`");
                    }
                }
            }

            for (String field : secretFields) {
                Map<String, Object> block = asMap(server.get(field));
                if (block == null) continue;
                for (Map.Entry<String, Object> kv : block.entrySet()) {
                    String value = asStr(kv.getValue());
                    if (value != null && looksLikeSecret(kv.getKey(), value, keyRe, valuePrefixes)) {
                        errors.add("  " + rel + " — server `" + name + "` field `" + field
                                + "." + kv.getKey() + "` looks like a literal secret."
                                + " Use `${VAR}` / `${VAR:-default}`, `oauth`, or"
                                + " `headersHelper` instead — invariant 11, @CLAUDE.md");
                    }
                }
            }
        }
    }

    /**
     * A value with no `${...}` expansion whose key name looks credential-shaped, or
     * whose value starts with a known token prefix, is a literal secret. Any `${`
     * anywhere in the value is treated as safe — "Bearer ${API_TOKEN}" is the
     * documented good pattern and must not be flagged alongside "Bearer abc123xyz".
     */
    static boolean looksLikeSecret(String key, String value, Pattern keyRe,
                                   List<String> valuePrefixes) {
        if (value.contains("${")) return false;
        boolean keySensitive = keyRe != null && keyRe.matcher(key).find();
        boolean valueSensitive = valuePrefixes.stream().anyMatch(value::startsWith);
        return keySensitive || valueSensitive;
    }

    static boolean matchesAny(List<String> globPatterns, String rel) {
        for (String g : globPatterns) if (matches(g, rel)) return true;
        return false;
    }

    /**
     * Top-level frontmatter keys and their scalar value, in file order. Returns
     * null when there is no closed `---` block. No YAML library: only unindented
     * keys matter. `#` comments are ignored — two rules in this repo use them
     * inside frontmatter.
     */
    static Map<String, String> frontmatter(String content) {
        List<String> lines = content.lines().collect(Collectors.toList());
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).strip();
            if (l.isEmpty()) continue;
            if (l.equals("---")) start = i;
            break;
        }
        if (start < 0) return null;

        Map<String, String> fm = new LinkedHashMap<>();
        for (int i = start + 1; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw.strip().equals("---")) return fm;
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
            if (Character.isWhitespace(raw.charAt(0)) || raw.startsWith("-")) continue;
            int c = raw.indexOf(':');
            if (c <= 0) continue;
            String v = raw.substring(c + 1).strip();
            if (v.startsWith(">") || v.startsWith("|")) v = "";   // block scalar
            fm.put(raw.substring(0, c).strip(), v);
        }
        return null;                                  // block opened and never closed
    }

    /** The allowed field that only differs in naming convention, or null. */
    static String nearest(String key, List<String> allowed) {
        String n = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        for (String a : allowed) {
            if (a.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").equals(n)) {
                return a;
            }
        }
        return null;
    }

    static boolean matches(String globPattern, String rel) {
        return globPattern != null && glob(globPattern).matcher(rel).matches();
    }

    /** Glob to regex: `**` crosses `/`, `*` does not. */
    static Pattern glob(String g) {
        StringBuilder b = new StringBuilder("^");
        for (int i = 0; i < g.length(); i++) {
            char c = g.charAt(i);
            if (c == '*') {
                if (i + 1 < g.length() && g.charAt(i + 1) == '*') { b.append(".*"); i++; }
                else b.append("[^/]*");
            } else if (c == '?') {
                b.append("[^/]");
            } else {
                b.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(b.append("$").toString());
    }

    static String readOrNull(Path p) {
        try { return Files.isRegularFile(p)
                ? Files.readString(p, StandardCharsets.UTF_8) : null; }
        catch (IOException e) { return null; }
    }

    // ── audit ────────────────────────────────────────────────────────────────
    //
    // Execution trail of the project's orchestrator skills — the ones carrying
    // `disable-model-invocation: true`. It is a hook and not a skill because the record
    // has to survive the model forgetting, the session dying, and the user pressing
    // Ctrl+C. A skill can promise that; only a lifecycle event delivers it.
    //
    // Phases (args[1]), one per hook event:
    //   prompt    UserPromptSubmit               opens a run when the prompt is `/<orchestrator>`
    //   call      PreToolUse Skill|Task|Agent    one node of the chain
    //   ask       PreToolUse AskUserQuestion     the run starts waiting for the user
    //   answer    PostToolUse AskUserQuestion    the wait ends — measured apart from work
    //   file      PostToolUse Write|Edit         file touched — feeds rule inference by glob
    //   fail      PostToolUseFailure             rework counter
    //   stopfail  StopFailure                    the turn ended in error
    //   perm      PermissionRequest|Denied       permission asked for mid-run
    //   agent     SubagentStop                   closes the current agent node
    //   compact   PreCompact                     manual `/compact` or automatic overflow
    //   flush     Stop                           marks the turn's end, rewrites the report
    //   close     SessionEnd                     stamps the final status and closes the run
    //
    // Every phase appends one line to .claude/audit-usage/.state/<session>.ndjson, and
    // the report is DERIVED from that log at flush time. Append-only survives a kill -9;
    // a read-modify-write of a structured file does not.
    //
    // A run closes at the first user prompt after it opened. Answers to AskUserQuestion
    // arrive as tool results, never as prompts, so any prompt is work outside the run —
    // counting it stretched a 12-second step to ten minutes in a real report.
    //
    // Inside a git worktree the trail is written to the MAIN checkout's
    // .claude/audit-usage/, so reports, history.jsonl and pricing.json never diverge
    // between checkouts.
    //
    // Switched off by the absence of .claude/audit-usage/: every phase returns
    // immediately. That is why this meta-repository, which does not create the
    // directory, pays nothing for a mode wired only into the generated project.

    static final String AUDIT_DIR = ".claude/audit-usage";

    /**
     * The trail's directory. In a git worktree `.git` is a file, and the trail belongs to
     * the main checkout — one history.jsonl, one pricing.json, whichever checkout ran.
     * Everywhere else, and whenever git can't answer, it's this project's own.
     */
    static Path auditDir() {
        if (!Files.isRegularFile(ROOT.resolve(".git"))) return ROOT.resolve(AUDIT_DIR);
        try {
            Proc p = run(gitCmd(), "rev-parse", "--git-common-dir");
            if (p.exit == 0 && !p.out.isEmpty()) {
                Path common = ROOT.resolve(p.out.get(0).strip()).normalize();
                if (common.getFileName() != null && ".git".equals(common.getFileName().toString())) {
                    return common.getParent().resolve(AUDIT_DIR);
                }
            }
        } catch (Exception ignored) { }
        return ROOT.resolve(AUDIT_DIR);
    }
    static final Locale PT = Locale.forLanguageTag("pt-BR");

    static void audit(String phase, String stdin) throws Exception {
        Path dir = auditDir();
        if (!Files.isDirectory(dir)) return;
        if ("summary".equals(phase)) { auditSummary(dir); return; }

        Object in = Json.parse(stdin);
        String session = asStr(get(in, "session_id"));
        if (session == null || session.isBlank()) session = "unknown";
        Path log = dir.resolve(".state").resolve(session.replaceAll("[^A-Za-z0-9_-]", "_") + ".ndjson");

        switch (phase) {
            case "prompt"   -> auditPrompt(dir, log, in);
            case "call"     -> auditCall(log, in);
            case "file"     -> auditFile(log, in);
            case "ask"      -> append(log, ev("ask"));
            case "answer"   -> append(log, ev("answer"));
            case "fail"     -> append(log, ev("fail", "tool", asStr(get(in, "tool_name"))));
            case "stopfail" -> append(log, ev("stop_fail"));
            case "perm"     -> append(log, ev("perm",
                                       "tool", asStr(get(in, "tool_name")),
                                       "event", asStr(get(in, "hook_event_name"))));
            case "agent"    -> append(log, ev("agent_end",
                                       "name", asStr(get(in, "agent_type")),
                                       "agent_id", asStr(get(in, "agent_id"))));
            case "compact"  -> append(log, ev("compact", "trigger", asStr(get(in, "trigger"))));
            case "flush"    -> { append(log, ev("stop")); auditRender(dir, log, in, false); }
            case "close"    -> auditRender(dir, log, in, true);
            default         -> { }
        }
    }

    /**
     * A prompt ends the run in progress, and opens one when it is `/<name>` for a skill
     * of this project. Which skills qualify is data, not code: a folder under
     * `.claude/skills/` is enough, so a skill written later is audited without this file
     * being touched — @CLAUDE.md invariant 7. A prompt that opens nothing is still kept,
     * overwritten each turn: when the model then invokes a skill or agent on its own,
     * `auditCall` opens the run and needs the words that led to it.
     */
    static void auditPrompt(Path dir, Path log, Object in) throws Exception {
        String prompt = asStr(get(in, "prompt"));
        if (prompt == null) return;

        // A background agent's result comes back as a synthetic `<task-notification>`
        // prompt, injected while the run that launched it is still open — not user
        // intent to end anything. Closing on it is what froze `/test-architect setup`'s
        // and `/new-feature`'s reports mid-flight: the very next turn (still inside the
        // agent's own work) reported "success" over a run the agent hadn't finished.
        // Neither close, nor open, nor overwrite the observer prompt file — let the run
        // keep absorbing the agent's remaining `file`/`agent_end` events.
        // .claude/decisions/0041-audit-background-subagent-tracking.md
        if (TASK_NOTIFICATION.matcher(prompt).find()) return;

        Matcher m = Pattern.compile("^\\s*/([a-z0-9][a-z0-9-]*)(.*)$", Pattern.DOTALL)
                .matcher(prompt);
        String name = m.find() ? m.group(1) : null;

        // An observer closes the run in progress and starts none of its own. Closing
        // already happened anyway (any second `/command` closes), so what this drops is
        // the empty report an observer would leave behind — and, for `/audit-usage`, the
        // feedback loop of a report about reading reports. The close is the useful half:
        // within one session a report is stuck at "em andamento" until something ends
        // the run, so asking for it is what finalizes it.
        if (name != null && isAuditExcluded(name)) {
            auditRender(dir, log, in, true);
            return;
        }
        // Any prompt ends the run in progress — a second `/command`, or plain text. An
        // AskUserQuestion answer is a tool result, not a prompt, so it never gets here.
        if (Files.isRegularFile(log)) auditRender(dir, log, in, true);

        String stripped = prompt.strip();
        String redacted = redact(stripped);
        String[] said = {
                "prompt", redacted,
                "prompt_sha256", sha256(prompt),
                "redacted", String.valueOf(!redacted.equals(stripped))};

        if (name == null || !isAuditedSkill(name)) {
            Files.createDirectories(log.getParent());
            Files.writeString(promptFile(log), ev("prompt", said) + "\n", StandardCharsets.UTF_8);
            return;
        }
        openRun(log, name, "skill", "user", m.group(2).strip(), said, null, null);
    }

    /** The last prompt of the session that opened no run. Lives in `.state/`, gitignored. */
    static Path promptFile(Path log) {
        return log.resolveSibling(log.getFileName().toString().replace(".ndjson", ".prompt.json"));
    }

    static void openRun(Path log, String name, String kind, String origin, String args,
                        String[] said, String toolUseId, String inAgent) throws IOException {
        Files.createDirectories(log.getParent());
        Files.writeString(log, "", StandardCharsets.UTF_8);
        List<String> kv = new ArrayList<>(List.of("skill", name, "kind", kind, "origin", origin));
        // `args` is redacted too, not just `prompt`: it is a slice of the same text and
        // it is what the report puts in the title. Redacting one and not the other
        // leaks the secret in the most visible line of the file.
        kv.add("args");        kv.add(args == null ? null : redact(args));
        kv.addAll(Arrays.asList(said));
        kv.add("tool_use_id"); kv.add(toolUseId);
        kv.add("in_agent");    kv.add(inAgent);
        kv.add("head");        kv.add(gitShort());
        kv.add("perm_before"); kv.add(String.join(" ", localPermissions()));
        append(log, ev("run_start", kv.toArray(new String[0])));
    }

    /**
     * Skills that observe instead of producing work — `/audit-usage` reads the trail,
     * `/arch-doctor` reads the setup. The list is data in extensions.json's
     * `audit.exclude_skills`, never here: same single-owner discipline as `redact`
     * (invariants 7 and 10).
     */
    static boolean isAuditExcluded(String skill) {
        return asStrList(get(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE))),
                "audit", "exclude_skills")).contains(skill);
    }

    /**
     * A piece is audited when its name resolves to a file of this project. A plugin skill
     * (`plugin:name`) or a runtime agent (`Explore`, `general-purpose`) has no such file
     * and falls out by construction — no list of exclusions to keep in sync.
     */
    static final Pattern PIECE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    /** The harness's own synthetic prompt delivering a background agent's result. */
    static final Pattern TASK_NOTIFICATION = Pattern.compile("^\\s*<task-notification>");

    static boolean isAuditedSkill(String name) {
        return name != null && PIECE_NAME.matcher(name).matches()
                && Files.isRegularFile(ROOT.resolve(".claude/skills").resolve(name).resolve("SKILL.md"));
    }

    static boolean isAuditedAgent(String name) {
        return name != null && PIECE_NAME.matcher(name).matches()
                && Files.isRegularFile(ROOT.resolve(".claude/agents").resolve(name + ".md"));
    }

    static boolean isAudited(String kind, String name) {
        return "agent".equals(kind) ? isAuditedAgent(name) : isAuditedSkill(name);
    }

    /**
     * A Skill or Agent call. Inside an open run it is a node. With no run open, and when
     * the piece belongs to this project, it opens one: the model invoked it on its own —
     * after a plain prompt, or chained by another piece's instructions — and that is the
     * invocation `auditPrompt` never sees. `tool_use_id` ties an agent node to its own
     * transcript; `agent_id` is present only when the call comes from inside a subagent.
     */
    static void auditCall(Path log, Object in) throws IOException {
        Map<String, Object> ti = asMap(get(in, "tool_input"));
        if (ti == null) return;
        String tool = asStr(get(in, "tool_name"));
        boolean skill = "Skill".equals(tool);
        if (!skill && !"Task".equals(tool) && !"Agent".equals(tool)) return;
        String kind = skill ? "skill" : "agent";
        String name = asStr(ti.get(skill ? "skill" : "subagent_type"));
        String toolUseId = asStr(get(in, "tool_use_id"));
        String inAgent = asStr(get(in, "agent_id"));

        if (!Files.isRegularFile(log)) {
            if (!isAudited(kind, name) || isAuditExcluded(name)) return;
            Object said = Json.parse(readOrNull(promptFile(log)));
            // `turn_t`: the model message that decided to call this piece was written before
            // PreToolUse fired. Its tokens belong to the run, so they are counted from the prompt.
            String[] kv = said == null ? new String[0] : new String[] {
                    "turn_t", String.valueOf(num(get(said, "t"))),
                    "prompt", asStr(get(said, "prompt")),
                    "prompt_sha256", asStr(get(said, "prompt_sha256")),
                    "redacted", asStr(get(said, "redacted"))};
            openRun(log, name, kind, "model", asStr(ti.get(skill ? "args" : "description")),
                    kv, toolUseId, inAgent);
            return;
        }
        if (skill) {
            String args = asStr(ti.get("args"));
            append(log, ev("skill", "name", name, "args", args == null ? null : redact(args),
                    "tool_use_id", toolUseId, "in_agent", inAgent));
        } else {
            append(log, ev("agent",
                    "name", name,
                    "detail", asStr(ti.get("description")),
                    "model", asStr(ti.get("model")),
                    "tool_use_id", toolUseId, "in_agent", inAgent));
        }
    }

    static void auditFile(Path log, Object in) {
        String f = asStr(get(in, "tool_input", "file_path"));
        if (f == null) return;
        append(log, ev("file", "op", asStr(get(in, "tool_name")),
                "path", relative(Paths.get(f))));
    }

    /** One NDJSON event. A null value is dropped instead of written as "null". */
    static String ev(String name, String... kv) {
        StringBuilder b = new StringBuilder("{\"t\":").append(System.currentTimeMillis())
                .append(",\"iso\":\"").append(Instant.now()).append('"')
                .append(",\"e\":\"").append(name).append('"');
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] == null) continue;
            b.append(",\"").append(kv[i]).append("\":\"")
             .append(jsonEscape(kv[i + 1])).append('"');
        }
        return b.append('}').toString();
    }

    static String jsonEscape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> {
                    if (c < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    /** Appends to an open run. No open run, no record — that is the off switch. */
    static void append(Path log, String line) {
        if (!Files.isRegularFile(log)) return;
        try {
            Files.writeString(log, line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    /**
     * Blanks anything credential-shaped before the prompt reaches a versioned file.
     * The patterns live in extensions.json's `audit.redact` block, never here — same
     * single-owner discipline as every other list this hook reads (invariant 10). A
     * secret pasted into a prompt and committed is irreversible; invariant 11 exists
     * for exactly this, and `.claude/audit-usage/` is versioned on purpose.
     */
    static String redact(String text) {
        Map<String, Object> r = asMap(get(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE))),
                "audit", "redact"));
        if (r == null) return text;
        String out = text;
        for (String p : asStrList(r.get("patterns"))) {
            try { out = Pattern.compile(p).matcher(out).replaceAll("$1[REDACTED]"); }
            catch (Exception ignored) { }
        }
        for (String prefix : asStrList(r.get("value_prefixes"))) {
            out = Pattern.compile(Pattern.quote(prefix) + "\\S+").matcher(out)
                    .replaceAll(Matcher.quoteReplacement(prefix + "[REDACTED]"));
        }
        return out;
    }

    static String sha256(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format(Locale.ROOT, "%02x", x));
            return b.substring(0, 16);
        } catch (Exception e) { return "?"; }
    }

    static String gitShort() {
        try {
            Proc p = run(gitCmd(), "rev-parse", "--short", "HEAD");
            return p.exit == 0 && !p.out.isEmpty() ? p.out.get(0).strip() : null;
        } catch (Exception e) { return null; }
    }

    /**
     * `permissions.*` of settings.local.json — where a rule granted mid-session lands.
     * Diffing this file before and after is the only observation of "permission added"
     * that does not depend on an undocumented hook payload.
     */
    static List<String> localPermissions() {
        Map<String, Object> p = asMap(get(Json.parse(
                readOrNull(ROOT.resolve(".claude/settings.local.json"))), "permissions"));
        if (p == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String k : List.of("allow", "ask", "deny")) {
            for (String v : asStrList(p.get(k))) out.add(k + ": " + v);
        }
        return out;
    }

    record Node(String kind, String name, String detail, long start, int depth,
                String toolUseId, String inAgent) {}

    /** One assistant message's usage: input, output, cache read, cache write. */
    record Turn(long t, String model, long[] u) {}

    /** Token usage summed per model — a subagent may run on another model, and a price is per model. */
    static final class Usage {
        final Map<String, long[]> byModel = new LinkedHashMap<>();

        void add(String model, long[] u) {
            long[] a = byModel.computeIfAbsent(model == null ? "?" : model, k -> new long[4]);
            for (int i = 0; i < 4; i++) a[i] += u[i];
        }
        void addAll(Usage o) { if (o != null) o.byModel.forEach(this::add); }
        long sum(int i) { return byModel.values().stream().mapToLong(a -> a[i]).sum(); }
        long in()         { return sum(0); }
        long out()        { return sum(1); }
        long cacheRead()  { return sum(2); }
        long cacheWrite() { return sum(3); }
        long billable()    { return in() + out() + cacheWrite(); }
        long contextRead() { return in() + cacheRead(); }
        String model() { return byModel.isEmpty() ? null : String.join(", ", byModel.keySet()); }
    }

    /**
     * Rewrites the report from the event log, from scratch, every time. Idempotent by
     * construction: an execution killed halfway keeps the last flush, marked
     * "em andamento", instead of leaving nothing behind.
     */
    static void auditRender(Path dir, Path log, Object in, boolean closing) throws Exception {
        if (!Files.isRegularFile(log)) return;
        List<Map<String, Object>> events = new ArrayList<>();
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            Map<String, Object> e = asMap(Json.parse(line));
            if (e != null && asStr(e.get("e")) != null) events.add(e);
        }
        if (events.isEmpty()) return;
        Map<String, Object> s0 = events.get(0);
        if (!"run_start".equals(asStr(s0.get("e")))) return;

        String skill    = orDash(asStr(s0.get("skill")));
        String kind     = "agent".equals(asStr(s0.get("kind"))) ? "agent" : "skill";
        String origin   = "model".equals(asStr(s0.get("origin"))) ? "model" : "user";
        String args     = asStr(s0.get("args"));
        String startIso = asStr(s0.get("iso"));
        long   startMs  = num(s0.get("t"));
        long   lastMs   = num(events.get(events.size() - 1).get("t"));
        final long endMs = lastMs > startMs ? lastMs : System.currentTimeMillis();
        long   elapsed  = Math.max(1, endMs - startMs);
        String rootLabel = "agent".equals(kind) ? "🤖 " + skill
                : "model".equals(origin) ? "Skill(" + skill + ")" : "/" + skill;

        // An agent's own transcript is what proves it exists — build the lookup before
        // the event loop, not after, so `agent_end` can be matched to the node it
        // actually closes instead of decrementing a bare counter. `SubagentStop` fires
        // for internal/ephemeral agents that never went through `Skill`/`Task`/`Agent`
        // PreToolUse too (no transcript of ours, `agent_id` unknown here), interleaved
        // with the real one when the real agent runs in background — a plain depth--
        // per `agent_end` closed the wrong node on the first of those.
        String transcript = asStr(get(in, "transcript_path"));
        Map<String, Path> subs = subagentTranscripts(transcript);
        Map<String, String> toolUseIdOfAgentId = new HashMap<>();
        subs.forEach((toolUseId, file) -> toolUseIdOfAgentId.put(agentIdOf(file), toolUseId));

        List<Node> nodes = new ArrayList<>();
        Set<String> touched = new LinkedHashSet<>();
        Map<String, Integer> fails = new LinkedHashMap<>();
        Map<String, Integer> ops = new LinkedHashMap<>();
        List<String> perms = new ArrayList<>();
        int compacts = 0, manualCompacts = 0;
        List<Long> ticks = new ArrayList<>();
        List<long[]> waits = new ArrayList<>();
        long askAt = 0;
        boolean errored = false;
        // Stack of tool_use_ids of agents opened and not yet closed — depth is its size
        // plus one. `agent_end` pops the entry whose `agent_id` resolves back to it
        // through `toolUseIdOfAgentId`; an `agent_id` that resolves to nothing (an
        // internal agent, not one this run opened) closes none of ours.
        Deque<String> openAgents = new ArrayDeque<>();

        for (Map<String, Object> e : events) {
            String evKind = asStr(e.get("e"));
            long t = num(e.get("t"));
            // A compaction is the runtime's, not the node's: it doesn't extend whoever ran before it.
            if (!"run_start".equals(evKind) && !"compact".equals(evKind)) ticks.add(t);
            switch (evKind) {
                case "skill" -> nodes.add(new Node("skill", orDash(asStr(e.get("name"))),
                        asStr(e.get("args")), t, openAgents.size() + 1,
                        asStr(e.get("tool_use_id")), asStr(e.get("in_agent"))));
                case "agent" -> {
                    String toolUseId = asStr(e.get("tool_use_id"));
                    nodes.add(new Node("agent", orDash(asStr(e.get("name"))),
                            asStr(e.get("model")), t, openAgents.size() + 1,
                            toolUseId, asStr(e.get("in_agent"))));
                    openAgents.push(toolUseId);
                }
                case "agent_end" -> {
                    String toolUseId = toolUseIdOfAgentId.get(asStr(e.get("agent_id")));
                    if (toolUseId != null) openAgents.remove(toolUseId);
                }
                case "file" -> {
                    String path = asStr(e.get("path"));
                    if (path != null) touched.add(path);
                    ops.merge(orDash(asStr(e.get("op"))), 1, Integer::sum);
                }
                case "ask" -> askAt = t;
                case "answer" -> {
                    if (askAt > 0) waits.add(new long[] {askAt, t});
                    askAt = 0;
                }
                case "fail" -> {
                    String tool = orDash(asStr(e.get("tool")));
                    // A rejected AskUserQuestion never gets an answer event: its failure ends the wait.
                    if ("AskUserQuestion".equals(tool) && askAt > 0) {
                        waits.add(new long[] {askAt, t});
                        askAt = 0;
                    }
                    fails.merge(tool, 1, Integer::sum);
                }
                case "stop_fail" -> errored = true;
                case "compact" -> {
                    if ("manual".equals(asStr(e.get("trigger")))) manualCompacts++;
                    else compacts++;
                }
                case "perm" -> perms.add(orDash(asStr(e.get("tool"))));
                default -> { }
            }
        }

        // A question still open when the run is rendered waits until the end.
        if (askAt > 0) waits.add(new long[] {askAt, endMs});
        long wait  = waited(startMs, endMs, waits);
        long total = Math.max(1, elapsed - wait);

        // ── tokens per piece ──
        // An agent's usage is in its own transcript, never in the main one: it is read
        // from there, whole. A main-thread turn belongs to the last main-thread piece that
        // started before it, when that piece is a skill; after an agent call, or before
        // any piece, it is the root's orchestration. A skill called from inside a
        // subagent has no transcript of its own — its tokens are its agent's.
        // `subs` and `toolUseIdOfAgentId` are already built above, ahead of the event loop.

        long turnT = lnum(s0.get("turn_t"));
        long tokensFrom = turnT > 0 && turnT < startMs ? turnT : startMs;
        Usage all = new Usage(), rootSelf = new Usage();
        List<Usage> selfOf = new ArrayList<>();
        for (Node nd : nodes) {
            if ("agent".equals(nd.kind())) {
                Usage u = usage(subs.get(nd.toolUseId()));
                all.addAll(u);
                selfOf.add(u);
            } else {
                selfOf.add(nd.inAgent() == null ? new Usage() : null);
            }
        }
        if ("agent".equals(kind)) {
            Usage u = usage(subs.get(asStr(s0.get("tool_use_id"))));
            all.addAll(u);
            rootSelf.addAll(u);
        }
        for (Turn tr : usageTurns(transcript == null ? null : Paths.get(transcript))) {
            if (tr.t() < tokensFrom) continue;
            all.add(tr.model(), tr.u());
            int owner = -1;
            for (int i = 0; i < nodes.size(); i++) {
                Node nd = nodes.get(i);
                if (nd.inAgent() != null || nd.start() > tr.t()) continue;
                owner = "skill".equals(nd.kind()) ? i : -1;
            }
            (owner < 0 ? rootSelf : selfOf.get(owner)).add(tr.model(), tr.u());
        }

        List<String> rootPreloaded = "agent".equals(kind) ? preloadedSkills(skill) : List.of();

        List<String> added = new ArrayList<>(localPermissions());
        added.removeAll(Arrays.asList(orEmpty(asStr(s0.get("perm_before"))).split(" ")));
        String headStart = asStr(s0.get("head"));
        String headEnd   = gitShort();

        String status = !closing
                ? (!openAgents.isEmpty() ? "⏳ aguardando subagent em background" : "⏳ em andamento")
                : errored ? "❌ erro"
                : fails.isEmpty() ? "✅ sucesso"
                : "⚠️ sucesso com falhas recuperadas";

        StringBuilder md = new StringBuilder();
        md.append("# 🧾 Auditoria de execução — `").append(rootLabel)
          .append(args == null || args.isBlank() ? "" : " " + args).append("`\n\n");

        md.append("| | |\n|---|---|\n")
          .append("| 🎯 Peça | `").append(rootLabel).append("` · ")
          .append("agent".equals(kind) ? "agent" : "skill").append(" |\n")
          .append("| 🙋 Origem | ").append("model".equals(origin)
                  ? "modelo — chamada por conta própria, sem `/comando`"
                  : "usuário — `/comando`").append(" |\n")
          .append("| 🕐 Início | ").append(startIso).append(" |\n")
          .append("| 🏁 Fim | ").append(Instant.ofEpochMilli(endMs)).append(" |\n")
          .append("| ⏱️ Duração | ").append(hms(elapsed)).append(" |\n")
          .append("| ⏸️ Espera pelo usuário | ").append(hms(wait)).append(" |\n")
          .append("| ⚙️ Duração ativa | ").append(hms(total)).append(" |\n")
          .append("| ").append(status.substring(0, status.indexOf(' ')))
          .append(" Status | ").append(status.substring(status.indexOf(' ') + 1)).append(" |\n")
          .append("| 🤖 Modelo | ").append(orDash(all.model())).append(" |\n")
          .append("| 🌿 HEAD | ").append(orDash(headStart)).append(" → ")
          .append(orDash(headEnd)).append(" |\n\n");

        String prompt = asStr(s0.get("prompt"));
        md.append("## 📜 Comando inicial\n\n");
        if (prompt == null) {
            md.append("Nenhum prompt registrado antes da chamada.\n\n");
        } else {
            if ("model".equals(origin)) {
                md.append("Prompt do usuário no turno em que o modelo chamou a peça:\n\n");
            }
            md.append("```text\n").append(prompt).append("\n```\n\n")
              .append("`sha256` do texto original (antes da redação): `")
              .append(orDash(asStr(s0.get("prompt_sha256")))).append("` · segredos removidos: ")
              .append("true".equals(asStr(s0.get("redacted"))) ? "**sim**" : "não").append("\n\n");
        }

        List<Node> ranked = new ArrayList<>(nodes);
        ranked.sort((a, b) -> Long.compare(dur(b, nodes, endMs, ticks, waits), dur(a, nodes, endMs, ticks, waits)));
        if (!ranked.isEmpty()) {
            md.append("## 🏆 Etapas mais caras\n\n| # | Etapa | Duração | % |\n|---|---|---|---|\n");
            for (int i = 0; i < Math.min(3, ranked.size()); i++) {
                Node nd = ranked.get(i);
                long d = dur(nd, nodes, endMs, ticks, waits);
                md.append("| ").append(i + 1).append(" | `").append(nd.name()).append("` | ")
                  .append(hms(d)).append(" | ").append(pct(d, total)).append(" |\n");
            }
            md.append('\n');
        }

        md.append("## 🔗 Encadeamento\n\n```text\n");
        md.append(pad(rootLabel, 46)).append(bar(1.0)).append(' ')
          .append(pad(hms(total), 9)).append("100%\n");
        for (String p : rootPreloaded) md.append("  📎 ").append(p).append(" (pré-carregada)\n");
        for (int i = 0; i < nodes.size(); i++) {
            Node nd = nodes.get(i);
            long d = dur(nd, nodes, endMs, ticks, waits);
            boolean last = i == nodes.size() - 1;
            String indent = "  ".repeat(Math.max(0, nd.depth() - 1));
            String label = indent + (last ? "└─ " : "├─ ")
                    + ("agent".equals(nd.kind()) ? "🤖 " : "📘 ") + nd.name()
                    + (nd.detail() == null || nd.detail().isBlank() ? "" : " (" + nd.detail() + ")");
            md.append(pad(label, 46)).append(bar((double) d / total)).append(' ')
              .append(pad(hms(d), 9)).append(pct(d, total)).append('\n');
            if ("agent".equals(nd.kind())) {
                for (String p : preloadedSkills(nd.name())) {
                    md.append(indent).append("     📎 ").append(p).append(" (pré-carregada)\n");
                }
            }
        }
        md.append("```\n\n")
          .append("> Duração de um nó = do seu início até o **último evento dele**, dentro da janela")
          .append(" que termina no próximo nó de mesma profundidade ou menor. Espera por")
          .append(" `AskUserQuestion` descontada. Percentuais sobre a duração ativa.\n\n");

        md.append("## 🧩 Tokens por peça\n\n")
          .append("| Peça | Origem | 🧮 Faturável próprio | 💰 Custo | ⏱️ Duração |\n|---|---|---|---|---|\n")
          .append("| `").append(rootLabel).append("` | ")
          .append("model".equals(origin) ? "modelo" : "usuário").append(" | ")
          .append(n(rootSelf.billable())).append(" | ")
          .append(orDash(auditCost(dir, rootSelf))).append(" | ").append(hms(total)).append(" |\n");
        for (String p : rootPreloaded) {
            md.append("| `📎 ").append(p).append("` | pré-carregada | ↳ no agent | — | — |\n");
        }
        for (int i = 0; i < nodes.size(); i++) {
            Node nd = nodes.get(i);
            Usage u = selfOf.get(i);
            md.append("| `").append("agent".equals(nd.kind()) ? "🤖 " : "📘 ").append(nd.name())
              .append("` | aninhada | ")
              .append(u == null ? "↳ no agent" : n(u.billable())).append(" | ")
              .append(u == null ? "—" : orDash(auditCost(dir, u))).append(" | ")
              .append(hms(dur(nd, nodes, endMs, ticks, waits))).append(" |\n");
            if ("agent".equals(nd.kind())) {
                for (String p : preloadedSkills(nd.name())) {
                    md.append("| `📎 ").append(p).append("` | pré-carregada | ↳ no agent | — | — |\n");
                }
            }
        }
        md.append("\n> Agent = o transcript do próprio subagent. Skill no thread principal = mensagens")
          .append(" do modelo desde a chamada até a próxima peça do thread principal. Resto = raiz.")
          .append(" Somados, fecham o agregado abaixo.\n\n");

        md.append("## 📊 Tokens (agregado)\n\n")
          .append("| Métrica | Valor |\n|---|---|\n")
          .append("| ⬇️ input | ").append(n(all.in())).append(" |\n")
          .append("| ⬆️ output | ").append(n(all.out())).append(" |\n")
          .append("| ♻️ cache read | ").append(n(all.cacheRead())).append(" |\n")
          .append("| 💾 cache write | ").append(n(all.cacheWrite())).append(" |\n")
          .append("| 🧮 faturável (input + output + cache write) | **").append(n(all.billable())).append("** |\n");
        String cost = auditCost(dir, all);
        md.append("| 💰 custo estimado | ")
          .append(cost == null ? "— (preencha `" + AUDIT_DIR + "/pricing.json`)" : "**" + cost + "**")
          .append(" |\n\n");
        long ctx = all.contextRead();
        double hit = ctx == 0 ? 0 : (double) all.cacheRead() / ctx;
        md.append("Cache hit ").append(pct(all.cacheRead(), Math.max(1, ctx))).append(" ")
          .append(bar(hit)).append("\n\n");

        md.append("## 🔐 Permissões adicionadas durante a execução\n\n");
        if (added.isEmpty()) {
            md.append("Nenhuma. `settings.local.json` não mudou entre o início e o fim.\n\n");
        } else {
            md.append("| Regra |\n|---|\n");
            for (String a : added) md.append("| `").append(a).append("` |\n");
            md.append('\n');
        }
        if (!perms.isEmpty()) {
            md.append("Solicitações observadas (").append(perms.size()).append("): ")
              .append(String.join(", ", new LinkedHashSet<>(perms))).append("\n\n");
        }

        md.append("## 📐 Regras carregadas (inferidas por território)\n\n");
        List<RuleHit> rules = auditRules(touched);
        if (rules.isEmpty()) {
            md.append("Nenhum arquivo tocado casa com o `paths` de alguma regra.\n\n");
        } else {
            md.append("| Regra | Glob | Arquivos |\n|---|---|---|\n");
            for (RuleHit r : rules) {
                md.append("| `").append(r.rule()).append("` | `").append(r.glob())
                  .append("` | ").append(r.files().size()).append(" |\n");
            }
            md.append("\n> Inferência, não observação: nenhum evento de hook expõe qual regra")
              .append(" entrou em contexto. Isto é \"as regras que **deveriam** ter carregado\".\n\n");
        }

        md.append("## 📁 Arquivos tocados\n\n");
        if (touched.isEmpty()) {
            md.append("Nenhum.\n\n");
        } else {
            md.append(ops.entrySet().stream().map(e -> e.getValue() + "× " + e.getKey())
                    .collect(Collectors.joining(" · ")))
              .append(" — ").append(touched.size())
              .append(touched.size() == 1 ? " arquivo distinto" : " arquivos distintos")
              .append("\n\n```text\n")
              .append(String.join("\n", touched)).append("\n```\n\n");
        }

        md.append("## 🔁 Retrabalho\n\n");
        if (fails.isEmpty()) {
            md.append("Nenhuma ferramenta falhou. 🎉\n\n");
        } else {
            md.append("| Ferramenta | Falhas |\n|---|---|\n");
            for (Map.Entry<String, Integer> e : fails.entrySet()) {
                md.append("| `").append(e.getKey()).append("` | ").append(e.getValue()).append(" |\n");
            }
            md.append("\n> Falha repetida na mesma ferramenta é sinal de spec ruim, não de azar.\n\n");
        }

        if (compacts > 0) {
            md.append("## ⚠️ Incidentes\n\n🗜️ Contexto compactado automaticamente ").append(compacts)
              .append("× durante a execução — qualidade da saída cai depois de cada compactação.\n\n");
        }
        if (manualCompacts > 0) {
            md.append("ℹ️ `/compact` manual: ").append(manualCompacts)
              .append("× — decisão do usuário, não incidente.\n\n");
        }

        if (headStart != null && headEnd != null && !headStart.equals(headEnd)) {
            md.append("## 🌿 Commits da execução\n\n```text\n")
              .append(String.join("\n", gitLog(headStart, headEnd))).append("\n```\n\n");
        }

        md.append("---\n\n*Gerado por `ArchHook.java audit` · `")
          .append(AUDIT_DIR).append("/`*\n");

        String stamp = orEmpty(startIso).replace(':', '-');
        int dot = stamp.indexOf('.');
        if (dot > 0) stamp = stamp.substring(0, dot);
        Path report = dir.resolve(stamp + "--" + skill + ".md");
        Files.writeString(report, md.toString(), StandardCharsets.UTF_8);

        if (closing) {
            String failures = String.valueOf(fails.values().stream().mapToInt(Integer::intValue).sum());
            Files.writeString(dir.resolve("history.jsonl"),
                    ev("run", "skill", skill, "kind", kind, "origin", origin, "start", startIso,
                       // The model is what the report's header showed and the ledger did
                       // not: two runs of the same pipeline, one on Sonnet and one on Opus,
                       // differ tenfold in cost, and the comparison had to be rebuilt by
                       // hand from the reports (lessons-learned-012, header note). A
                       // comma-separated list when a subagent ran on another model.
                       "model", all.model(),
                       "duration_ms", String.valueOf(total),
                       "wait_ms", String.valueOf(wait),
                       "status", status,
                       "tokens_billable", String.valueOf(all.billable()),
                       "tokens_self", String.valueOf(rootSelf.billable()),
                       "cost", cost,
                       "cost_usd", usd(auditUsd(dir, all)),
                       "cost_self_usd", usd(auditUsd(dir, rootSelf)),
                       "files", String.valueOf(touched.size()),
                       "failures", failures,
                       "report", relative(report)) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            // One line per nested piece of this project, in a ledger of its own: the run
            // ledger keeps one line per run, so what reads it does not pay for the finer grain.
            StringBuilder rows = new StringBuilder();
            String run = relative(report);
            for (String p : rootPreloaded) rows.append(nodeRow(run, skill, "skill", p, "preloaded", null, dir, 0)).append('\n');
            for (int i = 0; i < nodes.size(); i++) {
                Node nd = nodes.get(i);
                if (!isAudited(nd.kind(), nd.name())) continue;
                String parent = skill;
                if (nd.inAgent() != null) {
                    String tu = toolUseIdOfAgentId.get(nd.inAgent());
                    for (Node o : nodes) if (tu != null && tu.equals(o.toolUseId())) parent = o.name();
                }
                rows.append(nodeRow(run, parent, nd.kind(), nd.name(), "nested", selfOf.get(i), dir,
                        dur(nd, nodes, endMs, ticks, waits))).append('\n');
                if ("agent".equals(nd.kind())) {
                    for (String p : preloadedSkills(nd.name())) {
                        rows.append(nodeRow(run, nd.name(), "skill", p, "preloaded", null, dir, 0)).append('\n');
                    }
                }
            }
            if (rows.length() > 0) {
                Files.writeString(dir.resolve("nodes.jsonl"), rows.toString(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            Files.deleteIfExists(log);
        }
    }

    /** A `nodes.jsonl` line. No usage means "counted in its agent" — the field is left out, never zero. */
    static String nodeRow(String run, String parent, String kind, String name, String origin,
                          Usage u, Path dir, long durationMs) {
        return ev("node", "run", run, "parent", parent, "kind", kind, "skill", name, "origin", origin,
                "model", u == null ? null : u.model(),
                "tokens_self", u == null ? null : String.valueOf(u.billable()),
                "cost_usd", u == null ? null : usd(auditUsd(dir, u)),
                "duration_ms", u == null ? null : String.valueOf(durationMs));
    }

    /**
     * Skills an agent loads through its `skills:` frontmatter. No tool call exists for them,
     * so no hook sees them load — the frontmatter is the only record, and it is data.
     */
    static List<String> preloadedSkills(String agent) {
        if (!isAuditedAgent(agent)) return List.of();
        String c = readOrNull(ROOT.resolve(".claude/agents").resolve(agent + ".md"));
        Map<String, String> fm = c == null ? null : frontmatter(c);
        if (fm == null) return List.of();
        List<String> out = new ArrayList<>();
        String inline = fm.get("skills");
        if (inline != null && !inline.isBlank()) {
            for (String s : inline.replaceAll("[\\[\\]\"']", "").split("[,\\s]+")) {
                if (!s.isBlank()) out.add(s.strip());
            }
            return out;
        }
        boolean inside = false;
        for (String raw : c.lines().collect(Collectors.toList())) {
            if (raw.startsWith("skills:")) { inside = true; continue; }
            if (!inside) continue;
            String l = raw.strip();
            if (!l.startsWith("- ")) break;
            out.add(l.substring(2).strip().replaceAll("^[\"']|[\"']$", ""));
        }
        return out;
    }

    /**
     * From this node's start to its own last event, not to the next node's start. The
     * window closes at the next node at the same depth or shallower; events inside it
     * belong to this node, and the gap after the last of them does not. A node with no
     * event of its own falls back to the whole window. AskUserQuestion waits inside the
     * span are subtracted.
     */
    static long dur(Node n, List<Node> all, long endMs, List<Long> ticks, List<long[]> waits) {
        long boundary = endMs;
        for (Node o : all) {
            if (o.start() > n.start() && o.depth() <= n.depth()) { boundary = o.start(); break; }
        }
        long last = n.start();
        for (long t : ticks) {
            boolean inside = t > n.start() && (t < boundary || boundary == endMs && t <= endMs);
            if (inside) last = Math.max(last, t);
        }
        long end = last > n.start() ? last : boundary;
        return Math.max(0, end - n.start() - waited(n.start(), end, waits));
    }

    /** Milliseconds of [from, to] covered by the wait intervals. */
    static long waited(long from, long to, List<long[]> waits) {
        long sum = 0;
        for (long[] w : waits) sum += Math.max(0, Math.min(to, w[1]) - Math.max(from, w[0]));
        return sum;
    }

    /**
     * Assistant messages with `usage` from a transcript — the only place the runtime
     * writes real token counts. The runtime writes one line per content block, each
     * repeating the message's `usage`: lines are deduplicated by `message.id`, or a
     * message with text and two tool calls counts three times. The line layout is
     * observed, not documented — best-effort; a missing file reads as no usage, never as
     * an error.
     */
    static List<Turn> usageTurns(Path p) {
        if (p == null || !Files.isRegularFile(p)) return List.of();
        Map<String, Turn> byId = new LinkedHashMap<>();
        int anon = 0;
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                // Transcripts run to megabytes; most lines carry no usage and are not worth parsing.
                if (!line.contains("\"usage\"")) continue;
                Object e = Json.parse(line);
                Map<String, Object> u = asMap(get(e, "message", "usage"));
                if (u == null) continue;
                long[] v = {num(u.get("input_tokens")), num(u.get("output_tokens")),
                            num(u.get("cache_read_input_tokens")), num(u.get("cache_creation_input_tokens"))};
                if (v[0] + v[1] + v[2] + v[3] == 0) continue;
                String id = asStr(get(e, "message", "id"));
                byId.put(id != null ? id : "#" + anon++,
                        new Turn(epochMs(asStr(get(e, "timestamp"))), asStr(get(e, "message", "model")), v));
            }
        } catch (IOException ignored) { }
        return new ArrayList<>(byId.values());
    }

    static Usage usage(Path transcript) {
        Usage u = new Usage();
        for (Turn t : usageTurns(transcript)) u.add(t.model(), t.u());
        return u;
    }

    /** Parsed, not compared as text: `…12Z` sorts after `…12.5Z` as a string. */
    static long epochMs(String iso) {
        try { return Instant.parse(iso).toEpochMilli(); } catch (Exception e) { return 0L; }
    }

    /**
     * Agent tool call → that subagent's own transcript. Observed layout, not documented:
     * `<session>.jsonl` sits next to `<session>/subagents/agent-<agentId>.jsonl`, and a
     * sibling `.meta.json` carries the `toolUseId` of the call that spawned it — the same
     * `tool_use_id` PreToolUse delivered.
     */
    static Map<String, Path> subagentTranscripts(String transcript) {
        Map<String, Path> out = new HashMap<>();
        if (transcript == null || !transcript.endsWith(".jsonl")) return out;
        Path sub = Paths.get(transcript.substring(0, transcript.length() - ".jsonl".length()))
                .resolve("subagents");
        if (!Files.isDirectory(sub)) return out;
        try (Stream<Path> s = Files.list(sub)) {
            for (Path meta : s.filter(f -> f.toString().endsWith(".meta.json")).collect(Collectors.toList())) {
                String id = asStr(get(Json.parse(readOrNull(meta)), "toolUseId"));
                String fn = meta.getFileName().toString();
                if (id != null) {
                    out.put(id, meta.resolveSibling(fn.substring(0, fn.length() - ".meta.json".length()) + ".jsonl"));
                }
            }
        } catch (IOException ignored) { }
        return out;
    }

    static String agentIdOf(Path subagentTranscript) {
        String fn = subagentTranscript.getFileName().toString();
        return fn.replaceFirst("^agent-", "").replaceFirst("\\.jsonl$", "");
    }

    /**
     * Prices are data, never memory — the same discipline invariant 8 imposes on Java
     * and Spring versions. pricing.json ships pre-filled from the official pricing page
     * as of the date in its own $comment, but a model added later, or a price that has
     * since changed, is null until someone re-checks it: an unfilled price prints as
     * "não configurado", never as a confident US$ 0.00. Any model with usage and no
     * price makes the whole amount unknown, not a partial sum.
     */
    static Double auditUsd(Path dir, Usage u) {
        Map<String, Object> pr = asMap(Json.parse(readOrNull(dir.resolve("pricing.json"))));
        if (pr == null || u == null || u.byModel.isEmpty()) return null;
        Double per = dbl(pr.get("per"));
        double unit = per == null || per == 0 ? 1_000_000d : per;
        double usd = 0;
        for (Map.Entry<String, long[]> e : u.byModel.entrySet()) {
            Map<String, Object> m = asMap(get(pr, "models", e.getKey()));
            if (m == null) return null;
            Double in = dbl(m.get("input")), out = dbl(m.get("output")),
                   cr = dbl(m.get("cache_read")), cw = dbl(m.get("cache_write"));
            if (in == null || out == null || cr == null || cw == null) return null;
            long[] a = e.getValue();
            usd += (a[0] * in + a[1] * out + a[2] * cr + a[3] * cw) / unit;
        }
        return usd;
    }

    static String auditCost(Path dir, Usage u) { return money(dir, auditUsd(dir, u)); }

    static String money(Path dir, Double amount) {
        if (amount == null) return null;
        String cur = asStr(get(Json.parse(readOrNull(dir.resolve("pricing.json"))), "currency"));
        return String.format(PT, "%s %.2f", cur == null ? "USD" : cur, amount);
    }

    /** Machine form for the ledgers: dot decimal, no currency, so a reader can sum it. */
    static String usd(Double amount) {
        return amount == null ? null : String.format(Locale.ROOT, "%.6f", amount);
    }

    // ── audit summary ────────────────────────────────────────────────────────
    //
    // The consolidated view `/audit-usage` injects. Aggregation runs here and not in the
    // model: the ledger grows without bound, and injecting it raw costs tokens on every
    // read and leaves the arithmetic to the piece most likely to get it wrong. Output is
    // bounded — 15 runs, one line per piece — whatever the ledger's size.

    static void auditSummary(Path dir) throws IOException {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        List<Map<String, Object>> runs = jsonl(dir.resolve("history.jsonl"));
        List<Map<String, Object>> nested = jsonl(dir.resolve("nodes.jsonl"));
        StringBuilder o = new StringBuilder();

        Set<String> closed = new HashSet<>();
        for (Map<String, Object> r : runs) closed.add(asStr(r.get("report")));
        List<String> open = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            // Reports only: `<timestamp>--<piece>.md`. GENESIS.md and anything a person drops here are not runs.
            s.filter(f -> f.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}T.*--.+\\.md"))
             .map(ArchHook::relative).filter(f -> !closed.contains(f))
             .sorted(Comparator.reverseOrder()).forEach(open::add);
        }

        if (runs.isEmpty()) {
            o.append("execuções fechadas: 0 — `history.jsonl` ausente ou vazio\n");
        } else {
            String first = orDash(asStr(runs.get(0).get("start")));
            String last  = orDash(asStr(runs.get(runs.size() - 1).get("start")));

            // ── spend per piece: root self + nested self, never the run total twice ──
            Map<String, long[]> tok = new LinkedHashMap<>();      // tokens, roots, nested, preloaded
            Map<String, Double> money = new LinkedHashMap<>();
            Map<String, Integer> unpriced = new LinkedHashMap<>();
            Map<String, int[]> health = new LinkedHashMap<>();    // failed, runs — roots only
            long sumTok = 0, sumDur = 0;
            double sumCost = 0;
            int priced = 0, failed = 0;
            for (Map<String, Object> r : runs) {
                String key = pieceKey(r);
                boolean hasSelf = r.get("tokens_self") != null;
                long self = lnum(r.get(hasSelf ? "tokens_self" : "tokens_billable"));
                long[] t = tok.computeIfAbsent(key, k -> new long[4]);
                t[0] += self; t[1]++;
                Double c = hasSelf ? ldbl(r.get("cost_self_usd")) : runCost(r);
                if (c == null) unpriced.merge(key, 1, Integer::sum); else money.merge(key, c, Double::sum);
                sumTok += lnum(r.get("tokens_billable"));
                sumDur += lnum(r.get("duration_ms"));
                Double total = runCost(r);
                if (total != null) { sumCost += total; priced++; }
                boolean bad = lnum(r.get("failures")) > 0 || orEmpty(asStr(r.get("status"))).startsWith("❌");
                if (bad) failed++;
                int[] h = health.computeIfAbsent(key, k -> new int[2]);
                if (bad) h[0]++;
                h[1]++;
            }
            for (Map<String, Object> r : nested) {
                String key = pieceKey(r);
                long[] t = tok.computeIfAbsent(key, k -> new long[4]);
                if ("preloaded".equals(asStr(r.get("origin")))) { t[3]++; continue; }
                t[2]++;
                if (r.get("tokens_self") == null) continue;
                t[0] += lnum(r.get("tokens_self"));
                Double c = ldbl(r.get("cost_usd"));
                if (c == null) unpriced.merge(key, 1, Integer::sum); else money.merge(key, c, Double::sum);
            }

            o.append("execuções fechadas: ").append(runs.size())
             .append(" · período: ").append(first).append(" → ").append(last)
             .append(" · peças distintas: ").append(tok.size()).append("\n\n");

            o.append("### Últimas execuções\n\n")
             .append("| # | 🕐 Quando | 🎯 Peça | 🙋 Origem | 🤖 Modelo | Status | ⏱️ Duração | 🧮 Faturável | 💰 Custo | 📁 Arq. | 🔁 Falhas |\n")
             .append("|---|---|---|---|---|---|---|---|---|---|---|\n");
            int shown = 0;
            for (int i = runs.size() - 1; i >= 0 && shown < 15; i--, shown++) {
                Map<String, Object> r = runs.get(i);
                String st = orEmpty(asStr(r.get("status")));
                o.append("| ").append(shown + 1).append(" | ").append(orDash(asStr(r.get("start"))))
                 .append(" | `").append(pieceLabel(r)).append("` | ")
                 .append("model".equals(asStr(r.get("origin"))) ? "modelo" : "usuário").append(" | ")
                 // Absent in every row written before this column existed: a run recorded
                 // by an older hook shows `—`, not a model it never knew.
                 .append(orDash(asStr(r.get("model")))).append(" | ")
                 .append(st.isEmpty() ? "—" : st.substring(0, Math.max(1, st.indexOf(' ')))).append(" | ")
                 .append(hms(lnum(r.get("duration_ms")))).append(" | ")
                 .append(n(lnum(r.get("tokens_billable")))).append(" | ")
                 .append(orDash(money(dir, runCost(r)))).append(" | ")
                 .append(lnum(r.get("files"))).append(" | ").append(lnum(r.get("failures"))).append(" |\n");
            }
            if (runs.size() > 15) o.append("\n… mais ").append(runs.size() - 15).append(" execuções\n");

            o.append("\n### Totais\n\n")
             .append("faturável: ").append(n(sumTok)).append(" tok · custo: ")
             .append(priced == 0 ? "não configurado" : money(dir, sumCost))
             .append(" (").append(priced).append(" de ").append(runs.size()).append(" execuções com custo")
             .append(priced < runs.size() ? "; " + (runs.size() - priced) + " fora da soma, sem preço" : "")
             .append(") · duração ativa: ").append(hms(sumDur))
             .append(" · execuções: ").append(runs.size()).append("\n\n");

            long pieceSum = Math.max(1, tok.values().stream().mapToLong(a -> a[0]).sum());
            List<Map.Entry<String, long[]>> ranked = new ArrayList<>(tok.entrySet());
            ranked.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
            o.append("### Gasto por peça (tokens próprios — raiz + aninhada, sem dupla contagem)\n\n```text\n");
            for (Map.Entry<String, long[]> e : ranked) {
                long[] t = e.getValue();
                Double c = money.get(e.getKey());
                int miss = unpriced.getOrDefault(e.getKey(), 0);
                String calls = t[1] + t[2] == 0 ? t[3] + "× pré-carregada"
                        : (t[1] + t[2]) + "×"
                          + (t[2] > 0 ? " (" + t[1] + " raiz · " + t[2] + " aninhada)" : "")
                          + (t[3] > 0 ? " · " + t[3] + "× pré-carregada" : "");
                o.append(pad(keyLabel(e.getKey()), 34)).append(barW((double) t[0] / pieceSum, 25)).append(' ')
                 .append(pad(pct(t[0], pieceSum), 5)).append(pad(n(t[0]) + " tok", 16))
                 .append(pad(c == null ? (miss > 0 ? "sem preço" : "—") : money(dir, c) + (miss > 0 ? "*" : ""), 14))
                 .append(calls).append('\n');
            }
            o.append("```\n");
            if (unpriced.values().stream().anyMatch(v -> v > 0)) {
                o.append("`*` soma parcial: há invocações sem preço configurado.\n");
            }

            o.append("\n### Saúde\n\n").append("taxa de falha: ").append(failed).append('/')
             .append(runs.size()).append(" (").append(pct(failed, runs.size())).append(")");
            health.entrySet().stream().filter(e -> e.getValue()[0] > 0)
                  .max(Comparator.comparingDouble(e -> (double) e.getValue()[0] / e.getValue()[1]))
                  .ifPresent(e -> o.append(" · pior: ").append(keyLabel(e.getKey())).append(" (")
                          .append(e.getValue()[0]).append('/').append(e.getValue()[1]).append(')'));
            o.append("\n\n### Abrir\n\n");
            Map<String, Object> newest = runs.get(runs.size() - 1);
            o.append("mais recente: `").append(asStr(newest.get("report"))).append("`\n");
            for (int i = runs.size() - 1; i >= 0; i--) {
                Map<String, Object> r = runs.get(i);
                if (lnum(r.get("failures")) > 0 || orEmpty(asStr(r.get("status"))).startsWith("❌")) {
                    o.append("mais recente com falha: `").append(asStr(r.get("report"))).append("`\n");
                    break;
                }
            }
        }

        if (!open.isEmpty()) {
            o.append("\n### ⏳ Sem linha no ledger (em andamento ou sessão morta)\n\n");
            open.stream().limit(5).forEach(f -> o.append("- `").append(f).append("`\n"));
            if (open.size() > 5) o.append("- … mais ").append(open.size() - 5).append('\n');
        }
        out.print(o);
    }

    static List<Map<String, Object>> jsonl(Path p) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isRegularFile(p)) return out;
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            Map<String, Object> m = asMap(Json.parse(line));
            if (m != null) out.add(m);
        }
        return out;
    }

    /** Lines written before this field existed are skills — only skills were audited then. */
    static String pieceKey(Map<String, Object> row) {
        return ("agent".equals(asStr(row.get("kind"))) ? "agent" : "skill") + ":" + orDash(asStr(row.get("skill")));
    }

    static String keyLabel(String key) {
        return (key.startsWith("agent:") ? "🤖 " : "📘 ") + key.substring(key.indexOf(':') + 1);
    }

    static String pieceLabel(Map<String, Object> row) {
        String name = orDash(asStr(row.get("skill")));
        return "agent".equals(asStr(row.get("kind"))) ? "🤖 " + name
                : "model".equals(asStr(row.get("origin"))) ? "Skill(" + name + ")" : "/" + name;
    }

    /** `cost_usd` when present; older lines only carry the formatted `cost` ("USD 18,44"). */
    static Double runCost(Map<String, Object> row) {
        Double d = ldbl(row.get("cost_usd"));
        if (d != null) return d;
        String c = asStr(row.get("cost"));
        if (c == null) return null;
        String v = c.substring(c.lastIndexOf(' ') + 1).replace(".", "").replace(',', '.');
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return null; }
    }

    /** The ledgers write every value as a string. */
    static long lnum(Object o) {
        if (o instanceof Number x) return x.longValue();
        try { return o instanceof String s ? Long.parseLong(s.strip()) : 0L; }
        catch (NumberFormatException e) { return 0L; }
    }

    static Double ldbl(Object o) {
        if (o instanceof Number x) return x.doubleValue();
        try { return o instanceof String s ? Double.parseDouble(s.strip()) : null; }
        catch (NumberFormatException e) { return null; }
    }

    static String barW(double fraction, int width) {
        int full = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
        return "█".repeat(full) + "░".repeat(width - full);
    }

    /** One rule whose `paths` captured at least one file touched during the run. */
    record RuleHit(String rule, String glob, List<String> files) {}

    /**
     * Rules whose `paths` capture at least one file touched during the run. A record
     * keyed by nothing beats a string key split back apart at render time: the previous
     * "file glob" key joined the two with a literal NUL byte as separator, invisible in
     * an editor, and any `split(" ", 2)` on a rule or glob containing a real space threw
     * `ArrayIndexOutOfBoundsException` the moment a touched file actually matched one --
     * silently, since the hook's own top-level catch swallows it and exits 0. That froze
     * every report the instant a run touched `src/**` for good, `flush` and `close`
     * included: see `.claude/decisions/0041-audit-background-subagent-tracking.md`.
     */
    static List<RuleHit> auditRules(Set<String> touched) throws IOException {
        List<RuleHit> hits = new ArrayList<>();
        Path rules = ROOT.resolve(".claude/rules");
        if (!Files.isDirectory(rules) || touched.isEmpty()) return hits;
        try (Stream<Path> s = Files.list(rules)) {
            List<Path> files = s.filter(f -> f.toString().endsWith(".md")).sorted()
                    .collect(Collectors.toList());
            for (Path f : files) {
                for (String g : ruleGlobs(f)) {
                    Pattern re = glob(g);
                    List<String> matched = touched.stream()
                            .filter(t -> re.matcher(t).matches()).sorted()
                            .collect(Collectors.toList());
                    if (!matched.isEmpty()) {
                        hits.add(new RuleHit(f.getFileName().toString(), g, matched));
                    }
                }
            }
        }
        return hits;
    }

    /**
     * The `paths` of a rule. `frontmatter()` reads unindented scalars only, and this
     * repo writes `paths` as an indented block list — so the block is read here.
     */
    static List<String> ruleGlobs(Path file) {
        String c = readOrNull(file);
        if (c == null) return List.of();
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (String raw : c.lines().collect(Collectors.toList())) {
            String l = raw.strip();
            if (raw.startsWith("paths:")) { inside = true; continue; }
            if (!inside) continue;
            if (l.startsWith("#")) continue;
            if (!l.startsWith("- ")) break;
            out.add(l.substring(2).strip().replaceAll("^[\"']|[\"']$", ""));
        }
        return out;
    }

    static List<String> gitLog(String from, String to) {
        try {
            Proc p = run(gitCmd(), "log", "--oneline", from + ".." + to);
            return p.exit == 0 && !p.out.isEmpty() ? p.out : List.of("—");
        } catch (Exception e) { return List.of("—"); }
    }

    static String bar(double fraction) {
        int width = 20;
        int full = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
        return "█".repeat(full) + "░".repeat(width - full);
    }

    static String hms(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%dh%02dm%02ds", s / 3600, (s % 3600) / 60, s % 60)
             : s >= 60   ? String.format(Locale.ROOT, "%dm%02ds", s / 60, s % 60)
                         : s + "s";
    }

    static String pct(long part, long whole) {
        return whole <= 0 ? "0%" : Math.round(100.0 * part / whole) + "%";
    }

    static String n(long v) { return String.format(PT, "%,d", v); }

    static String pad(String s, int width) {
        return s.length() >= width ? s.substring(0, width - 1) + " "
                                   : s + " ".repeat(width - s.length());
    }

    static long num(Object o) { return o instanceof Number x ? x.longValue() : 0L; }

    static Double dbl(Object o) { return o instanceof Number x ? x.doubleValue() : null; }

    static String orDash(String s) { return s == null || s.isBlank() ? "—" : s; }

    static String orEmpty(String s) { return s == null ? "" : s; }

    // ── guard ────────────────────────────────────────────────────────────────
    //
    // Three boundaries the pipeline broke in real runs, while its skills said the opposite
    // in prose:
    //   1. a design skill wrote a migration under src/ — src/ belongs to the executor;
    //   2. a later use case edited the specs of an earlier, already-decided one;
    //   3. a design run wrote a service block into docker-compose.yml — which the denylist
    //      this mode used to carry (`src/**`) could not see, because the leaked file is
    //      never the one somebody thought to forbid. Hence the allowlist below.
    //
    // Phases (args[1]):
    //   prompt   UserPromptSubmit         a prompt ends any phase; `/<skill>` opens one
    //   call     PreToolUse Skill|Agent   a skill opens the phase; an executor agent closes
    //                                     it; a `blocked_during_design` class is refused
    //   write    PreToolUse Write|Edit    blocks (exit 2) what the three boundaries forbid
    //
    // The phase is one file per session in the OS temp dir — never in the project, so it
    // can't be committed. It holds the active skill's NAME; the class, its territory and
    // which agents execute are data (`skill_classes` and `guard` in extensions.json —
    // invariants 7, 10). Territory is deny-by-default: while a phase is open, a write is
    // allowed only where the active skill's `write_allow` says so. No phase open means no
    // restriction — a person editing a file by hand is not a skill overstepping.
    //
    // Known gap, accepted: a design skill that asks in plain text instead of
    // AskUserQuestion gets its answer as a prompt, and that prompt ends the phase.
    //
    // Known gap, accepted: a `Skill`(design) call and an `Agent`(executor) call fired in
    // the SAME turn race — two independent PreToolUse invocations, no ordering guarantee
    // between them. If the Skill's open lands after the Agent's close, the phase ends up
    // open and blocks the executor's own writes even though `agent_type` would have let
    // them through (guardWrite checks `agent_type` first, unconditionally — see below;
    // that's the real fix whenever it applies, this is only the residual race around it).
    // lessons-learned-006 § 1. No file-level fix: nothing here can order two separate
    // hook processes. The workaround is procedural — orchestrators MUST NOT fire
    // `Skill(<a design_phase class>)` and `Agent(<executor_agents>)` in the same message; do the
    // Skill call, wait for its turn to end, then the Agent call in a separate turn.
    // `new-feature/SKILL.md`'s executor-offer pre-flight is the one place in this repo
    // that can trigger both in one branch — it documents the same rule inline.

    static void guard(String phase, String stdin) throws Exception {
        Map<String, Object> sch = asMap(Json.parse(readOrNull(ROOT.resolve(SCHEMA_FILE))));
        if (sch == null || (sch.get("guard") == null && sch.get("skill_classes") == null)) return;
        Object in = Json.parse(stdin);
        Path state = guardState(asStr(get(in, "session_id")));
        switch (phase) {
            case "prompt" -> guardPrompt(sch, state, in);
            case "call"   -> guardCall(sch, state, in);
            case "write"  -> guardWrite(sch, state, in);
            default       -> { }
        }
    }

    static Path guardState(String session) {
        String s = session == null || session.isBlank() ? "unknown"
                : session.replaceAll("[^A-Za-z0-9_-]", "_");
        return Paths.get(System.getProperty("java.io.tmpdir"), "archhook-guard", s);
    }

    static void guardPrompt(Map<String, Object> sch, Path state, Object in) throws IOException {
        Files.deleteIfExists(state);
        Matcher m = Pattern.compile("^\\s*/([a-z0-9][a-z0-9-]*)").matcher(orEmpty(asStr(get(in, "prompt"))));
        if (m.find() && skillClassOf(sch, m.group(1)) != null) guardOpen(state, m.group(1));
    }

    static void guardCall(Map<String, Object> sch, Path state, Object in) throws IOException {
        String tool = asStr(get(in, "tool_name"));
        if ("Skill".equals(tool)) {
            String skill = asStr(get(in, "tool_input", "skill"));
            String cls = skillClassOf(sch, skill);
            if (cls == null) return;                       // plugin skill: not our territory
            guardRefuseBuildCall(sch, state, skill, cls);
            // A phase is never replaced by a skill of its own class. Two skills of one class
            // share a territory, so narrowing to the callee buys nothing — and it would
            // silently shrink the caller's territory for the rest of the turn, which is what
            // happens when project-bootstrap chains docker-architect in its step 4.10 and
            // then keeps writing src/.
            if (Files.isRegularFile(state) && cls.equals(skillClassOf(sch, readOrNull(state)))) {
                return;
            }
            guardOpen(state, skill);
        } else if ("Agent".equals(tool) || "Task".equals(tool)) {
            String agent = asStr(get(in, "tool_input", "subagent_type"));
            if (asStrList(get(sch, "guard", "executor_agents")).contains(agent)) {
                Files.deleteIfExists(state);
            }
        }
    }

    /**
     * A `blocked_during_design` class cannot be reached from inside an open `design_phase`
     * one. This is what makes the design pipeline docs-only in the mechanism rather than in
     * prose: `docker-architect` owns docker-compose.yml, and a `/new-feature` run reaches it
     * by reporting the missing service, not by writing the file mid-design.
     */
    static void guardRefuseBuildCall(Map<String, Object> sch, Path state, String skill, String cls)
            throws IOException {
        if (!Boolean.TRUE.equals(get(sch, "skill_classes", "classes", cls, "blocked_during_design"))) return;
        if (!Files.isRegularFile(state)) return;
        String active = readOrNull(state);
        String activeCls = skillClassOf(sch, active);
        if (activeCls == null
                || !Boolean.TRUE.equals(get(sch, "skill_classes", "classes", activeCls, "design_phase"))) {
            return;
        }
        err("❌ `" + skill + "` is class `" + cls + "` and `" + active + "` (class `"
                + activeCls + "`) is open — a design run does not materialize files.");
        err("Record what is missing in the partial and in the consolidated spec, finish the run,");
        err("then invoke `/" + skill + "` from a prompt of its own.");
        System.exit(2);
    }

    static void guardOpen(Path state, String skill) throws IOException {
        Files.createDirectories(state.getParent());
        Files.writeString(state, skill, StandardCharsets.UTF_8);
    }

    static void guardWrite(Map<String, Object> sch, Path state, Object in) throws IOException {
        String file = asStr(get(in, "tool_input", "file_path"));
        if (file == null) return;
        String rel = relative(Paths.get(file));
        Map<String, Object> cfg = asMap(sch.get("guard"));

        // 1. A phase is open: the path must be in the active skill's territory. Deny by
        //    default. `agent_type` is only present when the call comes from a subagent, and
        //    an executor agent writes whatever it was delegated.
        String agent = asStr(get(in, "agent_type"));
        boolean executor = agent != null
                && asStrList(get(sch, "guard", "executor_agents")).contains(agent);
        if (Files.isRegularFile(state) && !executor) {
            String active = readOrNull(state);
            String cls = skillClassOf(sch, active);
            List<String> allow = writeAllowOf(sch, active);
            if (cls != null && !matchesAny(allow, rel)) {
                err("❌ `" + active + "` is class `" + cls + "` — " + rel
                        + " is outside its territory.");
                err("   write_allow: " + (allow.isEmpty() ? "(nothing — this class writes no file)"
                        : String.join(", ", allow)));
                err("Put the content in the spec as a code block, or finish the run and invoke the");
                err("skill that owns this path. If the path is legitimately this skill's, widen");
                err("skill_classes." + cls + " in " + SCHEMA_FILE + " — retrying will not help.");
                System.exit(2);
            }
        }

        // 2. A folder whose consolidated spec is approved or implemented is frozen.
        if (cfg == null) return;
        Matcher m = Pattern.compile("^" + Pattern.quote(orEmpty(asStr(cfg.get("use_cases_dir")))) + "/(UC-[^/]+)/")
                .matcher(rel);
        if (!m.find()) return;
        Path folder = ROOT.resolve(asStr(cfg.get("use_cases_dir"))).resolve(m.group(1));
        String status = specStatus(folder);
        if (status == null || !asStrList(cfg.get("frozen_statuses")).contains(status)) return;
        if (isStatusClose(in, rel, status) || isChecklistToggle(in, rel, status)) return;
        err("❌ " + m.group(1) + " is " + status + " — its specs are immutable.");
        err("Record the change in the new use case's \"Impact on approved use cases\" section.");
        err("To reopen a spec that was never implemented, set `status: draft` by hand.");
        System.exit(2);
    }

    /** `status:` of the folder's UC-*-spec.md, or null when there's no consolidated spec. */
    static String specStatus(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return null;
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : s.filter(f -> f.getFileName().toString().matches("UC-.*-spec\\.md"))
                           .collect(Collectors.toList())) {
                Map<String, String> fm = frontmatter(orEmpty(readOrNull(p)));
                if (fm != null && fm.get("status") != null) return fm.get("status");
            }
        }
        return null;
    }

    /** The one edit an approved spec admits: its status line, approved → implemented. */
    static boolean isStatusClose(Object in, String rel, String status) {
        if (!"approved".equals(status) || !rel.matches(".*/UC-[^/]*-spec\\.md")) return false;
        if (!"Edit".equals(asStr(get(in, "tool_name")))) return false;
        String oldS = asStr(get(in, "tool_input", "old_string"));
        String newS = asStr(get(in, "tool_input", "new_string"));
        return oldS != null && newS != null && oldS.contains("status: approved")
                && newS.equals(oldS.replace("status: approved", "status: implemented"));
    }

    /**
     * The other edit an `approved` spec admits: toggling `- [ ]` to `- [x]` in its
     * implementation checklist, incremental progress that resuming the executor across
     * sessions depends on (lessons-learned-006 § 7). `[ ]` and `[x]` are the same length,
     * so normalizing both to `[ ]` and comparing catches any number of toggles in one
     * `Edit` while still rejecting a change to anything else in the snippet.
     */
    static boolean isChecklistToggle(Object in, String rel, String status) {
        if (!"approved".equals(status) || !rel.matches(".*/UC-[^/]*-spec\\.md")) return false;
        if (!"Edit".equals(asStr(get(in, "tool_name")))) return false;
        String oldS = asStr(get(in, "tool_input", "old_string"));
        String newS = asStr(get(in, "tool_input", "new_string"));
        if (oldS == null || newS == null || oldS.length() != newS.length()) return false;
        String oldNorm = oldS.replace("[x]", "[ ]");
        String newNorm = newS.replace("[x]", "[ ]");
        return oldNorm.equals(newNorm) && !oldS.equals(newS);
    }

    // ── utilities ────────────────────────────────────────────────────────────

    /** Extracts tool_input.file_path from the stdin JSON without an external library. */
    static String filePath(String json) {
        Matcher m = Pattern.compile("\"file_path\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json);
        if (!m.find()) return null;
        String raw = m.group(1)
                .replace("\\\\", "\\").replace("\\\"", "\"").replace("\\/", "/");
        return raw;   // Windows arrives with '\\'; Paths.get handles both separators
    }

    /** Path of the Maven module containing the file, or null if there is no POM. */
    static String moduleOf(String rel) {
        Path p = Paths.get(rel).getParent();
        while (p != null) {
            if (Files.isRegularFile(ROOT.resolve(p).resolve("pom.xml"))) {
                String s = p.toString().replace('\\', '/');
                return s.isEmpty() ? "." : s;
            }
            p = p.getParent();
        }
        return null;
    }

    /** The right wrapper for this operating system — the same problem Maven already solved. */
    static String wrapper() {
        Path w = ROOT.resolve(WINDOWS ? "mvnw.cmd" : "mvnw");
        return Files.isRegularFile(w) ? w.toString() : null;
    }

    static String gitCmd() { return "git"; }

    static String relative(Path abs) {
        try { return ROOT.relativize(abs.toAbsolutePath().normalize())
                        .toString().replace('\\', '/'); }
        catch (Exception e) { return abs.toString().replace('\\', '/'); }
    }

    record Proc(int exit, List<String> out) {}

    static Proc run(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(ROOT.toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        List<String> out;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            out = r.lines().collect(Collectors.toList());
        }
        return new Proc(proc.waitFor(), out);
    }

    /**
     * {@link #run} with a wall clock. For commands that can block indefinitely on
     * something outside this process — a Docker daemon that is starting, hibernating, or
     * unreachable. A diagnostic that hangs is worse than one that says "not checked":
     * `doctor` is what someone runs when the session already feels broken.
     * Returns exit -1 when the deadline passes, and the partial output.
     */
    static Proc runTimed(int seconds, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(ROOT.toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        List<String> out = new ArrayList<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) out.add(l);
            } catch (IOException ignored) { }
        });
        reader.setDaemon(true);
        reader.start();
        if (!proc.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            return new Proc(-1, List.copyOf(out));
        }
        reader.join(1000);
        return new Proc(proc.exitValue(), List.copyOf(out));
    }

    static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    static List<String> tail(List<String> lines, int n) {
        return lines.subList(Math.max(0, lines.size() - n), lines.size());
    }

    static void err(String s) { ERR.println(s); }

    // ── access to already-parsed JSON ───────────────────────────────────────

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    static String asStr(Object o) { return o instanceof String ? (String) o : null; }

    static List<String> asStrList(Object o) {
        return asList(o).stream().map(ArchHook::asStr).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    static Object get(Object o, String... path) {
        for (String k : path) {
            Map<String, Object> m = asMap(o);
            if (m == null) return null;
            o = m.get(k);
        }
        return o;
    }

    // ── JSON ─────────────────────────────────────────────────────────────────
    //
    // ~50-line recursive parser. Regex over JSON breaks with escaped quotes
    // and nesting — and here we read the runtime's stdin and the user's
    // settings.json, two places where breaking silently is worse than not
    // validating. The dependency remains just the JDK.

    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        /** The top-level value, or null if the text is not valid JSON. */
        static Object parse(String text) {
            if (text == null || text.isBlank()) return null;
            try { Json j = new Json(text); j.ws(); return j.value(); }
            catch (RuntimeException e) { return null; }
        }

        private Object value() {
            ws();
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> obj();
                case '[' -> arr();
                case '"' -> str();
                case 't' -> { i += 4; yield Boolean.TRUE; }
                case 'f' -> { i += 5; yield Boolean.FALSE; }
                case 'n' -> { i += 4; yield null; }
                default  -> num();
            };
        }

        private Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = str();
                ws(); i++;                                   // ':'
                m.put(k, value());
                ws();
                if (s.charAt(i++) == '}') return m;           // otherwise it was ','
            }
        }

        private List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                if (s.charAt(i++) == ']') return l;           // otherwise it was ','
            }
        }

        private String str() {
            StringBuilder b = new StringBuilder();
            i++;
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default  -> b.append(e);                 // \" \\ \/
                }
            }
        }

        private Object num() {
            int start = i;
            while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
            return Double.valueOf(s.substring(start, i));
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}
