package com.uoc.docker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Partitions exercised here.
 *
 * <p>fromState(running, health):
 * <ul>
 *   <li>running: true, false
 *   <li>health: null (image declares no healthcheck), "healthy", "unhealthy",
 *       "starting", "none", an unknown word, and case/whitespace variants
 * </ul>
 * The boundary that matters is running flipping from true to false while health stays
 * "healthy": Docker keeps the last health result after a container exits, so the two
 * inputs disagree and the running flag has to win.
 *
 * <p>fromEvent(action):
 * <ul>
 *   <li>lifecycle actions: start, die, stop
 *   <li>health actions: the prefix followed by healthy, unhealthy, starting, or nothing
 *   <li>actions that report no status: create, destroy, attach, an unknown word, empty
 *   <li>absent: null
 * </ul>
 */
class ContainerStatusTest {

    @Nested
    @DisplayName("from the inspected state")
    class FromState {

        @ParameterizedTest(name = "running with health {0} is {1}")
        @CsvSource({
                "healthy,   HEALTHY",
                "unhealthy, UNHEALTHY",
                "starting,  RUNNING",
                "none,      RUNNING",
                "whatever,  RUNNING"
        })
        void runningContainersFollowTheirHealthcheck(String health, ServiceStatus expected) {
            assertThat(ContainerStatus.fromState(true, health)).isEqualTo(expected);
        }

        @Test
        void aRunningContainerWithoutAHealthcheckIsLoading() {
            // Redis and Riak declare one, but an image is free not to. Without a check
            // there is nothing to wait for, yet the service is not confirmed ready either.
            assertThat(ContainerStatus.fromState(true, null)).isEqualTo(ServiceStatus.RUNNING);
        }

        @ParameterizedTest(name = "stopped with health {0}")
        @NullSource
        @ValueSource(strings = {"healthy", "unhealthy", "starting", "none"})
        void aStoppedContainerIsStoppedWhateverItsLastHealthSaid(String health) {
            // The boundary: Docker retains the health of the last run after the container
            // exits, which once showed stopped services as failed.
            assertThat(ContainerStatus.fromState(false, health)).isEqualTo(ServiceStatus.STOPPED);
        }

        @ParameterizedTest(name = "health {0} is still recognised")
        @ValueSource(strings = {"HEALTHY", "Healthy", "  healthy  ", "hEaLtHy"})
        void healthIsRecognisedWhateverItsCaseOrPadding(String health) {
            assertThat(ContainerStatus.fromState(true, health)).isEqualTo(ServiceStatus.HEALTHY);
        }

        @ParameterizedTest(name = "health {0} is not healthy")
        @ValueSource(strings = {"", " ", "health", "healthy!", "unhealth"})
        void aHealthValueThatIsNotAnExactMatchIsNotTakenAsHealthy(String health) {
            // The off point of the match: anything one character away must not pass.
            assertThat(ContainerStatus.fromState(true, health)).isEqualTo(ServiceStatus.RUNNING);
        }
    }

    @Nested
    @DisplayName("from a container event")
    class FromEvent {

        @ParameterizedTest(name = "{0} reports {1}")
        @CsvSource({
                "start,                      RUNNING",
                "die,                        STOPPED",
                "stop,                       STOPPED",
                "'health_status: healthy',   HEALTHY",
                "'health_status: unhealthy', UNHEALTHY",
                "'health_status:healthy',    HEALTHY"
        })
        void recognisedActions(String action, ServiceStatus expected) {
            assertThat(ContainerStatus.fromEvent(action)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} reports nothing")
        @ValueSource(strings = {
                "create", "destroy", "attach", "exec_start", "pause", "unpause",
                "restart", "kill", "rename", "", "   ", "started", "stopped"
        })
        void actionsThatDoNotChangeTheStatus(String action) {
            // Docker emits many events per container; the panel must ignore the ones that
            // say nothing about whether the service is usable.
            assertThat(ContainerStatus.fromEvent(action)).isNull();
        }

        @Test
        void aMissingActionReportsNothing() {
            assertThat(ContainerStatus.fromEvent(null)).isNull();
        }

        @ParameterizedTest(name = "health event {0} reports nothing")
        @ValueSource(strings = {
                "health_status: starting", "health_status:", "health_status: ", "health_status: none"
        })
        void healthEventsThatAreNotAVerdict(String action) {
            // A healthcheck that is still running is not yet an answer.
            assertThat(ContainerStatus.fromEvent(action)).isNull();
        }

        @ParameterizedTest(name = "{0} is still recognised")
        @ValueSource(strings = {"START", "  start  ", "Start", "HEALTH_STATUS: HEALTHY"})
        void actionsAreRecognisedWhateverTheirCaseOrPadding(String action) {
            assertThat(ContainerStatus.fromEvent(action)).isNotNull();
        }
    }
}
