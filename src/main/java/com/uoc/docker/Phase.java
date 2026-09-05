package com.uoc.docker;

/**
 * Whether a command is running against a service right now, and which.
 *
 * <p>
 * It exists because a container that does not exist yet and one that has stopped are the
 * same answer from Docker. While an image is being fetched there is nothing to inspect,
 * and the poll that runs every few seconds would otherwise report a download with minutes
 * left to run as "stopped", over and over.
 */
public enum Phase {
    /** No command in flight; Docker's answer is the whole story. */
    IDLE,
    /** The image is being fetched or built, so no container exists yet. */
    INSTALLING,
    /** The image is here and the container is being created and started. */
    STARTING,
    /** The service is being taken down. */
    STOPPING
}
