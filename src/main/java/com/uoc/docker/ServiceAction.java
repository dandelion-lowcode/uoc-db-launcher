package com.uoc.docker;

/**
 * The one thing worth offering to do to a service, and whether it can be done yet.
 *
 * <p>
 * There used to be two buttons beside every service, neither of which was ever disabled:
 * a student could stop something already stopped, or press start twice on a download and
 * launch a second one on top of the first. Two buttons also meant two rules to keep in
 * step with each other and with the indicator, which is three things that can disagree.
 *
 * <p>
 * Which action makes sense is a property of the state, so it is read from the state, and
 * one button shows it.
 */
public enum ServiceAction {
    START,
    STOP;

    /**
     * The action to offer for a status.
     *
     * <p>
     * While a command is in flight the action shown is the one it is heading towards, so
     * the button does not jump between icons as a service comes up. It is offered
     * disabled; see {@link #isAvailable(ServiceStatus)}.
     */
    public static ServiceAction forStatus(ServiceStatus status) {
        if (status.isUp()) {
            return STOP;
        }
        // Installing and starting are on their way to running, so stopping is what will
        // be wanted next.
        return status == ServiceStatus.INSTALLING || status == ServiceStatus.STARTING
                ? STOP
                : START;
    }

    /**
     * Whether the action can be taken now. A command already running is left to finish:
     * pressing start twice starts a second one, and there is nothing useful to do to a
     * service in the middle of being installed.
     */
    public static boolean isAvailable(ServiceStatus status) {
        return status != ServiceStatus.INSTALLING
                && status != ServiceStatus.STARTING
                && status != ServiceStatus.STOPPING;
    }
}
