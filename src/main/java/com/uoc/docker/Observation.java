package com.uoc.docker;

import java.util.Locale;

/**
 * What Docker reports about a container, as one value.
 *
 * <p>
 * Every constant here was produced against a real daemon rather than taken from a
 * description of one, because the obvious reading of Docker's answer is wrong in two
 * places that matter:
 *
 * <ul>
 * <li>a <em>paused</em> container reports {@code running=true};
 * <li>a container in a <em>restart loop</em> also reports {@code running=true}.
 * </ul>
 *
 * <p>
 * Anything deciding from {@code running} alone therefore shows a paused service, and a
 * service crashing over and over, as though both were working. The {@code status} field
 * tells them apart, so that is what is read here.
 *
 * <p>
 * A container killed for using too much memory is the other one worth naming. It exits
 * like any other, so it reads as merely stopped, and on a laptop running a graph of four
 * million nodes it is the likeliest way for a service to disappear. A student who is told
 * only "stopped" has nothing to act on.
 */
public enum Observation {

    /** No such container: it has never been created, or it has been removed. */
    ABSENT,
    /** Created, never started. */
    CREATED,
    /** Up, with a healthcheck that has not passed yet. */
    HEALTH_STARTING,
    /** Up, and its healthcheck passes. */
    HEALTHY,
    /** Up, and its healthcheck is failing. It may still recover on its own. */
    UNHEALTHY,
    /** Up, and the image defines no healthcheck, so being up is all that can be known. */
    UP_WITHOUT_HEALTHCHECK,
    /** Suspended. Reports {@code running=true} while answering nothing. */
    PAUSED,
    /** Being restarted, which for a service that keeps failing never ends. */
    RESTARTING,
    /** On its way out. */
    REMOVING,
    /** Finished by itself, without complaint. */
    EXITED_OK,
    /** Finished with a non-zero exit code. */
    EXITED_FAILED,
    /** Killed for using more memory than it was allowed. */
    OUT_OF_MEMORY,
    /** Broken in a way Docker cannot undo; it cannot even be removed normally. */
    DEAD,
    /** Docker itself could not be reached, so nothing is known about anything. */
    DAEMON_LOST;

    private static final String CREATED_STATUS = "created";
    private static final String RUNNING_STATUS = "running";
    private static final String PAUSED_STATUS = "paused";
    private static final String RESTARTING_STATUS = "restarting";
    private static final String REMOVING_STATUS = "removing";
    private static final String EXITED_STATUS = "exited";
    private static final String DEAD_STATUS = "dead";

    private static final String HEALTH_STARTING_VALUE = "starting";
    private static final String HEALTHY_VALUE = "healthy";
    private static final String UNHEALTHY_VALUE = "unhealthy";

    /**
     * Reads one container's state.
     *
     * <p>
     * The arguments are the plain values Docker answers with rather than the library's
     * own types, so that every combination can be exercised without a daemon that happens
     * to be in the right condition.
     *
     * @param status    the {@code State.Status} field: created, running, paused,
     *                  restarting, removing, exited or dead
     * @param oomKilled the {@code State.OOMKilled} field
     * @param exitCode  the {@code State.ExitCode} field
     * @param health    the healthcheck result, or {@code null} when the image defines
     *                  none
     * @return never {@code null}; an unrecognised status reads as {@link #ABSENT}, the
     *         one answer that leads nowhere harmful
     */
    public static Observation of(String status, boolean oomKilled, Integer exitCode,
            String health) {
        String state = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);

        // Asked before the exit itself, which it looks exactly like: a container killed
        // for its memory use has exited, with a code, and only this flag says why.
        if (oomKilled) {
            return OUT_OF_MEMORY;
        }

        return switch (state) {
            case CREATED_STATUS -> CREATED;
            case RUNNING_STATUS -> whileUp(health);
            case PAUSED_STATUS -> PAUSED;
            case RESTARTING_STATUS -> RESTARTING;
            case REMOVING_STATUS -> REMOVING;
            case DEAD_STATUS -> DEAD;
            case EXITED_STATUS -> exitCode != null && exitCode != 0 ? EXITED_FAILED : EXITED_OK;
            default -> ABSENT;
        };
    }

    private static Observation whileUp(String health) {
        if (health == null || health.isBlank()) {
            return UP_WITHOUT_HEALTHCHECK;
        }
        return switch (health.trim().toLowerCase(Locale.ROOT)) {
            case HEALTHY_VALUE -> HEALTHY;
            case UNHEALTHY_VALUE -> UNHEALTHY;
            case HEALTH_STARTING_VALUE -> HEALTH_STARTING;
            // A healthcheck Docker describes in words nobody here knows is no answer, and
            // being up is what is left that can be said honestly.
            default -> UP_WITHOUT_HEALTHCHECK;
        };
    }

    /** Whether the container is up in some form, however badly it is behaving. */
    public boolean isUp() {
        return this == HEALTH_STARTING || this == HEALTHY || this == UNHEALTHY
                || this == UP_WITHOUT_HEALTHCHECK || this == PAUSED || this == RESTARTING;
    }
}
