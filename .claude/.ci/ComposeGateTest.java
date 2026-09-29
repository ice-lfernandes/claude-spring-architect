///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves `ArchHook.jar compose gate` BLOCKS (exit 2) a compose file whose service
// publishes a port to the host while advertising only its compose-network name, stays silent
// with no compose file, and never blocks twice in one Stop.
//
// Why this test exists: the defect it gates shipped on day one — a `kafka` block publishing
// 9092:9092 next to `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092`, unreachable from
// every host client, while the healthcheck and Testcontainers both stayed green
// (lessons-learned-014 § 13, decision 0064). ComposeTagTest covers the report form; this is
// the exit code and the registration's contract, which the report form does not have.
//
// Why no Docker: the advertised-address check reads the file, and `composeReport` computes it
// before the first `docker` call. On a runner WITH Docker the declared service is also "not
// running", which blocks too — so the fixed case asserts on the advertised-address line, not
// just on exit 2, and the host-advertised variant asserts only that the line is absent.

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class ComposeGateTest {

    static final String UNREACHABLE = "no address the host can resolve";

    public static void main(String[] args) throws Exception {
        Path hook = Paths.get(".claude/hooks/ArchHook.jar").toAbsolutePath();

        String broken = """
                services:
                  kafka:
                    image: apache/kafka:3.8.0
                    ports:
                      - "9092:9092"
                    environment:
                      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
                """;
        String fixed = """
                services:
                  kafka:
                    image: apache/kafka:3.8.0
                    ports:
                      - "9092:9092"
                    environment:
                      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
                """;

        int failures = 0;
        failures += expect(hook, broken, "{}", 2, true,
                "published but advertised as kafka:9092 — blocked");
        failures += expect(hook, broken, "{\"stop_hook_active\":true}", 0, false,
                "stop_hook_active — does not block twice");
        failures += expect(hook, fixed, "{}", -1, false,
                "advertised on localhost — the address check stays quiet");
        failures += expect(hook, null, "{}", 0, false, "no compose file — silent");

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — `compose gate` is NOT"
                    + " blocking an unreachable published service.");
            System.exit(1);
        }
        System.out.println("✅ compose gate: unreachable published service blocked without Docker,"
                + " silent with nothing to check.");
    }

    /** {@code wanted} -1 means the exit code depends on the runner's Docker and is not checked. */
    static int expect(Path hook, String compose, String stdin, int wanted, boolean wantLine,
                      String label) throws Exception {
        Path tmp = Files.createTempDirectory("archhook-gate-ci");
        Path schemas = tmp.resolve(".claude/schemas");
        Files.createDirectories(schemas);
        Files.copy(Paths.get(".claude/schemas/extensions.json"), schemas.resolve("extensions.json"));
        if (compose != null) Files.writeString(tmp.resolve("docker-compose.yml"), compose);

        ProcessBuilder pb = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar", hook.toString(), "compose", "gate").directory(tmp.toFile());
        pb.environment().put("CLAUDE_PROJECT_DIR", tmp.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();

        boolean line = out.contains(UNREACHABLE);
        String why = wanted >= 0 && code != wanted ? "expected exit " + wanted + ", got " + code
                : line != wantLine ? "advertised-address line " + (line ? "present" : "absent")
                : null;
        if (why == null) {
            System.out.println("✅ " + label);
            return 0;
        }
        System.out.println("❌ " + label + " — " + why);
        System.out.println(out);
        return 1;
    }
}
