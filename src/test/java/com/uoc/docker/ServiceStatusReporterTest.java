package com.uoc.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Partitions exercised here.
 *
 * <p>
 * What the student asked for: nothing yet, running, stopped. What Docker
 * reports:
 * each of the seven statuses. The interesting combinations are the ones where
 * the two
 * disagree, which is what happens for the seconds a container takes to shut
 * down.
 */
class ServiceStatusReporterTest {

    private record Reported(String key, ServiceStatus status) {
    }

    private final List<Reported> statuses = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();

    // The dispatcher runs each notification immediately instead of queueing it on
    // the
    // interface thread, so a test can look at the result on the line after the
    // call.
    private final ServiceStatusReporter reporter = new ServiceStatusReporter(Runnable::run);

    ServiceStatusReporterTest() {
        reporter.setListener((key, status) -> statuses.add(new Reported(key, status)));
        reporter.setFailureListener((key, details) -> failures.add(details));
    }

    private List<ServiceStatus> statusesFor(String key) {
        return statuses.stream().filter(r -> r.key().equals(key)).map(Reported::status).toList();
    }

    @Test
    void aStatusIsPassedOnWhenNothingWasAskedFor() {
        reporter.report("mongo", ServiceStatus.HEALTHY);

        assertThat(statusesFor("mongo")).containsExactly(ServiceStatus.HEALTHY);
    }

    @ParameterizedTest
    @EnumSource(ServiceStatus.class)
    void everyStatusReachesTheInterfaceWhileTheServiceIsMeantToRun(ServiceStatus status) {
        reporter.expectRunning("mongo");

        reporter.report("mongo", status);

        assertThat(statusesFor("mongo")).containsExactly(status);
    }

    @ParameterizedTest
    @EnumSource(value = ServiceStatus.class, names = { "HEALTHY", "RUNNING", "STARTING", "UNHEALTHY" })
    void aLateReportIsIgnoredOnceAStopWasAskedFor(ServiceStatus status) {
        // The bug this prevents: a healthcheck already in flight lands after the stop
        // and
        // flashes a green "running" over a service that is on its way out.
        reporter.expectStopped("mongo");

        reporter.report("mongo", status);

        assertThat(statusesFor("mongo")).isEmpty();
    }

    @Test
    void aServiceReportedAsStoppedIsAlwaysAnnounced() {
        reporter.expectStopped("mongo");

        reporter.report("mongo", ServiceStatus.STOPPED);

        assertThat(statusesFor("mongo")).containsExactly(ServiceStatus.STOPPED);
    }

    @Test
    void onceItHasStoppedLaterReportsAreBelievedAgain() {
        // Otherwise a service could never be shown as running again after being
        // stopped.
        reporter.expectStopped("mongo");
        reporter.report("mongo", ServiceStatus.STOPPED);

        reporter.report("mongo", ServiceStatus.HEALTHY);

        assertThat(statusesFor("mongo"))
                .containsExactly(ServiceStatus.STOPPED, ServiceStatus.HEALTHY);
    }

    @Test
    void askingForItToRunAgainCancelsTheStopThatWasPending() {
        reporter.expectStopped("mongo");
        reporter.expectRunning("mongo");

        reporter.report("mongo", ServiceStatus.HEALTHY);

        assertThat(statusesFor("mongo")).containsExactly(ServiceStatus.HEALTHY);
    }

    @Test
    void stoppingOneServiceDoesNotSilenceTheOthers() {
        reporter.expectStopped("mongo");
        reporter.watch("redis");

        reporter.report("mongo", ServiceStatus.HEALTHY);
        reporter.report("redis", ServiceStatus.HEALTHY);

        assertThat(statusesFor("mongo")).isEmpty();
        assertThat(statusesFor("redis")).containsExactly(ServiceStatus.HEALTHY);
    }

    @Test
    void aFailureIsReportedWithWhatDockerSaid() {
        reporter.reportFailure("mongo", "  no such image  ");

        assertThat(statusesFor("mongo")).containsExactly(ServiceStatus.ERROR);
        assertThat(failures).containsExactly("no such image");
    }

    @Test
    void aFailureWithNothingToSayStillTurnsTheServiceRed() {
        reporter.reportFailure("mongo", "   ");

        assertThat(statusesFor("mongo")).containsExactly(ServiceStatus.ERROR);
        assertThat(failures).isEmpty();
    }

    @Test
    void aFailureWithNoDetailsAtAllDoesNotCrash() {
        reporter.reportFailure("mongo", null);

        assertThat(statusesFor("mongo")).containsExactly(ServiceStatus.ERROR);
        assertThat(failures).isEmpty();
    }

    @Test
    void aFailureIsAnnouncedEvenForAServiceThatWasBeingStopped() {
        // A stop that fails is exactly the case the student needs to be told about.
        reporter.expectStopped("mongo");

        reporter.reportFailure("mongo", "permission denied");

        assertThat(statusesFor("mongo")).containsExactly(ServiceStatus.ERROR);
    }

    @Test
    void losingTheDaemonMarksEverythingThatWasBeingWatched() {
        reporter.watch("mongo");
        reporter.expectRunning("redis");
        reporter.expectStopped("neo4j");

        reporter.reportEverythingUnreachable();

        assertThat(statuses).extracting(Reported::key)
                .containsExactlyInAnyOrder("mongo", "redis", "neo4j");
        assertThat(statuses).extracting(Reported::status).containsOnly(ServiceStatus.ERROR);
    }

    @Test
    void losingTheDaemonSaysNothingAboutServicesNobodyAskedAbout() {
        reporter.reportEverythingUnreachable();

        assertThat(statuses).isEmpty();
    }

    @Test
    void aServiceIsWatchedFromTheMomentItIsFirstMentioned() {
        reporter.watch("mongo");
        reporter.expectRunning("redis");
        reporter.expectStopped("neo4j");

        assertThat(reporter.watchedKeys()).containsExactlyInAnyOrder("mongo", "redis", "neo4j");
    }

    @Test
    void watchingTheSameServiceTwiceCountsOnce() {
        reporter.watch("mongo");
        reporter.watch("mongo");

        assertThat(reporter.watchedKeys()).containsExactly("mongo");
    }

    @Test
    void theListOfWatchedServicesCannotBeChangedFromOutside() {
        reporter.watch("mongo");

        assertThat(reporter.watchedKeys()).isUnmodifiable();
    }

    @Test
    void withNoListenerSetNothingBreaks() {
        ServiceStatusReporter bare = new ServiceStatusReporter(Runnable::run);

        bare.watch("mongo");
        bare.report("mongo", ServiceStatus.HEALTHY);
        bare.reportFailure("mongo", "algo");
        bare.reportEverythingUnreachable();
    }
}
