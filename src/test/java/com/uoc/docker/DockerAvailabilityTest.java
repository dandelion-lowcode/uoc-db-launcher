package com.uoc.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The check that decides whether there is anything to open a window for.
 *
 * <p>
 * It is the first thing the launcher does and the only one whose answer ends the process,
 * so each way it can be answered is worth pinning: a student who has Docker installed but
 * not started must be told to start it, and a machine with no Docker at all must be told
 * the same rather than shown a stack trace.
 */
@DisplayName("whether Docker is there to talk to")
class DockerAvailabilityTest {

    /** Answers one command one way, and remembers what it was asked. */
    private static final class Docker implements ProcessRunner {

        private final List<List<String>> asked = new ArrayList<>();
        private final Result answer;
        private final RuntimeException thrown;

        private Docker(Result answer, RuntimeException thrown) {
            this.answer = answer;
            this.thrown = thrown;
        }

        @Override
        public Result run(List<String> command, String stdin) {
            asked.add(List.copyOf(command));
            if (thrown != null) {
                throw thrown;
            }
            return answer;
        }
    }

    private static Docker answering(int exitCode, String output) {
        return new Docker(new ProcessRunner.Result(exitCode, output), null);
    }

    @Test
    void aDaemonThatAnswersIsRunning() {
        assertThat(DockerAvailability.isRunning(answering(0, "Server Version: 27.0.3\n")))
                .isTrue();
    }

    @Test
    void installedButNotStartedIsNotRunning() {
        // What Docker Desktop says while it is closed. The exit code is the whole of the
        // answer; the wording changes between versions and is not read.
        assertThat(DockerAvailability.isRunning(answering(1,
                "error during connect: this error may indicate that the docker daemon is"
                        + " not running")))
                .isFalse();
    }

    @Test
    void notInstalledAtAllIsNotRunningRatherThanAFailure() {
        // No executable to run means the runner throws rather than returning an exit
        // code, and this is the very first thing the launcher does.
        assertThat(DockerAvailability.isRunning(
                new Docker(null, new IllegalStateException("Cannot run program \"docker\""))))
                .isFalse();
    }

    @Test
    void aDaemonThatAnswersReportsRunning() {
        assertThat(DockerAvailability.status(answering(0, "Server Version: 27.0.3\n")))
                .isEqualTo(DockerAvailability.Status.RUNNING);
    }

    @Test
    void dockerClosedIsToldApartFromDockerAbsent() {
        // The two want opposite things of the student -- start Docker, or end a session
        // that predates installing it -- so the check has to distinguish them. A command
        // that runs and fails is a daemon that is not up.
        assertThat(DockerAvailability.status(answering(1,
                "error during connect: this error may indicate that the docker daemon is"
                        + " not running")))
                .isEqualTo(DockerAvailability.Status.NOT_STARTED);

        // A command that cannot be run at all is Docker missing from the PATH, which is
        // what a machine looks like while Docker Desktop is open in a session that was
        // started before Docker was installed.
        assertThat(DockerAvailability.status(
                new Docker(null, new IllegalStateException("Cannot run program \"docker\""))))
                .isEqualTo(DockerAvailability.Status.NOT_FOUND);
    }

    @Test
    void theMissingExecutableIsRecognisedTheWayTheRealRunnerReportsIt() {
        // The runner the application is given does not throw when a program cannot be
        // started: it catches it and answers with a negative exit code, which is what
        // ProcessRunner.Result documents and what SystemProcessRunnerTest pins. Read
        // through an exception alone, NOT_FOUND was unreachable outside this test class
        // -- every machine without docker on its PATH would have been told the daemon
        // was not running, which is the confusion the three states exist to end.
        assertThat(DockerAvailability.status(
                answering(-1, "Error: Cannot run program \"docker\": CreateProcess error=2")))
                .isEqualTo(DockerAvailability.Status.NOT_FOUND);
    }

    @Test
    void aDaemonThatRefusesIsStillToldApartFromAMissingExecutable() {
        // Docker's own failures come back with a real exit code, and those mean the
        // daemon is there to be started.
        assertThat(DockerAvailability.status(answering(1, "cannot connect")))
                .isEqualTo(DockerAvailability.Status.NOT_STARTED);
    }

    @Test
    void itIsTheDaemonThatIsAskedRatherThanTheExecutable() {
        // "docker --version" answers on a machine where the daemon is not running, which
        // is exactly the state this exists to catch.
        Docker docker = answering(0, "");

        DockerAvailability.isRunning(docker);

        assertThat(docker.asked).containsExactly(List.of(DockerCommand.EXECUTABLE, "info"));
    }

    @Test
    void theMessageWrapsInsteadOfStretchingAcrossTheScreen() {
        // A JEditorPane with no width asks for its longest line unbroken, and the
        // "Docker not found" message is three sentences long: the dialog came out as a
        // strip the full width of the display with one line of text in it.
        String html = new com.uoc.i18n.Translations(java.util.Locale.of("es"))
                .format(com.uoc.i18n.Message.DIALOG_DOCKER_NOT_FOUND_MESSAGE,
                        "https://docs.docker.com/get-started/get-docker/");

        javax.swing.JEditorPane pane = DockerAvailability.messagePane(html);

        assertThat(pane.getPreferredSize().width).isLessThanOrEqualTo(600);
        assertThat(pane.getPreferredSize().height)
                .as("wrapped text is taller than one line")
                .isGreaterThan(20);
    }

    @Test
    void theRealCheckAnswersWithoutThrowingWhateverThisMachineHas() {
        assertThat(DockerAvailability.isRunning()).isIn(true, false);
    }
}
