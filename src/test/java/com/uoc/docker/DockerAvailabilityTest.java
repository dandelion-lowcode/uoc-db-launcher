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
    void itIsTheDaemonThatIsAskedRatherThanTheExecutable() {
        // "docker --version" answers on a machine where the daemon is not running, which
        // is exactly the state this exists to catch.
        Docker docker = answering(0, "");

        DockerAvailability.isRunning(docker);

        assertThat(docker.asked).containsExactly(List.of(DockerCommand.EXECUTABLE, "info"));
    }

    @Test
    void theRealCheckAnswersWithoutThrowingWhateverThisMachineHas() {
        assertThat(DockerAvailability.isRunning()).isIn(true, false);
    }
}
