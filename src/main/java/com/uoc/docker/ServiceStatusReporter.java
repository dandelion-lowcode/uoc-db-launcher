package com.uoc.docker;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Decides which of the statuses Docker reports are worth showing, and delivers
 * them.
 *
 * <p>
 * Docker describes what a container is doing; the panel has to describe what
 * the
 * student asked for. The two disagree while a service shuts down, because a
 * healthcheck
 * already in flight reports success after the stop was requested. Announcing
 * that would
 * flash a green "running" over a service on its way out, which is what this
 * guards.
 *
 * <p>
 * The rules are kept away from the client library and from Swing so they can be
 * exercised on their own, with the delivery of each status observed directly.
 */
public class ServiceStatusReporter {

    public interface StatusListener {
        void onStatusChanged(String key, ServiceStatus status);
    }

    /** Receives what Docker said when a service could not be started or stopped. */
    public interface FailureListener {
        void onFailure(String key, String details);
    }

    private final Consumer<Runnable> dispatcher;
    private final Set<String> watchedKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> stoppingKeys = ConcurrentHashMap.newKeySet();

    private volatile StatusListener listener = (key, status) -> {
    };
    private volatile FailureListener failureListener = (key, details) -> {
    };

    /**
     * @param dispatcher runs each notification where the listener expects to be
     *                   called,
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

    /**
     * Remembers a service, so it keeps being polled and is told about a lost
     * daemon.
     */
    public void watch(String key) {
        watchedKeys.add(key);
    }

    public Set<String> watchedKeys() {
        return Set.copyOf(watchedKeys);
    }

    /**
     * The student asked for this service to run, so late stop reports no longer
     * apply.
     */
    public void expectRunning(String key) {
        watch(key);
        stoppingKeys.remove(key);
    }

    /**
     * The student asked for this service to stop, so late health reports are
     * ignored.
     */
    public void expectStopped(String key) {
        watch(key);
        stoppingKeys.add(key);
    }

    /**
     * Announces a status, unless it contradicts a stop the student already asked
     * for.
     * A service reported as stopped always gets through and settles the matter.
     */
    public void report(String key, ServiceStatus status) {
        if (status == ServiceStatus.STOPPED) {
            stoppingKeys.remove(key);
            deliver(key, ServiceStatus.STOPPED);
            return;
        }
        if (stoppingKeys.contains(key)) {
            return;
        }
        deliver(key, status);
    }

    /** Reports a service as failed, along with whatever Docker said about it. */
    public void reportFailure(String key, String details) {
        deliver(key, ServiceStatus.ERROR);
        if (details != null && !details.isBlank()) {
            dispatcher.accept(() -> failureListener.onFailure(key, details.strip()));
        }
    }

    /** The daemon is gone, so nothing that is known can be described any more. */
    public void reportEverythingUnreachable() {
        for (String key : watchedKeys) {
            deliver(key, ServiceStatus.ERROR);
        }
    }

    private void deliver(String key, ServiceStatus status) {
        dispatcher.accept(() -> listener.onStatusChanged(key, status));
    }
}
