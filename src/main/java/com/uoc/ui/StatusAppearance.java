package com.uoc.ui;

import com.uoc.docker.ServiceStatus;

import java.awt.Color;

public final class StatusAppearance {

    private static final Color HEALTHY_COLOR = new Color(0x2e, 0x7d, 0x32);
    private static final Color TRANSITION_COLOR = new Color(0xf9, 0xa8, 0x25);
    private static final Color FAILURE_COLOR = new Color(0xc6, 0x28, 0x28);
    private static final Color IDLE_COLOR = Color.GRAY;

    private StatusAppearance() {
    }

    public static Color colorFor(ServiceStatus status) {
        if (status == ServiceStatus.HEALTHY) {
            return HEALTHY_COLOR;
        }
        if (status.isTransitional()) {
            return TRANSITION_COLOR;
        }
        if (status.isFailure()) {
            return FAILURE_COLOR;
        }
        return IDLE_COLOR;
    }

    public static boolean pulsates(ServiceStatus status) {
        return status != ServiceStatus.STOPPED && status != ServiceStatus.ERROR;
    }
}
