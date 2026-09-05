package com.uoc.docker;

/**
 * Everything known about one service, and the single place that decides what is shown.
 *
 * <p>
 * Two things bear on it, and until this existed they were scattered: whether a command is
 * running against the service right now, and what Docker reports. Each was handled where
 * it happened to arrive, as a guard beside the code that noticed it, and the guards did
 * not compose. Two faults came straight out of that: a service being downloaded was
 * reported as stopped every few seconds, because a container that does not exist yet
 * answers exactly like one that has stopped; and a service in a restart loop was shown as
 * running, because Docker says {@code running=true} throughout.
 *
 * <p>
 * There was a third thing here for a while: what the student had last asked for. It was
 * removed once it turned out to decide nothing. Every case that seemed to need it -- a
 * healthcheck landing late during a stop, a container created but not yet started -- is
 * already covered by the command in flight, and outside a command Docker is simply right.
 * Keeping it only added a way to be wrong: a service stopped here and then started from a
 * terminal read as "stopping" for as long as the launcher stayed open.
 *
 * @param phase         the command in flight, if any
 * @param observation   what Docker reports
 * @param commandFailed whether the last command the launcher ran was refused
 */
public record ServiceState(Phase phase, Observation observation, boolean commandFailed) {

    /** Nothing running, nothing there. */
    public static ServiceState unknown() {
        return new ServiceState(Phase.IDLE, Observation.ABSENT, false);
    }

    public ServiceState withPhase(Phase newPhase) {
        return new ServiceState(newPhase, observation, commandFailed);
    }

    public ServiceState withObservation(Observation newObservation) {
        return new ServiceState(phase, newObservation, commandFailed);
    }

    /** Docker refused the command the launcher ran. */
    public ServiceState withCommandFailed() {
        return new ServiceState(phase, observation, true);
    }

    /**
     * A fresh request from the student clears the failure of the last one: they are
     * trying again, and last time's error is no longer what they need to see.
     */
    public ServiceState retrying() {
        return new ServiceState(phase, observation, false);
    }

    /**
     * What the student is shown.
     *
     * <p>
     * The order of the rules is the whole of the logic, and each one is here because
     * without it something would be reported wrongly:
     *
     * <ol>
     * <li>A daemon that cannot be reached makes every other answer meaningless.
     * <li>An install is believed over anything Docker says, because during it there is no
     * container to ask about and the answer is always "no such container".
     * <li>A stop in progress likewise, so a healthcheck landing late cannot flash green
     * over a service on its way down.
     * <li>While starting, a container that is absent or merely created is still starting
     * rather than stopped. Once it is up, what it reports wins, so a service that
     * becomes healthy says so without waiting for the command to return.
     * <li>Otherwise Docker is simply believed.
     * </ol>
     */
    public ServiceStatus displayed() {
        if (observation == Observation.DAEMON_LOST || observation == Observation.DEAD) {
            return ServiceStatus.ERROR;
        }
        // A command that was refused outweighs anything the container says, as long as
        // the container is not somehow up anyway. Without this the failure is announced
        // and then overwritten a moment later by the reconciliation that follows it, and
        // the student sees the service settle back to "stopped" with no reason given.
        if (commandFailed && !observation.isUp()) {
            return ServiceStatus.ERROR;
        }
        if (phase == Phase.INSTALLING) {
            return ServiceStatus.INSTALLING;
        }
        if (phase == Phase.STOPPING) {
            return ServiceStatus.STOPPING;
        }
        // Absent or merely created is still on its way while a start is running; once the
        // container is up, what it reports wins, so a service that becomes healthy says
        // so without waiting for the command to return.
        if (phase == Phase.STARTING
                && (observation == Observation.ABSENT || observation == Observation.CREATED)) {
            return ServiceStatus.STARTING;
        }
        return fromObservation();
    }

    private ServiceStatus fromObservation() {
        return switch (observation) {
            case ABSENT, EXITED_OK -> ServiceStatus.STOPPED;
            // Created but never started, with nothing running: it is not going anywhere.
            case CREATED -> ServiceStatus.STOPPED;
            case HEALTH_STARTING -> ServiceStatus.RUNNING;
            case HEALTHY -> ServiceStatus.HEALTHY;
            case UNHEALTHY -> ServiceStatus.UNHEALTHY;
            // No healthcheck means being up is everything that can be known, so it is
            // taken as ready. This is what Jupyter relies on, and it falls out of the
            // observation rather than being a rule about that one service.
            case UP_WITHOUT_HEALTHCHECK -> ServiceStatus.HEALTHY;
            case PAUSED -> ServiceStatus.PAUSED;
            case RESTARTING -> ServiceStatus.RESTARTING;
            case REMOVING -> ServiceStatus.STOPPING;
            case EXITED_FAILED -> ServiceStatus.CRASHED;
            case OUT_OF_MEMORY -> ServiceStatus.OUT_OF_MEMORY;
            case DEAD, DAEMON_LOST -> ServiceStatus.ERROR;
        };
    }
}
