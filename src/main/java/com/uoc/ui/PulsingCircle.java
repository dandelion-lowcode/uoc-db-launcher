package com.uoc.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.Timer;

public class PulsingCircle extends JComponent {

    private static final int SIZE = 16;
    private static final int TICK_MILLIS = 40;
    private static final float PHASE_STEP = 0.12f;
    private static final float TWO_PI = (float) (2 * Math.PI);

    /**
     * One clock for every circle on screen, rather than one each.
     *
     * <p>
     * Eleven services means eleven of these, and each one used to wake the event
     * thread twenty-five times a second on its own account. Shared, they also pulse
     * together: separate timers drift apart, and a row of indicators breathing out of
     * step looks like a fault rather than a rhythm.
     */
    private static final Set<PulsingCircle> pulsingNow = new LinkedHashSet<>();
    private static float phase;
    private static final Timer CLOCK = new Timer(TICK_MILLIS, event -> {
        phase = (phase + PHASE_STEP) % TWO_PI;
        pulsingNow.forEach(PulsingCircle::repaint);
    });

    private Color color = Color.GRAY;
    private boolean pulsating;

    public PulsingCircle() {
        Dimension size = new Dimension(SIZE, SIZE);
        setPreferredSize(size);
        setMinimumSize(size);
        setOpaque(false);
    }

    public void setColor(Color color) {
        this.color = color;
        repaint();
    }

    public void setPulsating(boolean pulsating) {
        if (this.pulsating == pulsating) {
            return;
        }
        this.pulsating = pulsating;
        if (pulsating) {
            pulsingNow.add(this);
            CLOCK.start();
        } else {
            pulsingNow.remove(this);
            // Nothing to animate, so nothing to wake up for.
            if (pulsingNow.isEmpty()) {
                CLOCK.stop();
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float alpha = pulsating ? 0.7f + 0.3f * (float) Math.sin(phase) : 1f;
        int diameter = SIZE - 2;
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(alpha * 255)));
        g2.fillOval(1, 1, diameter, diameter);

        g2.dispose();
    }
}
