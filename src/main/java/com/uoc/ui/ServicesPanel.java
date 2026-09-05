package com.uoc.ui;

import com.uoc.docker.Database;
import com.uoc.docker.ServiceAction;
import com.uoc.docker.ServiceStatus;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.Timer;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ServicesPanel {

    private static final String START_ICON = "icons/start.svg";
    private static final String STOP_ICON = "icons/stop.svg";
    private static final int ACTION_ICON_SIZE = 16;
    private static final int STATUS_COLUMN_WIDTH = 80;
    private static final int CELL_PADDING = 3;
    private static final int SECTION_GAP = 5;

    private final JPanel component;
    private final JLabel titleLabel = new JLabel();
    private final Map<String, PulsingCircle> circles = new LinkedHashMap<>();
    private final Map<String, JLabel> statusLabels = new LinkedHashMap<>();
    private final Map<String, ServiceStatus> currentStatus = new LinkedHashMap<>();
    private final Map<String, JButton> actionButtons = new LinkedHashMap<>();
    private final JPanel grid = new JPanel(new GridBagLayout());
    private final Translations translations;

    // How many dots trail a word while something is waiting, and how often one is added.
    // Installing can run for minutes and loading for a good while; a label that never
    // changed would be indistinguishable from an application that had stopped responding.
    private static final int MAX_DOTS = 3;
    private static final int DOT_MILLIS = 450;
    private int dots;
    private final Timer waitingAnimation = new Timer(DOT_MILLIS, e -> {
        dots = (dots + 1) % (MAX_DOTS + 1);
        redrawWaitingLabels();
    });
    private int nextRow;

    public ServicesPanel(List<Database> databases, Consumer<String> onStart, Consumer<String> onStop,
            Translations translations) {
        this.translations = translations;

        for (Database database : databases) {
            addService(database.key(), database.displayName(), onStart, onStop);
        }

        component = new JPanel(new BorderLayout(SECTION_GAP, SECTION_GAP));
        component.add(titleLabel, BorderLayout.NORTH);
        component.add(grid, BorderLayout.CENTER);

        translations.register(() -> {
            titleLabel.setText(translations.get(Message.LABEL_SERVICES));
            currentStatus.forEach(this::updateAction);
            currentStatus.forEach((key, status) -> statusLabels.get(key).setText(textFor(status)));
        });
    }

    public void addService(String key, String displayName, Consumer<String> onStart,
            Consumer<String> onStop) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(CELL_PADDING, CELL_PADDING, CELL_PADDING, CELL_PADDING);
        gbc.anchor = GridBagConstraints.WEST;

        PulsingCircle circle = new PulsingCircle();
        circle.setColor(StatusAppearance.colorFor(ServiceStatus.STOPPED));
        circles.put(key, circle);

        JLabel nameLabel = new JLabel(displayName);
        JLabel statusLabel = new JLabel();
        statusLabel.setText(translations.get(ServiceStatus.STOPPED.message()));
        statusLabel.setPreferredSize(new Dimension(STATUS_COLUMN_WIDTH, statusLabel.getPreferredSize().height));
        statusLabels.put(key, statusLabel);
        currentStatus.put(key, ServiceStatus.STOPPED);

        // One button, not two. Which action makes sense is a property of the state, so
        // there is one question to answer rather than two enabled flags to keep in step
        // with each other and with the indicator beside them.
        JButton actionButton = new JButton();
        actionButton.setName(key);
        actionButton.addActionListener(e -> {
            if (ServiceAction.forStatus(currentStatus.get(key)) == ServiceAction.START) {
                onStart.accept(key);
            } else {
                onStop.accept(key);
            }
        });
        actionButtons.put(key, actionButton);

        gbc.gridy = nextRow++;
        gbc.gridx = 0;
        grid.add(circle, gbc);
        gbc.gridx = 1;
        grid.add(nameLabel, gbc);
        gbc.gridx = 2;
        grid.add(statusLabel, gbc);
        gbc.gridx = 3;
        grid.add(actionButton, gbc);

        updateAction(key, ServiceStatus.STOPPED);
    }

    /** Puts the one action worth offering on the button, and says whether it can be taken. */
    private void updateAction(String key, ServiceStatus status) {
        JButton button = actionButtons.get(key);
        if (button == null) {
            return;
        }
        ServiceAction action = ServiceAction.forStatus(status);
        boolean start = action == ServiceAction.START;
        boolean available = ServiceAction.isAvailable(status);

        // The icon is painted rather than left to Swing to grey out when disabled: its
        // own dimming reads differently on each theme and lands on a muddy grey, while
        // the palette's is the one already measured against this background.
        FlatSVGIcon icon = new FlatSVGIcon(start ? START_ICON : STOP_ICON,
                ACTION_ICON_SIZE, ACTION_ICON_SIZE);
        Color painted = StatusAppearance.actionColor(available, start);
        button.setIcon(icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> painted)));
        button.setDisabledIcon(button.getIcon());

        button.setEnabled(available);
        button.setToolTipText(translations.get(start ? Message.TOOLTIP_START : Message.TOOLTIP_STOP));
    }

    /**
     * Repaints every dot and icon in the colours of the theme now in use.
     *
     * <p>
     * The colours come from the palette rather than being fixed, so switching between
     * light and dark has to be followed here; otherwise the indicators keep the contrast
     * of the theme they were built under.
     */
    public void applyThemeColors() {
        currentStatus.forEach((key, status) -> {
            PulsingCircle circle = circles.get(key);
            if (circle != null) {
                circle.setColor(StatusAppearance.colorFor(status));
            }
            updateAction(key, status);
        });
    }

    public JPanel getComponent() {
        return component;
    }

    /** The one button beside a service, whichever action it is currently offering. */
    public JButton actionButtonFor(String key) {
        return actionButtons.get(key);
    }

    public void updateStatus(String key, ServiceStatus status) {
        currentStatus.put(key, status);

        PulsingCircle circle = circles.get(key);
        if (circle != null) {
            circle.setColor(StatusAppearance.colorFor(status));
            circle.setPulsating(StatusAppearance.pulsates(status));
        }

        JLabel label = statusLabels.get(key);
        if (label != null) {
            label.setText(textFor(status));
        }
        updateAction(key, status);
        syncWaitingAnimation();
    }

    /**
     * What a status reads as. Anything that is a wait trails a growing run of dots, so a
     * service that takes minutes never looks like a launcher that has stopped responding.
     */
    private String textFor(ServiceStatus status) {
        String text = translations.get(status.message());
        return status.isWaiting() ? text + ".".repeat(dots) : text;
    }

    /** Runs the animation only while something is waiting, and stops it otherwise. */
    private void syncWaitingAnimation() {
        boolean waiting = currentStatus.values().stream().anyMatch(ServiceStatus::isWaiting);
        if (waiting && !waitingAnimation.isRunning()) {
            dots = 0;
            waitingAnimation.start();
        } else if (!waiting && waitingAnimation.isRunning()) {
            waitingAnimation.stop();
        }
    }

    private void redrawWaitingLabels() {
        currentStatus.forEach((key, status) -> {
            if (status.isWaiting()) {
                statusLabels.get(key).setText(textFor(status));
            }
        });
    }
}
