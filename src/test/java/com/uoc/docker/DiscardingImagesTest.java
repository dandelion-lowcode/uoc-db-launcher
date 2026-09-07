package com.uoc.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Giving back the disk a service was using.
 *
 * <p>
 * Eleven images is a good many gigabytes on a laptop, so a service the student has put
 * away takes its image with it. The order is the whole of the difficulty: Docker refuses
 * to remove an image while any container still refers to it, a stopped one included, so
 * the stop has to have finished before the container goes and the container before the
 * image.
 */
@DisplayName("putting a service away and getting the disk back")
class DiscardingImagesTest {

    private static final String SERVICE = "redis";
    private static final Duration LIMIT = Duration.ofSeconds(10);

    private final List<List<String>> commands = new CopyOnWriteArrayList<>();
    private DockerManager manager;

    @AfterEach
    void stopListening() {
        if (manager != null) {
            manager.close();
        }
    }

    private DockerManager managerFor() {
        manager = new DockerManager((command, stdin) -> {
            commands.add(List.copyOf(command));
            if (command.contains("config")) {
                return new ProcessRunner.Result(0, "redis:8.10.1\n");
            }
            return new ProcessRunner.Result(0, "");
        }, Runnable::run, Path.of("docker-compose.yml"));
        return manager;
    }

    @Test
    void theImageGoesWhenTheServiceIsPutAway() {
        managerFor();

        manager.stopAndDiscardImage(SERVICE);

        awaitCommandContaining("image");
        assertThat(commands).anySatisfy(command ->
                assertThat(command).containsSequence("image", "rm", "redis:8.10.1"));
    }

    @Test
    void inTheOrderDockerInsistsOn() {
        // Stop, then the container, then the image. Any other order is refused, and the
        // refusal is quiet: the image simply stays on the disk.
        managerFor();

        manager.stopAndDiscardImage(SERVICE);

        awaitCommandContaining("image");
        assertThat(indexOfCommandContaining("stop"))
                .isLessThan(indexOfCommandContaining("rm"));
        assertThat(indexOfCommandContaining("rm"))
                .isLessThan(indexOfCommandContaining("image"));
    }

    @Test
    void aPlainStopLeavesTheImageWhereItIs() {
        // The button beside a service keeps the student's place. Coming back to something
        // they only paused must not be a download.
        managerFor();

        manager.stop(SERVICE);

        awaitCommandContaining("stop");
        quietMoment();
        assertThat(commands).noneSatisfy(command ->
                assertThat(command).contains("image", "rm"));
    }

    @Test
    void puttingOneAwayDoesNotTakeTheNextOnesImageWithIt() {
        // The mark is per service and is cleared as it is used, or the next stop of any
        // service would take its image too.
        managerFor();
        manager.stopAndDiscardImage(SERVICE);
        awaitCommandContaining("image");
        commands.clear();

        manager.stop(SERVICE);

        awaitCommandContaining("stop");
        quietMoment();
        assertThat(commands).noneSatisfy(command -> assertThat(command).contains("image"));
    }

    private void awaitCommandContaining(String word) {
        Instant deadline = Instant.now().plus(LIMIT);
        while (Instant.now().isBefore(deadline)
                && commands.stream().noneMatch(command -> command.contains(word))) {
            pause(20);
        }
        assertThat(commands).as("no command ever mentioned %s", word)
                .anySatisfy(command -> assertThat(command).contains(word));
    }

    /** Long enough that anything else that was going to run would have. */
    private void quietMoment() {
        pause(300);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private int indexOfCommandContaining(String word) {
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).contains(word)) {
                return i;
            }
        }
        return -1;
    }
}
