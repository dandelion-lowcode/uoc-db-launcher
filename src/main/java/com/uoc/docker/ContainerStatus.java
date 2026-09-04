package com.uoc.docker;

import java.util.Locale;

/**
 * Translates what Docker reports about a container into the status the
 * interface shows.
 *
 * <p>
 * The rules live apart from the code that talks to Docker so they can be
 * exercised
 * without a running daemon, which is what the two subtleties here deserve:
 * Docker keeps
 * the last health result after a container exits, and a container that is up
 * but has not
 * passed its healthcheck yet is still loading rather than ready.
 */
public final class ContainerStatus {

    public static final String HEALTHY = "healthy";
    public static final String UNHEALTHY = "unhealthy";

    private static final String HEALTH_EVENT_PREFIX = "health_status:";
    private static final String STARTED = "start";
    private static final String DIED = "die";
    private static final String STOPPED = "stop";

    private ContainerStatus() {
    }

    /**
     * The status of a container from its inspected state.
     *
     * @param running whether Docker reports the container as running
     * @param health  the healthcheck result, or {@code null} when the image defines
     *                none
     * @return never {@code null}
     */
    public static ServiceStatus fromState(boolean running, String health) {
        // Health is only meaningful while the container runs: Docker retains the last
        // result after it exits, which would otherwise show a stopped service as
        // failed.
        if (!running) {
            return ServiceStatus.STOPPED;
        }
        if (isHealth(health, HEALTHY)) {
            return ServiceStatus.HEALTHY;
        }
        if (isHealth(health, UNHEALTHY)) {
            return ServiceStatus.UNHEALTHY;
        }
        // Up, but either still starting its healthcheck or without one at all.
        return ServiceStatus.RUNNING;
    }

    /**
     * The status announced by a container event.
     *
     * @param action the event action, such as {@code start} or
     *               {@code health_status: healthy}
     * @return the status the event reports, or {@code null} when it reports none
     */
    public static ServiceStatus fromEvent(String action) {
        if (action == null) {
            return null;
        }
        String normalized = action.trim().toLowerCase(Locale.ROOT);

        if (normalized.startsWith(HEALTH_EVENT_PREFIX)) {
            String health = normalized.substring(HEALTH_EVENT_PREFIX.length()).trim();
            if (health.equals(HEALTHY)) {
                return ServiceStatus.HEALTHY;
            }
            if (health.equals(UNHEALTHY)) {
                return ServiceStatus.UNHEALTHY;
            }
            return null;
        }

        switch (normalized) {
            case STARTED:
                return ServiceStatus.RUNNING;
            case DIED:
            case STOPPED:
                return ServiceStatus.STOPPED;
            default:
                return null;
        }
    }

    private static boolean isHealth(String health, String expected) {
        return health != null && health.trim().toLowerCase(Locale.ROOT).equals(expected);
    }
}
