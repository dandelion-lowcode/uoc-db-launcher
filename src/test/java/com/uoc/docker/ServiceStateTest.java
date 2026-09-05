package com.uoc.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules that decide what a student sees.
 *
 * <p>
 * Because the decision is one function of three arguments, its whole domain can be
 * covered: four phases, fourteen observations and a failed command or not, which is a
 * hundred and twelve combinations. Before this was a function they were guards spread
 * across three classes, and most of those combinations had no defined answer at all --
 * two of them were faults reported from the running application.
 */
class ServiceStateTest {

    private static ServiceStatus shown(Phase phase, Observation observation) {
        return new ServiceState(phase, observation, false).displayed();
    }

    private static ServiceStatus shownAfterFailure(Phase phase, Observation observation) {
        return new ServiceState(phase, observation, true).displayed();
    }

    // --- The whole domain has an answer -------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(Phase.class)
    void everyCombinationOfPhaseAndObservationDecidesSomething(Phase phase) {
        for (Observation observation : Observation.values()) {
            assertThat(shown(phase, observation)).as("%s with %s", phase, observation).isNotNull();
            assertThat(shownAfterFailure(phase, observation))
                    .as("%s with %s after a refused command", phase, observation)
                    .isNotNull();
        }
    }

    // --- What a running command means ---------------------------------------------

    @ParameterizedTest(name = "installing over {0}")
    @EnumSource(Observation.class)
    void whileAnImageIsBeingFetchedNothingDockerSaysChangesTheAnswer(Observation observation) {
        // This is the fault that started all of it. A container being installed does not
        // exist yet, and Docker describes it exactly as it describes one that has
        // stopped, so the poll running every few seconds turned a download with minutes
        // left into "stopped", again and again.
        if (observation == Observation.DAEMON_LOST || observation == Observation.DEAD) {
            return;
        }
        assertThat(shown(Phase.INSTALLING, observation)).isEqualTo(ServiceStatus.INSTALLING);
    }

    @ParameterizedTest(name = "stopping over {0}")
    @EnumSource(Observation.class)
    void whileAServiceIsBeingStoppedALateHealthcheckCannotFlashGreenOverIt(
            Observation observation) {
        if (observation == Observation.DAEMON_LOST || observation == Observation.DEAD) {
            return;
        }
        assertThat(shown(Phase.STOPPING, observation)).isEqualTo(ServiceStatus.STOPPING);
    }

    @Test
    void aContainerNotThereYetIsStartingRatherThanStopped() {
        assertThat(shown(Phase.STARTING, Observation.ABSENT)).isEqualTo(ServiceStatus.STARTING);
        assertThat(shown(Phase.STARTING, Observation.CREATED)).isEqualTo(ServiceStatus.STARTING);
    }

    @Test
    void aServiceThatComesUpDuringItsStartSaysSoWithoutWaitingForTheCommandToReturn() {
        assertThat(shown(Phase.STARTING, Observation.HEALTHY)).isEqualTo(ServiceStatus.HEALTHY);
        assertThat(shown(Phase.STARTING, Observation.HEALTH_STARTING))
                .isEqualTo(ServiceStatus.RUNNING);
    }

    @Test
    void aServiceThatDiesDuringItsStartSaysSoRatherThanClaimingToBeStarting() {
        assertThat(shown(Phase.STARTING, Observation.EXITED_FAILED))
                .isEqualTo(ServiceStatus.CRASHED);
        assertThat(shown(Phase.STARTING, Observation.OUT_OF_MEMORY))
                .isEqualTo(ServiceStatus.OUT_OF_MEMORY);
    }

    // --- What Docker reports, with nothing in flight -------------------------------

    @Test
    void aContainerThatIsNotThereIsStopped() {
        assertThat(shown(Phase.IDLE, Observation.ABSENT)).isEqualTo(ServiceStatus.STOPPED);
    }

    @Test
    void aContainerCreatedButNeverStartedIsNotOnItsWayAnywhere() {
        assertThat(shown(Phase.IDLE, Observation.CREATED)).isEqualTo(ServiceStatus.STOPPED);
    }

    @Test
    void aHealthcheckStillRunningIsLoadingRatherThanReady() {
        assertThat(shown(Phase.IDLE, Observation.HEALTH_STARTING))
                .isEqualTo(ServiceStatus.RUNNING);
    }

    @Test
    void aServiceWithoutAHealthcheckIsReadyAsSoonAsItIsUp() {
        // Which is what Jupyter relies on. It falls out of what was observed rather than
        // being a rule about that one service, so nothing anywhere names it.
        assertThat(shown(Phase.IDLE, Observation.UP_WITHOUT_HEALTHCHECK))
                .isEqualTo(ServiceStatus.HEALTHY);
    }

    @Test
    void aPausedContainerIsNotReportedAsWorking() {
        // Docker says running=true for a paused container, and its healthcheck fails
        // while it is suspended, so reading either field alone shows a service that
        // answers nobody as either healthy or broken.
        assertThat(shown(Phase.IDLE, Observation.PAUSED)).isEqualTo(ServiceStatus.PAUSED);
    }

    @Test
    void aContainerLoopingThroughRestartsIsNotReportedAsWorking() {
        // Docker says running=true throughout a restart loop, so a service crashing over
        // and over used to show as healthy, in green, indefinitely.
        assertThat(shown(Phase.IDLE, Observation.RESTARTING)).isEqualTo(ServiceStatus.RESTARTING);
    }

    @Test
    void aContainerKilledForItsMemoryIsToldApartFromOneThatMerelyStopped() {
        // The likeliest way for a database to disappear on a laptop, and the only one the
        // student can do something about. Reported as "stopped" it gives them nothing.
        assertThat(shown(Phase.IDLE, Observation.OUT_OF_MEMORY))
                .isEqualTo(ServiceStatus.OUT_OF_MEMORY);
        assertThat(shown(Phase.IDLE, Observation.EXITED_OK)).isEqualTo(ServiceStatus.STOPPED);
    }

    @Test
    void aContainerThatExitedBadlyIsToldApartFromOneThatWasAskedToStop() {
        assertThat(shown(Phase.IDLE, Observation.EXITED_FAILED)).isEqualTo(ServiceStatus.CRASHED);
        assertThat(shown(Phase.IDLE, Observation.EXITED_OK)).isEqualTo(ServiceStatus.STOPPED);
    }

    @Test
    void aContainerOnItsWayOutIsStopping() {
        assertThat(shown(Phase.IDLE, Observation.REMOVING)).isEqualTo(ServiceStatus.STOPPING);
    }

    @Test
    void aFailingHealthcheckIsShownAsSuchWhileTheContainerStaysUp() {
        assertThat(shown(Phase.IDLE, Observation.UNHEALTHY)).isEqualTo(ServiceStatus.UNHEALTHY);
    }

    // --- Nothing can be known ------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(Phase.class)
    void aDaemonThatCannotBeReachedOutweighsEverythingElse(Phase phase) {
        assertThat(shown(phase, Observation.DAEMON_LOST)).isEqualTo(ServiceStatus.ERROR);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Phase.class)
    void aContainerDockerCannotEvenRemoveIsAnError(Phase phase) {
        assertThat(shown(phase, Observation.DEAD)).isEqualTo(ServiceStatus.ERROR);
    }

    // --- A command that was refused ------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(Observation.class)
    void aRefusedCommandIsShownUnlessTheContainerIsUpAnyway(Observation observation) {
        ServiceStatus status = shownAfterFailure(Phase.IDLE, observation);

        if (observation.isUp()) {
            assertThat(status).as("%s", observation).isNotEqualTo(ServiceStatus.ERROR);
        } else {
            assertThat(status).as("%s", observation).isEqualTo(ServiceStatus.ERROR);
        }
    }

    @Test
    void aRefusedCommandOutweighsACommandStillInFlight() {
        // Compose reports the failure before the phase is cleared, and the failure is
        // what the student needs to see rather than a start that is not happening.
        assertThat(shownAfterFailure(Phase.STARTING, Observation.ABSENT))
                .isEqualTo(ServiceStatus.ERROR);
        assertThat(shownAfterFailure(Phase.INSTALLING, Observation.ABSENT))
                .isEqualTo(ServiceStatus.ERROR);
    }

    // --- The record itself ---------------------------------------------------------

    @Test
    void nothingIsKnownAboutAServiceUntilSomethingHappensToIt() {
        assertThat(ServiceState.unknown().displayed()).isEqualTo(ServiceStatus.STOPPED);
    }

    @Test
    void eachPieceOfNewsChangesOnlyItsOwnPart() {
        ServiceState state = ServiceState.unknown()
                .withPhase(Phase.STARTING)
                .withObservation(Observation.HEALTHY)
                .withCommandFailed();

        assertThat(state.phase()).isEqualTo(Phase.STARTING);
        assertThat(state.observation()).isEqualTo(Observation.HEALTHY);
        assertThat(state.commandFailed()).isTrue();

        assertThat(state.retrying().commandFailed()).isFalse();
        assertThat(state.retrying().observation()).isEqualTo(Observation.HEALTHY);
        assertThat(state.retrying().phase()).isEqualTo(Phase.STARTING);
    }
}
