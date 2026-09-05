package com.uoc.docker;

import com.uoc.i18n.Message;

public enum ServiceStatus {
    STOPPED(Message.STATUS_STOPPED),
    /**
     * The image is not on this machine yet, so Docker is fetching or building it before
     * anything can start. Told apart from {@link #STARTING} because the two take wildly
     * different amounts of time: a start is seconds, and the Twitter graph is half a
     * gigabyte to pull. Shown the same way, a student watching a ten-minute download has
     * no way to tell it from a launcher that has hung.
     */
    INSTALLING(Message.STATUS_INSTALLING),
    STARTING(Message.STATUS_STARTING),
    RUNNING(Message.STATUS_RUNNING),
    HEALTHY(Message.STATUS_HEALTHY),
    UNHEALTHY(Message.STATUS_UNHEALTHY),
    /** Suspended. Docker still calls it running, which is why it needs saying. */
    PAUSED(Message.STATUS_PAUSED),
    /** Coming back up after failing, which for a service that keeps failing never ends. */
    RESTARTING(Message.STATUS_RESTARTING),
    /** Finished with an error rather than because it was asked to. */
    CRASHED(Message.STATUS_CRASHED),
    /**
     * Killed for using more memory than it was allowed. Told apart from every other way
     * of stopping because it is the likeliest on a laptop and the only one the student
     * can do something about, by giving Docker more memory.
     */
    OUT_OF_MEMORY(Message.STATUS_OUT_OF_MEMORY),
    STOPPING(Message.STATUS_STOPPING),
    ERROR(Message.STATUS_ERROR);

    private final Message message;

    ServiceStatus(Message message) {
        this.message = message;
    }

    /**
     * Whether this is a wait that ends by itself.
     *
     * <p>
     * These are the ones that pulse and trail dots, so a download with minutes left in it
     * never looks like an application that has stopped responding. One predicate governs
     * both, because a status that pulsed without trailing dots, or the other way round,
     * would be an inconsistency a student notices without being able to say why.
     *
     * <p>
     * Restarting is movement, and is deliberately left out: a container caught in a
     * restart loop never arrives, and showing it the way a download is shown tells a
     * student to wait for something that will not come.
     */
    public boolean isWaiting() {
        return this == INSTALLING || this == STARTING || this == RUNNING || this == STOPPING;
    }

    public boolean isFailure() {
        return this == UNHEALTHY || this == ERROR || this == CRASHED || this == OUT_OF_MEMORY;
    }

    /** Whether the container is up in some form, which is what decides the button. */
    public boolean isUp() {
        return this == RUNNING || this == HEALTHY || this == UNHEALTHY || this == PAUSED
                || this == RESTARTING;
    }

    public Message message() {
        return message;
    }
}
