package com.uoc.docker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The manager driven by a stand-in for the docker command line.
 *
 * <p>Both of the questions it puts to Docker are exercised here: the event stream it
 * holds open for as long as the launcher runs, and the inspect it answers every question
 * about a container with. Neither needs a daemon, which is the point of asking through
 * the command line at all -- the event stream used to go through a client library, and
 * the only way to test it was to aim a real client at a port nothing was listening on.
 *
 * <p>The commands themselves are pinned rather than only their effects, because a
 * template that stops printing the field it promises fails silently: Docker answers, the
 * answer is unreadable, and every service reads as absent.
 */
@DisplayName("what the manager asks Docker, and what it makes of the answer")
class DockerManagerTest {

    private static final String SERVICE = "redis";
    private static final String CONTAINER = "uocdb-redis";

    /** Long enough to cover a reconnect, which is deliberately unhurried. */
    private static final int LIMIT_SECONDS = 30;

    /** Long enough that an event that was going to be acted on would have been. */
    private static final int QUIET_MILLIS = 500;

    private record Announcement(String key, ServiceStatus status) {
    }

    /**
     * Answers Docker commands, and hands the test the event stream to write on.
     *
     * <p>Every command but an inspect succeeds with nothing to say, which is what compose
     * does when it works.
     */
    private static final class FakeDocker implements ProcessRunner {

        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private final BlockingQueue<List<String>> streams = new LinkedBlockingQueue<>();

        private volatile Consumer<String> onLine = line -> {
        };
        private volatile Runnable onEnded = () -> {
        };
        private volatile Result inspectAnswer = new Result(0, "running\tfalse\t0\thealthy\n");

        @Override
        public Result run(List<String> command, String stdin) {
            commands.add(List.copyOf(command));
            return command.contains("inspect") ? inspectAnswer : new Result(0, "");
        }

        @Override
        public LiveProcess stream(List<String> command, Consumer<String> lines, Runnable ended) {
            this.onLine = lines;
            this.onEnded = ended;
            streams.add(List.copyOf(command));
            return () -> {
            };
        }

        void emit(String event) {
            onLine.accept(event);
        }

        /** The daemon going away, which is the only thing that ends the stream. */
        void endTheStream() {
            onEnded.run();
        }

        List<List<String>> inspects() {
            return commands.stream().filter(command -> command.contains("inspect")).toList();
        }
    }

    private final FakeDocker docker = new FakeDocker();
    private final BlockingQueue<Announcement> announced = new LinkedBlockingQueue<>();
    private DockerManager manager;

    @BeforeEach
    void watchWhatIsAnnounced() {
        // The statuses are collected where they are decided rather than on the interface
        // thread, so the test can read them directly.
        manager = new DockerManager(docker, Runnable::run, Path.of("docker-compose.yml"));
        manager.setListener((key, status) -> announced.add(new Announcement(key, status)));
    }

    @AfterEach
    void stopListening() {
        manager.close();
    }

    @Test
    void theEventStreamIsAskedForOneLinePerContainerEvent() {
        manager.start();

        assertThat(nextStream()).containsExactly("docker", "events",
                "--filter", "type=container",
                "--format", "{{.Actor.Attributes.name}}\t{{.Action}}");
    }

    @Test
    void anEventAboutAServiceIsAnsweredByLookingAtTheContainerItself() {
        // An event says something changed, not what the container now is: it cannot
        // report a healthcheck the image does not define, nor tell a pause from a restart
        // loop. What proves it here is that "start" is reported as healthy, which no
        // reading of the verb alone could arrive at.
        manager.start();

        docker.emit(CONTAINER + "\tstart");

        assertThat(next()).isEqualTo(new Announcement(SERVICE, ServiceStatus.HEALTHY));
    }

    @Test
    void aHealthEventCountsEvenThoughItCarriesItsOwnResult() {
        // These arrive as "health_status: healthy", so the verb has to be read apart from
        // what follows it.
        manager.start();

        docker.emit(CONTAINER + "\thealth_status: healthy");

        assertThat(next()).isEqualTo(new Announcement(SERVICE, ServiceStatus.HEALTHY));
    }

    @Test
    void everyExecIsIgnoredRatherThanLookedAt() {
        // Running a query is an exec, and so is every healthcheck of every service, which
        // together are several events a second. A student typing would re-inspect on
        // every line.
        manager.start();

        docker.emit(CONTAINER + "\texec_start: redis-cli ping");
        docker.emit(CONTAINER + "\texec_die");

        assertThat(nothingFollows()).isTrue();
        assertThat(docker.inspects()).isEmpty();
    }

    @Test
    void aContainerThatIsNotTheLaunchersIsIgnored() {
        // A student's own containers are on the same daemon and report the same events.
        manager.start();

        docker.emit("postgres\tstart");

        assertThat(nothingFollows()).isTrue();
        assertThat(docker.inspects()).isEmpty();
    }

    @Test
    void anEventStreamThatEndsIsReportedAndThenAskedForAgain() {
        manager.start();
        nextStream();
        manager.refreshStatus(SERVICE);
        assertThat(next()).isEqualTo(new Announcement(SERVICE, ServiceStatus.HEALTHY));

        docker.endTheStream();

        assertThat(next())
                .as("a daemon that has gone away leaves nothing known about anything")
                .isEqualTo(new Announcement(SERVICE, ServiceStatus.ERROR));
        assertThat(nextStream())
                .as("the launcher has to reconnect by itself, since a student who "
                        + "restarts Docker Desktop will not restart the launcher too")
                .isNotNull();
    }

    @Test
    void aContainerIsAskedForTheFourFieldsThatDecideAnythingAndNothingElse() {
        // The whole of an inspect is some thirty kilobytes of JSON a service; these four
        // are all that is read out of it.
        manager.refreshStatus(SERVICE);
        next();

        assertThat(docker.inspects()).containsExactly(List.of("docker", "inspect", "--format",
                "{{.State.Status}}\t{{.State.OOMKilled}}\t{{.State.ExitCode}}"
                        + "\t{{if .State.Health}}{{.State.Health.Status}}{{end}}",
                CONTAINER));
    }

    @Test
    void aContainerUpWithNoHealthcheckLeavesTheLastFieldEmpty() {
        // Jupyter's image defines none, and the template prints nothing rather than the
        // word "null", so the line ends in a separator with nothing after it.
        assertRead(new ProcessRunner.Result(0, "running\tfalse\t0\t\n"), ServiceStatus.HEALTHY);
    }

    @Test
    void aContainerStillStartingUpIsNotYetReady() {
        assertRead(new ProcessRunner.Result(0, "running\tfalse\t0\tstarting\n"),
                ServiceStatus.RUNNING);
    }

    @Test
    void aPausedContainerIsNotConfusedForARunningOne() {
        assertRead(new ProcessRunner.Result(0, "paused\tfalse\t0\t\n"), ServiceStatus.PAUSED);
    }

    @Test
    void aContainerThatKeepsRestartingIsNotConfusedForARunningOne() {
        assertRead(new ProcessRunner.Result(0, "restarting\tfalse\t0\t\n"),
                ServiceStatus.RESTARTING);
    }

    @Test
    void aContainerThatDiedWithACodeIsShownAsCrashed() {
        assertRead(new ProcessRunner.Result(0, "exited\tfalse\t137\t\n"), ServiceStatus.CRASHED);
    }

    @Test
    void aContainerTheKernelKilledForItsMemoryIsSaidSoRatherThanCalledCrashed() {
        // On a laptop running a graph of four million nodes this is the likeliest way for
        // a service to disappear, and it exits like any other, so only the flag says why.
        assertRead(new ProcessRunner.Result(0, "exited\ttrue\t137\t\n"),
                ServiceStatus.OUT_OF_MEMORY);
    }

    @Test
    void aContainerThatIsNotThereIsStoppedRatherThanBroken() {
        // Docker fails the inspect either way, and only the message separates a container
        // that has never been created from a daemon that cannot be reached. Saying the
        // wrong one leaves the student with no button to press.
        assertRead(new ProcessRunner.Result(1, "error: no such object: " + CONTAINER),
                ServiceStatus.STOPPED);
    }

    @Test
    void anInspectThatDoesNotReachTheDaemonAtAllSaysNothingAboutTheContainer() {
        assertRead(new ProcessRunner.Result(1, "Cannot connect to the Docker daemon at "
                + "unix:///var/run/docker.sock. Is the docker daemon running?"),
                ServiceStatus.ERROR);
    }

    @Test
    void anAnswerInAShapeNobodyHereKnowsLeadsNowhereHarmful() {
        // A template that stopped printing what it promises would arrive like this, and
        // "stopped" is the reading that leaves the student able to press start.
        assertRead(new ProcessRunner.Result(0, "<no value>\n"), ServiceStatus.STOPPED);
    }

    private void assertRead(ProcessRunner.Result answer, ServiceStatus expected) {
        docker.inspectAnswer = answer;

        manager.refreshStatus(SERVICE);

        assertThat(next()).isEqualTo(new Announcement(SERVICE, expected));
    }

    /** The next status announced, or a failure if none arrives. */
    private Announcement next() {
        Announcement announcement = poll(announced, LIMIT_SECONDS, TimeUnit.SECONDS);
        assertThat(announcement).as("no status was ever announced").isNotNull();
        return announcement;
    }

    /** Whether nothing at all is announced, which is what ignoring an event looks like. */
    private boolean nothingFollows() {
        return poll(announced, QUIET_MILLIS, TimeUnit.MILLISECONDS) == null;
    }

    private List<String> nextStream() {
        List<String> command = poll(docker.streams, LIMIT_SECONDS, TimeUnit.SECONDS);
        assertThat(command).as("no event stream was ever asked for").isNotNull();
        return command;
    }

    private static <T> T poll(BlockingQueue<T> queue, long timeout, TimeUnit unit) {
        try {
            return queue.poll(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
