package com.uoc.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the manager says while a service is being fetched, and when it will not start.
 *
 * <p>
 * These are the parts a student sees only on the worst morning: the first run, when
 * several gigabytes are on their way, and the run where something is wrong and the only
 * account of it is what Docker wrote. Neither needs a daemon to be checked, and neither
 * can be checked by starting a service that works.
 */
@DisplayName("what the manager reports while installing, and when it cannot")
class DockerManagerReportingTest {

    private static final String SERVICE = "redis";
    private static final Duration LIMIT = Duration.ofSeconds(10);

    private final List<String> progress = new CopyOnWriteArrayList<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    private final List<ServiceStatus> statuses = new CopyOnWriteArrayList<>();

    private DockerManager manager;

    @AfterEach
    void stopListening() {
        if (manager != null) {
            manager.close();
        }
    }

    private DockerManager managerFor(ProcessRunner runner) {
        manager = new DockerManager(runner, Runnable::run, Path.of("docker-compose.yml"));
        manager.setProgressListener((key, text) -> progress.add(text));
        manager.setFailureListener((key, details) -> failures.add(details));
        manager.setListener((key, status) -> statuses.add(status));
        return manager;
    }

    /**
     * A Docker that has no image for the service yet, so a start is an install, and that
     * writes the lines a pull writes.
     */
    private static final class Downloading implements ProcessRunner {

        private final List<String> lines;

        private Downloading(String... lines) {
            this.lines = List.of(lines);
        }

        @Override
        public Result run(List<String> command, String stdin) {
            if (command.contains("config")) {
                return new Result(0, "redis:7\n");
            }
            if (command.contains("inspect")) {
                // Both the image inspect, which says the image is missing, and the
                // container inspect, which says there is no container yet.
                return new Result(1, "No such object");
            }
            return new Result(0, String.join("\n", lines));
        }

        @Override
        public Result run(List<String> command, String stdin, Consumer<String> onLine) {
            lines.forEach(onLine);
            return run(command, stdin);
        }
    }

    @Test
    void aServiceWhoseImageIsMissingReportsTheDownloadAsItArrives() {
        managerFor(new Downloading(
                "7: Pulling from library/redis",
                "a2abf6c4d29d: Downloading [====>     ]  12.5MB/50MB",
                "a2abf6c4d29d: Pull complete"));

        manager.start(SERVICE);

        awaitSomethingIn(progress);
        assertThat(progress).isNotEmpty();
        assertThat(progress.getLast()).contains("Pull complete");
    }

    @Test
    void aStartThatDockerRefusesIsReportedWordForWord() {
        // The only account a student has of why nothing happened.
        String refusal = "service \"redis\" has no image and no build context";
        managerFor((command, stdin) -> command.contains("up")
                ? new ProcessRunner.Result(1, refusal)
                : new ProcessRunner.Result(0, ""));

        manager.start(SERVICE);

        awaitSomethingIn(failures);
        assertThat(failures).containsExactly(refusal);
    }

    @Test
    void aCommandThatCannotBeRunAtAllIsReportedRatherThanLost() {
        // Docker uninstalled between opening the launcher and pressing play: the runner
        // throws instead of answering, and there is still a student waiting to be told.
        managerFor((command, stdin) -> {
            throw new IllegalStateException("Cannot run program \"docker\"");
        });

        manager.start(SERVICE);

        awaitSomethingIn(failures);
        assertThat(failures.getFirst()).contains("docker");
    }

    @Test
    void anEventStreamThatCannotEvenBeStartedReadsAsALostDaemon() {
        // Not an error on the way to the first window: it is the same news as the stream
        // ending later, and is answered the same way.
        managerFor(new ProcessRunner() {

            @Override
            public Result run(List<String> command, String stdin) {
                return new Result(1, "");
            }

            @Override
            public LiveProcess stream(List<String> command, Consumer<String> onLine,
                    Runnable onEnded) {
                throw new IllegalStateException("no daemon");
            }
        });
        manager.refreshStatus(SERVICE);

        manager.start();

        awaitSomethingIn(statuses);
        assertThat(statuses).endsWith(ServiceStatus.ERROR);
    }

    @Test
    void closingEndsTheEventStreamItOpened() {
        List<String> stopped = new CopyOnWriteArrayList<>();
        managerFor(new ProcessRunner() {

            @Override
            public Result run(List<String> command, String stdin) {
                return new Result(0, "");
            }

            @Override
            public LiveProcess stream(List<String> command, Consumer<String> onLine,
                    Runnable onEnded) {
                return () -> stopped.add("stopped");
            }
        }).start();

        manager.close();

        assertThat(stopped).containsExactly("stopped");
    }

    @Test
    void closingTwiceIsNotAFailure() {
        // The window can be closed while a service is still being asked about.
        managerFor((command, stdin) -> new ProcessRunner.Result(0, "")).start();

        manager.close();
        manager.close();

        assertThat(failures).isEmpty();
    }

    @Test
    void nothingIsAskedOfDockerAfterTheLauncherHasClosed() {
        List<List<String>> asked = new CopyOnWriteArrayList<>();
        managerFor((command, stdin) -> {
            asked.add(command);
            return new ProcessRunner.Result(0, "");
        }).start();
        manager.close();
        asked.clear();

        manager.start(SERVICE);
        manager.refreshStatus(SERVICE);

        assertThat(asked).isEmpty();
    }

    /**
     * Waits for the manager's own threads to say something. Everything here is decided
     * off the calling thread, so an assertion made straight away would pass or fail on
     * timing rather than on behaviour.
     */
    private static void awaitSomethingIn(List<?> reported) {
        java.time.Instant deadline = java.time.Instant.now().plus(LIMIT);
        while (java.time.Instant.now().isBefore(deadline) && reported.isEmpty()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(reported).as("nothing was ever reported").isNotEmpty();
    }
}
