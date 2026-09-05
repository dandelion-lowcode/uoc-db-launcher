package com.uoc.docker;

import java.nio.file.Path;
import java.util.List;

/**
 * Whether the image a service needs is already on this machine.
 *
 * <p>
 * It decides between two waits that look identical to a student but are not: starting a
 * service whose image is already here takes seconds, while fetching the Twitter graph is
 * half a gigabyte and building Riak or Jupyter is minutes of work. Reported the same way,
 * a long one is indistinguishable from a launcher that has stopped responding.
 *
 * <p>
 * The image name is read from the compose file rather than repeated here, so it stays
 * right when a version is pinned to something new.
 */
public class ImageAvailability {

    private final ProcessRunner processRunner;
    private final Path composeFile;

    public ImageAvailability(ProcessRunner processRunner, Path composeFile) {
        this.processRunner = processRunner;
        this.composeFile = composeFile;
    }

    /**
     * Whether starting this service means fetching or building its image first.
     *
     * @param key the compose service name
     * @return true only when the image is known to be missing. Anything that cannot be
     *         determined answers false: claiming an install that is not happening would
     *         leave the panel saying so until the service was up, and a plain start
     *         reported as a start is the harmless way to be wrong.
     */
    public boolean mustBeInstalled(String key) {
        String image = imageFor(key);
        if (image == null) {
            return false;
        }
        // "image inspect" fails precisely when the image is not held locally, which is
        // the question being asked. It never reaches the network.
        return processRunner.run(
                List.of(DockerCommand.EXECUTABLE, "image", "inspect", image), null).failed();
    }

    private String imageFor(String key) {
        ProcessRunner.Result result = processRunner.run(List.of(
                DockerCommand.EXECUTABLE, DockerCommand.COMPOSE,
                "-f", composeFile.toString(), "config", "--images", key), null);
        if (result.failed()) {
            return null;
        }
        // One service, so one image, but the command answers with a line per image.
        String output = result.output().strip();
        if (output.isEmpty()) {
            return null;
        }
        return output.lines().findFirst().map(String::strip).filter(line -> !line.isEmpty())
                .orElse(null);
    }
}
