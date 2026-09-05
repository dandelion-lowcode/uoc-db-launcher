package com.uoc.docker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the panel shows when Docker is not there.
 *
 * <p>A student can quit Docker Desktop while the launcher is open, and the services must
 * not stay frozen on the last thing that was true. Every Docker command answers here the
 * way it does with no daemon behind it, which is the same thing from the manager's side
 * and needs no Docker to run.
 */
@DisplayName("when the Docker daemon cannot be reached")
class DockerManagerOfflineTest {

    private static final Duration LIMIT = Duration.ofSeconds(30);

    /** Word for word what the command line says with nothing listening. */
    private static final String NO_DAEMON = "Cannot connect to the Docker daemon at "
            + "unix:///var/run/docker.sock. Is the docker daemon running?";

    private final List<ServiceStatus> seen = new CopyOnWriteArrayList<>();
    private DockerManager manager;

    /** Refuses every command the way the command line does with no daemon behind it. */
    private static ProcessRunner deadDaemon() {
        return (command, stdin) -> new ProcessRunner.Result(1, NO_DAEMON);
    }

    private DockerManager managerFor(ProcessRunner processRunner) {
        manager = new DockerManager(processRunner, Runnable::run,
                Path.of("docker-compose.yml"));
        manager.setListener((key, status) -> seen.add(status));
        return manager;
    }

    @Test
    void aSecondStartWhileTheFirstIsStillRunningIsIgnored() {
        // Two compose commands at once against one service both fetch the image and then
        // both try to create the container, and Docker refuses the second:
        //   Conflict. The container name "/uocdb-mongo" is already in use
        // which is what a student saw in the trace box after pressing play twice while
        // an image was downloading.
        List<List<String>> commands = new CopyOnWriteArrayList<>();
        managerFor((command, stdin) -> {
            commands.add(command);
            sleepBriefly();
            return new ProcessRunner.Result(0, "");
        });

        manager.start(Database.MONGO.key());
        manager.start(Database.MONGO.key());
        manager.start(Database.MONGO.key());

        awaitQuiet();
        assertThat(commands.stream().filter(c -> c.contains("up")).toList()).hasSize(1);
    }

    @Test
    void startingAServiceWhileItIsStoppingIsHeldRatherThanDropped() {
        // A service is reported stopped the moment its container dies, which is seconds
        // before "compose stop" returns. The button is live again in that window, and a
        // press landing there used to be thrown away without a word: the service sat
        // there stopped, however many times a student asked it to start.
        List<List<String>> commands = new CopyOnWriteArrayList<>();
        managerFor((command, stdin) -> {
            commands.add(command);
            sleepBriefly();
            return new ProcessRunner.Result(0, "");
        });

        manager.stop(Database.MONGO.key());
        manager.start(Database.MONGO.key());

        awaitQuiet();
        assertThat(commands.stream().filter(c -> c.contains("stop")).toList()).hasSize(1);
        assertThat(commands.stream().filter(c -> c.contains("up")).toList())
                .as("the start asked for while it was stopping never ran")
                .hasSize(1);
    }

    @Test
    void onlyTheLastThingAskedForWhileBusyIsDone() {
        // Changing their mind twice is one decision, not three commands to work through.
        List<List<String>> commands = new CopyOnWriteArrayList<>();
        managerFor((command, stdin) -> {
            commands.add(command);
            sleepBriefly();
            return new ProcessRunner.Result(0, "");
        });

        manager.start(Database.MONGO.key());
        manager.stop(Database.MONGO.key());
        manager.start(Database.MONGO.key());
        manager.stop(Database.MONGO.key());

        awaitQuiet();
        assertThat(commands.stream().filter(c -> c.contains("up")).toList()).hasSize(1);
        assertThat(commands.stream().filter(c -> c.contains("stop")).toList()).hasSize(1);
    }

    @Test
    void aServiceCanBeStartedAgainOnceTheFirstCommandHasFinished() {
        List<List<String>> commands = new CopyOnWriteArrayList<>();
        managerFor((command, stdin) -> {
            commands.add(command);
            return new ProcessRunner.Result(0, "");
        });

        manager.start(Database.MONGO.key());
        awaitQuiet();
        manager.start(Database.MONGO.key());
        awaitQuiet();

        assertThat(commands.stream().filter(c -> c.contains("up")).toList()).hasSize(2);
    }

    @Test
    void oneServiceBeingBusyDoesNotBlockAnother() {
        List<List<String>> commands = new CopyOnWriteArrayList<>();
        managerFor((command, stdin) -> {
            commands.add(command);
            sleepBriefly();
            return new ProcessRunner.Result(0, "");
        });

        manager.start(Database.MONGO.key());
        manager.start(Database.REDIS.key());

        awaitQuiet();
        assertThat(commands.stream().filter(c -> c.contains("up")).toList()).hasSize(2);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Waits long enough for the commands submitted so far to have run. */
    private static void awaitQuiet() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void stopListening() {
        if (manager != null) {
            manager.close();
        }
    }

    private void awaitStatus(ServiceStatus status) {
        Instant deadline = Instant.now().plus(LIMIT);
        while (Instant.now().isBefore(deadline)) {
            if (seen.contains(status)) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("never reached " + status + "; saw " + seen);
    }

    @Test
    void aServiceWhoseStateCannotBeReadIsShownAsFailed() {
        // Not "stopped": the launcher does not know that, and saying so would invite the
        // student to press start on a service that may well be running. What separates
        // the two is only the message, since an inspect that finds no container and one
        // that finds no daemon both come back as a failure.
        managerFor(deadDaemon());

        manager.refreshStatus(Database.REDIS.key());

        awaitStatus(ServiceStatus.ERROR);
    }

    @Test
    void everyServiceBeingWatchedIsMarkedWhenTheDaemonGoesAway() {
        managerFor(deadDaemon());
        manager.refreshStatus(Database.REDIS.key());
        awaitStatus(ServiceStatus.ERROR);

        // An event stream that ends the moment it is asked for is the same thing that
        // happens when the daemon disappears while the launcher is open. The service is
        // already shown as failed, and a status that has not changed is not announced a
        // second time: the panel is already saying the right thing, and repainting it
        // would restart its animation for nothing.
        manager.start();

        assertThat(seen).containsOnly(ServiceStatus.ERROR);
    }

    @Test
    void aStartThatCannotRunComposeReportsWhatWentWrong() {
        List<String> failures = new CopyOnWriteArrayList<>();
        managerFor((command, stdin) -> new ProcessRunner.Result(1, "cannot connect to the Docker daemon"));
        manager.setFailureListener((key, details) -> failures.add(details));

        manager.start(Database.REDIS.key());

        // The status is announced before the explanation that follows it, so waiting for
        // the red dot is not the same as waiting for the reason.
        awaitFailureDetail(failures);

        assertThat(failures)
                .as("the student has to be told why, not just shown a red dot")
                .containsExactly("cannot connect to the Docker daemon");
    }

    private static void awaitFailureDetail(List<String> failures) {
        Instant deadline = Instant.now().plus(LIMIT);
        while (Instant.now().isBefore(deadline) && failures.isEmpty()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(failures).as("no explanation ever arrived").isNotEmpty();
    }

    @Test
    void closingTheManagerStopsItReportingAnything() {
        managerFor(deadDaemon());
        manager.start();
        manager.refreshStatus(Database.REDIS.key());
        awaitStatus(ServiceStatus.ERROR);

        manager.close();
        seen.clear();

        // Closing must not paint the panel red on the way out. Ending the event stream
        // looks exactly like the stream ending by itself, which is the daemon going away,
        // and a manager on its way out must not report that as news.
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(seen).isEmpty();
    }
}
