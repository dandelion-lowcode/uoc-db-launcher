package com.uoc.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading Docker's answer about a container.
 *
 * <p>
 * Every case below was produced against a real daemon first and the values copied from
 * what it actually said, because the obvious reading of two of them is wrong: a paused
 * container and one in a restart loop both report {@code running=true}, and a container
 * the kernel killed for its memory use exits like any other.
 */
class ObservationTest {

    @Test
    void aContainerCreatedButNeverStarted() {
        assertThat(Observation.of("created", false, 0, null)).isEqualTo(Observation.CREATED);
    }

    @Test
    void aContainerUpWithNoHealthcheckAtAll() {
        assertThat(Observation.of("running", false, 0, null))
                .isEqualTo(Observation.UP_WITHOUT_HEALTHCHECK);
    }

    @ParameterizedTest(name = "health {0}")
    @CsvSource({ "starting, HEALTH_STARTING", "healthy, HEALTHY", "unhealthy, UNHEALTHY" })
    void aContainerUpWithAHealthcheck(String health, Observation expected) {
        assertThat(Observation.of("running", false, 0, health)).isEqualTo(expected);
    }

    @Test
    void aPausedContainerIsNotConfusedForARunningOne() {
        // Docker reports running=true for a paused container, and its healthcheck fails
        // while it is suspended, so both of the fields the launcher used to read say the
        // wrong thing at once.
        assertThat(Observation.of("paused", false, 0, "unhealthy")).isEqualTo(Observation.PAUSED);
        assertThat(Observation.of("paused", false, 0, null)).isEqualTo(Observation.PAUSED);
    }

    @Test
    void aContainerLoopingThroughRestartsIsNotConfusedForARunningOne() {
        // running=true throughout, which is how a service crashing over and over came to
        // be shown in green.
        assertThat(Observation.of("restarting", false, 1, null))
                .isEqualTo(Observation.RESTARTING);
    }

    @Test
    void aContainerThatFinishedWithoutComplaint() {
        assertThat(Observation.of("exited", false, 0, null)).isEqualTo(Observation.EXITED_OK);
    }

    @ParameterizedTest(name = "exit code {0}")
    @ValueSource(ints = { 1, 2, 127, 130, 143 })
    void aContainerThatFinishedBadly(int exitCode) {
        assertThat(Observation.of("exited", false, exitCode, null))
                .isEqualTo(Observation.EXITED_FAILED);
    }

    @Test
    void aContainerKilledForItsMemoryUse() {
        // It exits with a code like any other, and only the flag says why. Asked after
        // the exit rather than before it, this would read as an ordinary failure.
        assertThat(Observation.of("exited", true, 137, null)).isEqualTo(Observation.OUT_OF_MEMORY);
    }

    @Test
    void memoryOutweighsWhateverElseTheContainerLooksLike() {
        assertThat(Observation.of("running", true, 137, "healthy"))
                .isEqualTo(Observation.OUT_OF_MEMORY);
    }

    @Test
    void aContainerDockerCannotEvenRemove() {
        assertThat(Observation.of("dead", false, 0, null)).isEqualTo(Observation.DEAD);
    }

    @Test
    void aContainerOnItsWayOut() {
        assertThat(Observation.of("removing", false, 0, null)).isEqualTo(Observation.REMOVING);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = { "RUNNING", "Running", " running " })
    void theAnswerIsReadWhateverItsCaseOrSpacing(String status) {
        assertThat(Observation.of(status, false, 0, "HEALTHY")).isEqualTo(Observation.HEALTHY);
    }

    @Test
    void aStatusNobodyHereKnowsIsTreatedAsNothingBeingThere() {
        // The one answer that leads nowhere harmful: it shows as stopped, and the poll
        // corrects it as soon as Docker says something recognisable.
        assertThat(Observation.of("inventado", false, 0, null)).isEqualTo(Observation.ABSENT);
        assertThat(Observation.of(null, false, 0, null)).isEqualTo(Observation.ABSENT);
        assertThat(Observation.of("", false, 0, null)).isEqualTo(Observation.ABSENT);
    }

    @Test
    void aHealthcheckDescribedInWordsNobodyKnowsFallsBackToMerelyBeingUp() {
        assertThat(Observation.of("running", false, 0, "inventado"))
                .isEqualTo(Observation.UP_WITHOUT_HEALTHCHECK);
        assertThat(Observation.of("running", false, 0, "  "))
                .isEqualTo(Observation.UP_WITHOUT_HEALTHCHECK);
    }

    @Test
    void anExitCodeDockerDidNotGiveIsNotTakenForAFailure() {
        assertThat(Observation.of("exited", false, null, null)).isEqualTo(Observation.EXITED_OK);
    }

    @Test
    void theOnesThatCountAsUpAreTheOnesWithAContainerRunning() {
        assertThat(Observation.values()).filteredOn(Observation::isUp)
                .containsExactlyInAnyOrder(Observation.HEALTH_STARTING, Observation.HEALTHY,
                        Observation.UNHEALTHY, Observation.UP_WITHOUT_HEALTHCHECK,
                        Observation.PAUSED, Observation.RESTARTING);
    }
}
