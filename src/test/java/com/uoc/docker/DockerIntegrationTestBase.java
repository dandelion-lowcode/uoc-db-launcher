package com.uoc.docker;

import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Support for the tests that need real containers.
 *
 * <p>These drive the project's own compose file rather than containers a test library
 * would create, because the compose file is the deployment: it fixes the container names
 * the launcher runs {@code docker exec} against and the ports the course notes use. A
 * test that started its own containers would be checking a deployment nobody runs.
 *
 * <p>They are tagged so the everyday {@code mvn test} stays fast, and they step aside
 * rather than fail when Docker is not running, so a missing daemon never looks like a
 * broken build.
 */
@Tag("integration")
abstract class DockerIntegrationTestBase {

    private static final Duration STARTUP_LIMIT = Duration.ofMinutes(5);
    private static final Duration POLL = Duration.ofSeconds(2);

    private static final ProcessRunner PROCESS = new SystemProcessRunner();

    /**
     * The compose file, unpacked exactly the way the application unpacks it. These tests
     * go through the same installation the students do, so that a file left out of the
     * bundle fails here rather than on their machines.
     */
    static final Path COMPOSE_FILE = installBundledFiles();

    private static Path installBundledFiles() {
        try {
            return BundledFiles.inUserDataDirectory().install();
        } catch (IOException e) {
            throw new IllegalStateException("Could not install the bundled Docker files", e);
        }
    }

    static void assumeDockerIsRunning() {
        assumeTrue(DockerAvailability.isRunning(),
                "Docker is not running, so the containers these tests need cannot be started");
    }

    /**
     * Brings one service up and waits for its healthcheck to pass, leaving it running for
     * the next test rather than paying the startup cost again.
     */
    static void startAndAwait(Database database) {
        assumeDockerIsRunning();

        compose("up", "-d", database.key());

        Instant deadline = Instant.now().plus(STARTUP_LIMIT);
        while (Instant.now().isBefore(deadline)) {
            if (healthOf(database).equals(ContainerStatus.HEALTHY)) {
                return;
            }
            sleep();
        }
        throw new IllegalStateException(
                database.displayName() + " did not become healthy within " + STARTUP_LIMIT);
    }

    /** Takes a service down and waits for it to be gone, so a test starts from nothing. */
    static void stopAndAwait(Database database) {
        assumeDockerIsRunning();

        compose("stop", database.key());

        Instant deadline = Instant.now().plus(STARTUP_LIMIT);
        while (Instant.now().isBefore(deadline)) {
            if (!isRunning(database)) {
                return;
            }
            sleep();
        }
        throw new IllegalStateException(database.displayName() + " would not stop");
    }

    private static boolean isRunning(Database database) {
        ProcessRunner.Result result = PROCESS.run(List.of(
                DockerCommand.EXECUTABLE, "inspect", "--format", "{{.State.Running}}",
                database.containerName()), null);
        return result.output().trim().equals("true");
    }

    /** A client aimed at the same daemon the application would use. */
    static com.github.dockerjava.api.DockerClient dockerClient() {
        var config = com.github.dockerjava.core.DefaultDockerClientConfig
                .createDefaultConfigBuilder().build();
        var httpClient = new com.github.dockerjava.zerodep.ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        return com.github.dockerjava.core.DockerClientImpl.getInstance(config, httpClient);
    }

    private static String healthOf(Database database) {
        ProcessRunner.Result result = PROCESS.run(List.of(
                DockerCommand.EXECUTABLE, "inspect",
                "--format", "{{if .State.Health}}{{.State.Health.Status}}{{end}}",
                database.containerName()), null);
        return result.output().trim();
    }

    private static void compose(String... arguments) {
        List<String> command = new java.util.ArrayList<>(List.of(
                DockerCommand.EXECUTABLE, DockerCommand.COMPOSE, "-f", COMPOSE_FILE.toString()));
        command.addAll(List.of(arguments));

        ProcessRunner.Result result = PROCESS.run(command, null);
        if (result.failed()) {
            throw new IllegalStateException(
                    "compose " + String.join(" ", arguments) + " failed:\n" + result.output());
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Runs a query the way the application does, through the real process runner. */
    protected static String query(Database database, String query) {
        return new QueryRunner().execute(database.key(), query);
    }
}
