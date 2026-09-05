package com.uoc.docker;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Driven with a fake process, so every answer Docker can give is exercised without
 * needing a machine that happens to be missing the right image.
 *
 * <p>
 * Partitions: the image is held, the image is missing, the compose file cannot be read,
 * and the answers that are not an image name at all.
 */
class ImageAvailabilityTest {

    /** Replies according to what is being asked, and records every command. */
    private static final class FakeDocker implements ProcessRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private String imageName = "mongo:8.3.8";
        private int configExit;
        private int inspectExit;

        @Override
        public Result run(List<String> command, String stdin) {
            commands.add(List.copyOf(command));
            if (command.contains("config")) {
                return new Result(configExit, imageName);
            }
            return new Result(inspectExit, inspectExit == 0 ? "[{}]" : "No such image");
        }
    }

    private final FakeDocker docker = new FakeDocker();
    private final ImageAvailability availability =
            new ImageAvailability(docker, Path.of("docker-compose.yml"));

    @Test
    void anImageDockerCannotFindHasToBeInstalledFirst() {
        docker.inspectExit = 1;

        assertThat(availability.mustBeInstalled("mongo")).isTrue();
    }

    @Test
    void anImageAlreadyOnTheMachineIsStartedStraightAway() {
        docker.inspectExit = 0;

        assertThat(availability.mustBeInstalled("mongo")).isFalse();
    }

    @Test
    void theImageNameIsReadFromTheComposeFileRatherThanGuessedFromTheServiceName() {
        docker.imageName = "ghcr.io/dandelion-lowcode/uocdb-neo4j-twitter:1.0";
        docker.inspectExit = 1;

        availability.mustBeInstalled("neo4j-twitter");

        assertThat(docker.commands.get(1))
                .containsSubsequence("image", "inspect",
                        "ghcr.io/dandelion-lowcode/uocdb-neo4j-twitter:1.0");
    }

    @Test
    void theQuestionIsAskedOfTheProjectsOwnComposeFile() {
        availability.mustBeInstalled("mongo");

        assertThat(docker.commands.get(0))
                .containsSubsequence("-f", "docker-compose.yml")
                .containsSubsequence("config", "--images", "mongo");
    }

    @Test
    void aComposeFileThatCannotBeReadIsNotReportedAsAnInstall() {
        // Claiming an install that is not happening would leave the panel saying so for
        // as long as the service took to start, which is worse than saying nothing.
        docker.configExit = 1;

        assertThat(availability.mustBeInstalled("mongo")).isFalse();
    }

    @Test
    void anEmptyAnswerIsNotTakenForAnImageName() {
        docker.imageName = "   ";

        assertThat(availability.mustBeInstalled("mongo")).isFalse();
    }

    @Test
    void nothingIsInspectedWhenNoImageNameCameBack() {
        docker.imageName = "";

        availability.mustBeInstalled("mongo");

        assertThat(docker.commands).hasSize(1);
    }

    @Test
    void onlyTheFirstLineIsTakenWhenDockerAnswersWithSeveral() {
        docker.imageName = "redis:8.10.1\ncassandra:5.0.9";
        docker.inspectExit = 1;

        availability.mustBeInstalled("redis");

        assertThat(docker.commands.get(1)).contains("redis:8.10.1")
                .doesNotContain("cassandra:5.0.9");
    }

    @Test
    void theCheckNeverReachesTheNetwork() {
        // "image inspect" answers from what is on the machine. Anything that could pull
        // would turn a question into the very download it is asking about.
        availability.mustBeInstalled("mongo");

        assertThat(docker.commands).allSatisfy(
                command -> assertThat(command).doesNotContain("pull").doesNotContain("run"));
    }
}
