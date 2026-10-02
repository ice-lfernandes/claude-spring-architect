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
//
// Question 5 (decision 0110, issue #66) has the same shape and gets the same treatment: the
// OTLP collector published no host port, so `./mvnw spring-boot:run` failed every export
// while the gate stayed silent, and projects older than 0046 never set the metrics endpoint
// on `app`. Each side is a blocked case plus its fixed variant, and two cases guard the false
// positives the check must not raise: relaxed binding (`SPRING_DATASOURCE_URL` overriding
// `${DB_URL:…}`) and Kafka's host-first default on its EXTERNAL port.

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class ComposeGateTest {

    static final String UNREACHABLE = "no address the host can resolve";
    static final String HOST_RUN = "the application run on the host cannot reach it";
    static final String CONTAINER = "is the application itself";

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
        failures += expect(hook, broken, null, UNREACHABLE, "{}", 2, true,
                "published but advertised as kafka:9092 — blocked");
        failures += expect(hook, broken, null, UNREACHABLE, "{\"stop_hook_active\":true}", 0, false,
                "stop_hook_active — does not block twice");
        failures += expect(hook, fixed, null, UNREACHABLE, "{}", -1, false,
                "advertised on localhost — the address check stays quiet");
        failures += expect(hook, null, null, UNREACHABLE, "{}", 0, false, "no compose file — silent");

        String otlpYml = """
                management:
                  otlp:
                    tracing:
                      endpoint: ${OTLP_ENDPOINT:http://localhost:4318/v1/traces}
                    metrics:
                      export:
                        url: ${OTLP_METRICS_ENDPOINT:http://localhost:4318/v1/metrics}
                """;
        String bothVars = """
                      OTLP_ENDPOINT: http://otel-collector:4318/v1/traces
                      OTLP_METRICS_ENDPOINT: http://otel-collector:4318/v1/metrics
                """;
        String collectorUnpublished = """
                  otel-collector:
                    image: otel/opentelemetry-collector-contrib:0.160.0
                """;
        String collectorPublished = collectorUnpublished + """
                    ports:
                      - "${OTLP_HTTP_PORT:-4318}:4318"
                """;
        failures += expect(hook, withApp(bothVars, collectorUnpublished), otlpYml, HOST_RUN, "{}", 2,
                true, "collector publishes no port, defaults point at localhost:4318 — blocked");
        failures += expect(hook, withApp(bothVars, collectorPublished), otlpYml, HOST_RUN, "{}", -1,
                false, "collector published on a variable host port — host-run line absent");
        failures += expect(hook, withApp("      OTLP_ENDPOINT: http://otel-collector:4318/v1/traces\n",
                        collectorPublished), otlpYml, CONTAINER, "{}", 2, true,
                "app sets only OTLP_ENDPOINT — the metrics endpoint blocked");
        failures += expect(hook, withApp(bothVars, collectorPublished), otlpYml, CONTAINER, "{}", -1,
                false, "app sets both endpoints — container line absent");

        String datasourceYml = """
                spring:
                  datasource:
                    url: ${DB_URL:jdbc:postgresql://localhost:5432/app}
                """;
        String postgres = """
                  postgres:
                    image: postgres:17-alpine
                    ports:
                      - "5432:5432"
                """;
        failures += expect(hook, withApp("      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/app\n",
                        postgres), datasourceYml, HOST_RUN + "|" + CONTAINER, "{}", -1, false,
                "SPRING_DATASOURCE_URL overrides ${DB_URL:…} by relaxed binding — quiet");

        String kafkaYml = """
                spring:
                  kafka:
                    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:29092}
                """;
        String kafka = """
                  kafka:
                    image: apache/kafka:3.8.0
                    ports:
                      - "9092:9092"
                      - "29092:29092"
                    environment:
                      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,EXTERNAL://localhost:29092
                """;
        failures += expect(hook, withApp("      KAFKA_BOOTSTRAP_SERVERS: kafka:9092\n", kafka), kafkaYml,
                HOST_RUN + "|" + CONTAINER, "{}", -1, false,
                "Kafka host-first default on its EXTERNAL port — quiet");

        if (failures > 0) {
            System.err.println("❌ " + failures + " case(s) failed — `compose gate` did not"
                    + " block or stay quiet where it should.");
            System.exit(1);
        }
        System.out.println("✅ compose gate: unreachable published service blocked without Docker,"
                + " host and container sides of each placeholder checked, silent with nothing to check.");
    }

    /** A compose file with a built `app` service carrying {@code env}, then {@code others}. */
    static String withApp(String env, String others) {
        return "services:\n  app:\n    build: .\n    ports:\n      - \"8080:8080\"\n"
                + "    environment:\n" + env + others;
    }

    /**
     * {@code wanted} -1 means the exit code depends on the runner's Docker and is not checked.
     * {@code needle} is one or more `|`-separated phrases; the line counts as present when any
     * of them is in the output.
     */
    static int expect(Path hook, String compose, String appYml, String needle, String stdin,
                      int wanted, boolean wantLine, String label) throws Exception {
        Path tmp = Files.createTempDirectory("archhook-gate-ci");
        Path schemas = tmp.resolve(".claude/schemas");
        Files.createDirectories(schemas);
        Files.copy(Paths.get(".claude/schemas/extensions.json"), schemas.resolve("extensions.json"));
        if (compose != null) Files.writeString(tmp.resolve("docker-compose.yml"), compose);
        if (appYml != null) {
            Path res = tmp.resolve("src/main/resources");
            Files.createDirectories(res);
            Files.writeString(res.resolve("application.yml"), appYml);
        }

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

        boolean line = java.util.Arrays.stream(needle.split("\\|")).anyMatch(out::contains);
        String why = wanted >= 0 && code != wanted ? "expected exit " + wanted + ", got " + code
                : line != wantLine ? "line `" + needle + "` " + (line ? "present" : "absent")
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
