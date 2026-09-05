package com.uoc.docker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status shown beside each service, followed through a real start and a real stop.
 *
 * <p>This is the only test that covers the whole chain: the Docker event stream, the
 * inspection of container state, the guard against late reports, and the statuses that
 * reach the panel. The pieces are checked on their own elsewhere; what is checked here is
 * that they agree with what Docker really does.
 *
 * <p>Redis is the service used because it starts in a couple of seconds and has a
 * healthcheck, so a full cycle is quick.
 */
@DisplayName("the status of a service through a real start and stop")
class ServiceLifecycleIntegrationTest extends DockerIntegrationTestBase {

    private static final Database SERVICE = Database.REDIS;
    private static final Duration LIMIT = Duration.ofMinutes(2);

    private final List<ServiceStatus> seen = new CopyOnWriteArrayList<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    private DockerManager manager;

    @BeforeEach
    void startFromAStoppedService() {
        assumeDockerIsRunning();
        stopAndAwait(SERVICE);

        // The statuses are collected as they are decided rather than on the interface
        // thread, so the test can read them directly.
        manager = new DockerManager(new SystemProcessRunner(), Runnable::run, COMPOSE_FILE);
        manager.setListener((key, status) -> {
            if (key.equals(SERVICE.key())) {
                seen.add(status);
            }
        });
        manager.setFailureListener((key, details) -> failures.add(details));
        manager.start();
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
            pause();
        }
        throw new AssertionError("never reached " + status + "; saw " + seen);
    }

    private static void pause() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aStoppedServiceIsReportedAsStopped() {
        manager.refreshStatus(SERVICE.key());

        awaitStatus(ServiceStatus.STOPPED);
    }

    @Test
    void startingAServiceGoesFromStartingToHealthy() {
        manager.start(SERVICE.key());

        awaitStatus(ServiceStatus.HEALTHY);

        // The student has to be told it is on its way before it is ready, or the panel
        // looks frozen for as long as the image takes to come up. What comes before that
        // is whatever the service already was, which for one nobody has touched yet is
        // stopped.
        assertThat(seen).contains(ServiceStatus.STARTING);
        assertThat(seen.indexOf(ServiceStatus.STARTING))
                .isLessThan(seen.indexOf(ServiceStatus.HEALTHY));
        assertThat(seen).doesNotContain(ServiceStatus.ERROR, ServiceStatus.CRASHED);
        assertThat(failures).isEmpty();
    }

    @Test
    void aServiceThatIsUpButNotYetCheckedIsShownAsLoadingRatherThanReady() {
        manager.start(SERVICE.key());
        awaitStatus(ServiceStatus.HEALTHY);

        // RUNNING means "up, healthcheck not passed yet". Reaching HEALTHY without ever
        // passing through it would mean the panel called the service ready too early.
        assertThat(seen).contains(ServiceStatus.RUNNING);
        assertThat(seen.indexOf(ServiceStatus.RUNNING))
                .isLessThan(seen.indexOf(ServiceStatus.HEALTHY));
    }

    @Test
    void stoppingAServiceEndsAtStoppedAndStaysThere() {
        manager.start(SERVICE.key());
        awaitStatus(ServiceStatus.HEALTHY);
        seen.clear();

        manager.stop(SERVICE.key());
        awaitStatus(ServiceStatus.STOPPED);

        // The bug this covers: a healthcheck already in flight lands after the stop and
        // flashes the service green again. Whatever arrives late, the last word is
        // stopped, and nothing claims it is healthy after the stop was asked for.
        Instant settle = Instant.now().plusSeconds(12);
        while (Instant.now().isBefore(settle)) {
            pause();
        }
        assertThat(seen)
                .as("statuses after the stop was requested: %s", seen)
                .doesNotContain(ServiceStatus.HEALTHY, ServiceStatus.RUNNING);
        assertThat(seen.get(seen.size() - 1)).isEqualTo(ServiceStatus.STOPPED);
    }

    @Test
    void aServiceStartedAgainAfterBeingStoppedIsReportedHealthyAgain() {
        manager.start(SERVICE.key());
        awaitStatus(ServiceStatus.HEALTHY);
        manager.stop(SERVICE.key());
        awaitStatus(ServiceStatus.STOPPED);
        seen.clear();

        // The guard must not leave the service permanently silenced.
        manager.start(SERVICE.key());

        awaitStatus(ServiceStatus.HEALTHY);
    }

    @Test
    void theStatusOfOneServiceSaysNothingAboutAnother() {
        manager.refreshStatus(Database.MONGO.key());
        manager.start(SERVICE.key());

        awaitStatus(ServiceStatus.HEALTHY);

        // seen only collects the service under test, so anything here came from it.
        assertThat(seen).isNotEmpty();
    }
}
