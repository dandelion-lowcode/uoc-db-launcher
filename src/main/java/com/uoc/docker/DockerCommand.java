package com.uoc.docker;

/**
 * Names shared by everything that shells out to Docker. The executable is
 * resolved
 * through the PATH, so it works the same on Windows, macOS and Linux.
 */
public final class DockerCommand {

    public static final String EXECUTABLE = "docker";
    public static final String COMPOSE = "compose";
    /**
     * The compose file's name inside the directory it is unpacked into. Nothing should
     * build a path from this: ask {@link BundledFiles} where the file actually is, since
     * an installed application keeps it under the user's own data directory rather than
     * anywhere near the working directory it happens to be started from.
     */
    public static final String COMPOSE_FILE_NAME = "docker-compose.yml";
    public static final String CONTAINER_PREFIX = "uocdb-";

    private DockerCommand() {
    }

    public static String containerName(String serviceKey) {
        return CONTAINER_PREFIX + serviceKey;
    }

    public static String serviceKey(String containerName) {
        return containerName.startsWith(CONTAINER_PREFIX)
                ? containerName.substring(CONTAINER_PREFIX.length())
                : containerName;
    }

    public static boolean isManagedContainer(String containerName) {
        return containerName != null && containerName.startsWith(CONTAINER_PREFIX);
    }
}
