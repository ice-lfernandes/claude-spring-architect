///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves `ArchHook.java compose` REPORTS a compose `image:` tag that disagrees
// with the tag `src/test` pins for the same repository in `DockerImageName.parse(...)`,
// and stays quiet when the two agree.
//
// Why this test exists: the mismatch is silent in the only way that matters. The suite
// passes — against an engine version nobody runs. `docker-compose.yml` says
// `postgres:16-alpine`, `TestcontainersConfiguration.java` says `postgres:15`, both
// halves are green, and the first behavioural difference between the two versions shows
// up in production. Two skills used to promise the match in prose (`docker-architect`
// step 4, `test-architect`'s setup mode); @CLAUDE.md invariant 6 is why it became a hook.
//
// Why this test can run on a runner with no Docker: question 3 of the `compose` mode
// compares two files and nothing else. `composeReport` computes it before the first
// `docker` call and `withTags` carries it through every early return — no PATH, no
// daemon, no container. That property is itself asserted here: the cases below never
// look at service state, so they hold identically on all three OSes of the matrix.
//
// Assertions are over the output, not the exit code, on purpose: `compose` is a
// diagnostic that never blocks — see the mode's own comment in ArchHook.java.

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class ComposeTagTest {

    /** What the mode prints for question 3. Substring, so the `→ make both…` tail can change. */
    static final String MISMATCH = "image tag mismatch for";

    public static void main(String[] args) throws Exception {
        Path hook = Paths.get(".claude/hooks/ArchHook.java").toAbsolutePath();

        String composePostgres16 = """
                services:
                  postgres:
                    image: postgres:16-alpine
                    ports:
                      - "5432:5432"
                """;
        String composeExpanded = composePostgres16.replace(
                "image: postgres:16-alpine", "image: postgres:${POSTGRES_TAG:-16-alpine}");
        String composeRedisOnly = """
                services:
                  redis:
                    image: redis:7-alpine
                """;
        String pinsPostgres15 = testConfig("postgres:15");
        String pinsPostgres16 = testConfig("postgres:16-alpine");

        int failures = 0;
        failures += expect(hook, composePostgres16, pinsPostgres15, true,
                "tag mismatch between compose and src/test reported");
        failures += expect(hook, composePostgres16, pinsPostgres16, false,
                "matching tags report nothing");
        // `${POSTGRES_TAG:-16-alpine}` is the tag whoever runs `docker compose up` gets by
        // default, so the default is what the comparison has to use — an unexpanded
        // variable would silently opt the service out of the check.
        failures += expect(hook, composeExpanded, pinsPostgres15, true,
                "`${VAR:-default}` expanded before the comparison");
        // A repository on one side only: a compose service no test touches is legitimate,
        // and flagging it would make the check unusable.
        failures += expect(hook, composeRedisOnly, pinsPostgres15, false,
                "repository present on only one side is not a mismatch");

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — the compose/src/test tag"
                    + " comparison is NOT enforcing what it documents.");
            System.exit(1);
        }
        System.out.println("✅ Compose image tags enforced: divergent tag reported,"
                + " `${VAR:-default}` expanded, matching tags and one-sided repositories"
                + " quiet — all without Docker.");
    }

    /** A `src/test` file holding the one pin the comparison reads. */
    static String testConfig(String image) {
        return """
                package com.example.probe;

                import org.testcontainers.utility.DockerImageName;

                class TestcontainersConfiguration {
                    static final DockerImageName IMAGE = DockerImageName.parse("%s");
                }
                """.formatted(image);
    }

    /**
     * Runs `compose` over a throwaway project holding one compose file and one `src/test`
     * pin, and checks whether the mismatch line is there.
     */
    static int expect(Path hook, String compose, String testSource, boolean wantMismatch,
            String label) throws Exception {
        Path tmp = Files.createTempDirectory("archhook-compose-ci");
        Files.writeString(tmp.resolve("docker-compose.yml"), compose);
        Path testDir = tmp.resolve("src/test/java/com/example/probe");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("TestcontainersConfiguration.java"), testSource);

        ProcessBuilder pb = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                hook.toString(), "compose");
        pb.environment().put("CLAUDE_PROJECT_DIR", tmp.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().close();                 // `compose` reads no stdin
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();                                 // a diagnostic mode: exit code is not the signal

        boolean reported = out.contains(MISMATCH);
        if (reported == wantMismatch) {
            System.out.println("✅ " + label);
            return 0;
        }
        System.out.println("❌ " + label + " — expected the mismatch line "
                + (wantMismatch ? "present" : "absent") + ", it was "
                + (reported ? "present" : "absent"));
        System.out.println(out);
        return 1;
    }
}
