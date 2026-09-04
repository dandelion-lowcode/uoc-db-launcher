package com.uoc.ui;

import com.uoc.docker.Database;
import com.uoc.docker.ServiceStatus;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
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
    private final Map<String, JButton> startButtonByKey = new LinkedHashMap<>();
    private final List<JButton> startButtons = new ArrayList<>();
    private final List<JButton> stopButtons = new ArrayList<>();
    private final JPanel grid = new JPanel(new GridBagLayout());
    private final Translations translations;
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
            startButtons.forEach(button -> button.setToolTipText(translations.get(Message.TOOLTIP_START)));
            stopButtons.forEach(button -> button.setToolTipText(translations.get(Message.TOOLTIP_STOP)));
            currentStatus.forEach((key, status) -> statusLabels.get(key).setText(translations.get(status.message())));
        });
    }

    public void addService(String key, String displayName, Consumer<String> onStart,
            Consumer<String> onStop) {
        FlatSVGIcon startIcon = new FlatSVGIcon(START_ICON, ACTION_ICON_SIZE, ACTION_ICON_SIZE);
        FlatSVGIcon stopIcon = new FlatSVGIcon(STOP_ICON, ACTION_ICON_SIZE, ACTION_ICON_SIZE);
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

        JButton startButton = new JButton(startIcon);
        startButton.addActionListener(e -> onStart.accept(key));
        startButtons.add(startButton);
        startButtonByKey.put(key, startButton);

        JButton stopButton = new JButton(stopIcon);
        stopButton.addActionListener(e -> onStop.accept(key));
        stopButtons.add(stopButton);

        gbc.gridy = nextRow++;
        gbc.gridx = 0;
        grid.add(circle, gbc);
        gbc.gridx = 1;
        grid.add(nameLabel, gbc);
        gbc.gridx = 2;
        grid.add(statusLabel, gbc);
        gbc.gridx = 3;
        grid.add(startButton, gbc);
        gbc.gridx = 4;
        grid.add(stopButton, gbc);
    }

    public JPanel getComponent() {
        return component;
    }

    public JButton startButtonFor(String key) {
        return startButtonByKey.get(key);
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
            label.setText(translations.get(status.message()));
        }
    }
}
