package com.uoc.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class PulsingCircle extends JComponent {

    private static final int SIZE = 16;
    private static final int TICK_MILLIS = 40;
    private static final float PHASE_STEP = 0.12f;
    private static final float TWO_PI = (float) (2 * Math.PI);

    private Color color = Color.GRAY;
    private boolean pulsating;
    private float phase;
    private final Timer timer;

    public PulsingCircle() {
        Dimension size = new Dimension(SIZE, SIZE);
        setPreferredSize(size);
        setMinimumSize(size);
        setOpaque(false);
        timer = new Timer(TICK_MILLIS, e -> {
            phase = (phase + PHASE_STEP) % TWO_PI;
            repaint();
        });
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
            timer.start();
        } else {
            timer.stop();
            phase = 0f;
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
