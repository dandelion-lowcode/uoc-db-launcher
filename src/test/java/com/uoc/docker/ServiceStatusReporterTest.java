package com.uoc.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reporter holds what is known about each service and announces what should be shown.
 *
 * <p>
 * What is checked here is the holding and the announcing: that the three things which can
 * change arrive independently, that a status is only announced when it actually changes,
 * and that a failure survives the reconciliation that follows it. Which status a given
 * combination produces belongs to {@link ServiceStateTest}, not here.
 */
class ServiceStatusReporterTest {

    private record Reported(String key, ServiceStatus status) {
    }

    private final List<Reported> statuses = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();

    // The dispatcher runs each notification immediately instead of queueing it on the
    // interface thread, so a test can look at the result on the line after the call.
    private final ServiceStatusReporter reporter = new ServiceStatusReporter(Runnable::run);

    ServiceStatusReporterTest() {
        reporter.setListener((key, status) -> statuses.add(new Reported(key, status)));
        reporter.setFailureListener((key, details) -> failures.add(details));
    }

    private List<ServiceStatus> announcedFor(String key) {
        return statuses.stream().filter(r -> r.key().equals(key)).map(Reported::status).toList();
    }

    @Test
    void aServiceIsRememberedOnceItHasBeenAskedAbout() {
        reporter.watch("mongo");

        assertThat(reporter.watchedKeys()).containsExactly("mongo");
    }

    @Test
    void watchingAServiceTwiceDoesNotForgetWhatWasKnownAboutIt() {
        reporter.observe("mongo", Observation.HEALTHY);

        reporter.watch("mongo");

        assertThat(reporter.stateOf("mongo").observation()).isEqualTo(Observation.HEALTHY);
    }

    @Test
    void aServiceNobodyHasMentionedHasNothingKnownAboutIt() {
        assertThat(reporter.stateOf("mongo")).isEqualTo(ServiceState.unknown());
    }

    @Test
    void whatDockerReportsIsRememberedAndAnnounced() {
        reporter.observe("mongo", Observation.HEALTHY);

        assertThat(reporter.stateOf("mongo").observation()).isEqualTo(Observation.HEALTHY);
        assertThat(announcedFor("mongo")).containsExactly(ServiceStatus.HEALTHY);
    }

    @Test
    void aCommandInFlightIsRememberedSeparatelyFromWhatDockerReports() {
        reporter.observe("mongo", Observation.ABSENT);

        reporter.enterPhase("mongo", Phase.INSTALLING);

        assertThat(reporter.stateOf("mongo").observation()).isEqualTo(Observation.ABSENT);
        assertThat(reporter.stateOf("mongo").phase()).isEqualTo(Phase.INSTALLING);
    }

    @Test
    void theSameStatusTwiceIsOnlyAnnouncedOnce() {
        // The poll runs every few seconds and mostly finds nothing new. Announcing every
        // time would repaint the panel and restart its animation for no reason.
        reporter.observe("mongo", Observation.HEALTHY);
        reporter.observe("mongo", Observation.HEALTHY);
        reporter.observe("mongo", Observation.UP_WITHOUT_HEALTHCHECK);

        assertThat(announcedFor("mongo")).containsExactly(ServiceStatus.HEALTHY);
    }

    @Test
    void aStatusThatChangesBackIsAnnouncedAgain() {
        reporter.observe("mongo", Observation.HEALTHY);
        reporter.observe("mongo", Observation.EXITED_OK);
        reporter.observe("mongo", Observation.HEALTHY);

        assertThat(announcedFor("mongo")).containsExactly(
                ServiceStatus.HEALTHY, ServiceStatus.STOPPED, ServiceStatus.HEALTHY);
    }

    @Test
    void aContainerThatDoesNotExistYetIsNotAnnouncedAsStopped() {
        // While an image is fetched there is no container, and Docker answers questions
        // about it exactly as it would about one that had stopped. The poll runs
        // throughout, so taken at face value it would report a download that has minutes
        // left to run as stopped, over and over.
        reporter.enterPhase("mongo", Phase.INSTALLING);

        reporter.observe("mongo", Observation.ABSENT);

        assertThat(announcedFor("mongo")).containsExactly(ServiceStatus.INSTALLING);
    }

    @Test
    void onceTheCommandIsOverAMissingContainerIsBelieved() {
        reporter.enterPhase("mongo", Phase.INSTALLING);
        reporter.observe("mongo", Observation.ABSENT);

        reporter.enterPhase("mongo", Phase.IDLE);

        assertThat(announcedFor("mongo")).containsExactly(
                ServiceStatus.INSTALLING, ServiceStatus.STOPPED);
    }

    @Test
    void aFailedCommandSurvivesTheReconciliationThatFollowsIt() {
        // The failure used to be announced and then overwritten a moment later by the
        // inspection that came after it, so the service settled back to "stopped" with
        // no reason ever shown.
        reporter.enterPhase("mongo", Phase.STARTING);
        reporter.reportFailure("mongo", "no such image");

        reporter.observe("mongo", Observation.ABSENT);
        reporter.enterPhase("mongo", Phase.IDLE);

        // It says it is starting first, which is true, and then that it failed. What must
        // not happen is settling back to "stopped" afterwards with no reason shown.
        assertThat(announcedFor("mongo"))
                .containsExactly(ServiceStatus.STARTING, ServiceStatus.ERROR)
                .doesNotContain(ServiceStatus.STOPPED);
        assertThat(failures).containsExactly("no such image");
    }

    @Test
    void aFailureIsForgottenWhenTheStudentTriesAgain() {
        reporter.reportFailure("mongo", "no such image");

        reporter.retrying("mongo");

        assertThat(reporter.stateOf("mongo").commandFailed()).isFalse();
        assertThat(announcedFor("mongo")).containsExactly(
                ServiceStatus.ERROR, ServiceStatus.STOPPED);
    }

    @Test
    void aFailedCommandDoesNotHideAContainerThatIsRunningAnyway() {
        // Compose can refuse and leave the service up regardless; what is on the screen
        // should be what is true.
        reporter.reportFailure("mongo", "port already allocated");

        reporter.observe("mongo", Observation.HEALTHY);

        assertThat(announcedFor("mongo")).containsExactly(
                ServiceStatus.ERROR, ServiceStatus.HEALTHY);
    }

    @Test
    void aFailureWithNothingToSayAnnouncesTheStatusButNoDetail() {
        reporter.reportFailure("mongo", "   ");

        assertThat(announcedFor("mongo")).containsExactly(ServiceStatus.ERROR);
        assertThat(failures).isEmpty();
    }

    @Test
    void aFailureForAServiceNobodyWasWatchingStillCountsAsWatching() {
        reporter.reportFailure("mongo", "boom");

        assertThat(reporter.watchedKeys()).containsExactly("mongo");
    }

    @Test
    void aLostDaemonIsReportedForEveryServiceBeingWatched() {
        reporter.observe("mongo", Observation.HEALTHY);
        reporter.observe("redis", Observation.EXITED_OK);

        reporter.reportEverythingUnreachable();

        assertThat(announcedFor("mongo")).endsWith(ServiceStatus.ERROR);
        assertThat(announcedFor("redis")).endsWith(ServiceStatus.ERROR);
    }

    @Test
    void aLostDaemonSaysNothingAboutAServiceNobodyIsWatching() {
        reporter.reportEverythingUnreachable();

        assertThat(statuses).isEmpty();
    }

    @Test
    void oneServiceChangingSaysNothingAboutAnother() {
        reporter.observe("mongo", Observation.HEALTHY);
        reporter.observe("redis", Observation.EXITED_OK);

        assertThat(announcedFor("mongo")).containsExactly(ServiceStatus.HEALTHY);
        assertThat(announcedFor("redis")).containsExactly(ServiceStatus.STOPPED);
    }

    @ParameterizedTest
    @EnumSource(Observation.class)
    void everyThingDockerCanReportProducesSomethingToShow(Observation observation) {
        reporter.observe("mongo", observation);

        assertThat(announcedFor("mongo")).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(Phase.class)
    void everyCommandInFlightProducesSomethingToShow(Phase phase) {
        reporter.enterPhase("mongo", phase);

        assertThat(reporter.stateOf("mongo").phase()).isEqualTo(phase);
    }
}
