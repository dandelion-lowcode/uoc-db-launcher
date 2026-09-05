package com.uoc.docker;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Keeps what is known about each service, and announces what the student should see.
 *
 * <p>
 * It holds a {@link ServiceState} per service and lets three things change it
 * independently: what the student asked for, what command is running, and what Docker
 * reports. After any of them, {@link ServiceState#displayed()} decides, so no rule about
 * how the three combine lives here.
 *
 * <p>
 * It used to work the other way round: each caller announced a status, and two sets of
 * keys suppressed the announcements known to be wrong. Every new situation added another
 * guard, and the guards could not be reasoned about together.
 *
 * <p>
 * A status is only delivered when it differs from the last one, so the poll that runs
 * every few seconds does not repaint a service that has not moved.
 */
public class ServiceStatusReporter {

    public interface StatusListener {
        void onStatusChanged(String key, ServiceStatus status);
    }

    /** Receives what Docker said when a service could not be started or stopped. */
    public interface FailureListener {
        void onFailure(String key, String details);
    }

    /**
     * Receives how an install is going, as often as Docker says anything.
     *
     * <p>
     * The text is the whole of what should be on screen, not an addition to it: fetching
     * an image means the same handful of lines counting up, so each report replaces the
     * last rather than being appended to it.
     */
    public interface ProgressListener {
        void onProgress(String key, String text);
    }

    private final Consumer<Runnable> dispatcher;
    private final Map<String, ServiceState> states = new ConcurrentHashMap<>();
    private final Map<String, ServiceStatus> announced = new ConcurrentHashMap<>();

    private volatile StatusListener listener = (key, status) -> {
    };
    private volatile FailureListener failureListener = (key, details) -> {
    };
    private volatile ProgressListener progressListener = (key, text) -> {
    };

    /**
     * @param dispatcher runs each notification where the listener expects to be called,
     *                   which in the application is the interface thread
     */
    public ServiceStatusReporter(Consumer<Runnable> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void setListener(StatusListener listener) {
        this.listener = listener;
    }

    public void setFailureListener(FailureListener failureListener) {
        this.failureListener = failureListener;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    /** Passes on how an install is going, on the thread the listener expects. */
    public void reportProgress(String key, String text) {
        dispatcher.accept(() -> progressListener.onProgress(key, text));
    }

    /** Remembers a service, so it keeps being polled and is told about a lost daemon. */
    public void watch(String key) {
        states.computeIfAbsent(key, k -> ServiceState.unknown());
    }

    public Set<String> watchedKeys() {
        return Set.copyOf(states.keySet());
    }

    /** What is currently known about a service. */
    public ServiceState stateOf(String key) {
        return states.getOrDefault(key, ServiceState.unknown());
    }

    /**
     * The student has asked for something, so the last command's failure stops being what
     * they need to see. What they asked for is not kept: the command about to run says it
     * better, and an intent that outlives its command only starts lying in the other
     * direction.
     */
    public void retrying(String key) {
        change(key, ServiceState::retrying);
    }

    /** A command has begun against this service, or has finished. */
    public void enterPhase(String key, Phase phase) {
        change(key, state -> state.withPhase(phase));
    }

    /** Docker has been asked about this service and answered. */
    public void observe(String key, Observation observation) {
        change(key, state -> state.withObservation(observation));
    }

    /**
     * Reports a service as failed, along with whatever Docker said about it.
     *
     * <p>
     * This is the launcher's own failure to run a command rather than anything read off a
     * container, so it is announced directly instead of through the state.
     */
    public void reportFailure(String key, String details) {
        change(key, ServiceState::withCommandFailed);
        if (details != null && !details.isBlank()) {
            dispatcher.accept(() -> failureListener.onFailure(key, details.strip()));
        }
    }

    /** The daemon is gone, so nothing that is known can be described any more. */
    public void reportEverythingUnreachable() {
        for (String key : Set.copyOf(states.keySet())) {
            change(key, state -> state.withObservation(Observation.DAEMON_LOST));
        }
    }

    private void change(String key, UnaryOperator<ServiceState> how) {
        ServiceState updated = states.compute(key,
                (k, current) -> how.apply(current == null ? ServiceState.unknown() : current));
        deliver(key, updated.displayed());
    }

    /** Announces a status, unless it is the one already showing. */
    private void deliver(String key, ServiceStatus status) {
        ServiceStatus previous = announced.put(key, status);
        if (status != previous) {
            dispatcher.accept(() -> listener.onStatusChanged(key, status));
        }
    }
}
