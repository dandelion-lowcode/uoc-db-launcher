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
     * Where the services are described. The path is relative to where the application is
     * run from, which is the project root while it is run from source.
     */
    public static final String COMPOSE_FILE = "docker/docker-compose.yml";
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
