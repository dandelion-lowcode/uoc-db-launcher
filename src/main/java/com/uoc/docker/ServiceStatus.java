package com.uoc.docker;

import com.uoc.i18n.Message;

public enum ServiceStatus {
    STOPPED(Message.STATUS_STOPPED),
    STARTING(Message.STATUS_STARTING),
    RUNNING(Message.STATUS_RUNNING),
    HEALTHY(Message.STATUS_HEALTHY),
    UNHEALTHY(Message.STATUS_UNHEALTHY),
    STOPPING(Message.STATUS_STOPPING),
    ERROR(Message.STATUS_ERROR);

    private final Message message;

    ServiceStatus(Message message) {
        this.message = message;
    }

    public boolean isTransitional() {
        return this == STARTING || this == RUNNING || this == STOPPING;
    }

    public boolean isFailure() {
        return this == UNHEALTHY || this == ERROR;
    }

    public Message message() {
        return message;
    }
}
