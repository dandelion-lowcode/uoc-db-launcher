package com.uoc.ui;

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

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.uoc.docker.Database;
import com.uoc.docker.ServiceAction;
import com.uoc.docker.ServiceStatus;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

public class ServicesPanel {

    private static final String START_ICON = "icons/start.svg";
    private static final String STOP_ICON = "icons/stop.svg";
    private static final int ACTION_ICON_SIZE = 16;
    private static final int STATUS_COLUMN_WIDTH = 80;
    private static final int CELL_PADDING = 3;
    private static final int SECTION_GAP = 5;

    /** Indicator, name, status, button. */
    private static final int COLUMNS = 4;

    /** Room between the box's own frame and the rows inside it. */
    private static final int PANEL_PADDING = 6;

    private final JPanel component;

    /**
     * The panel's own frame and name, rather than a label sitting loose above it. What
     * the services are is then said by the box they are in, which is what a titled border
     * is for and what puts the group beside the tabs on the same footing as any other.
     */
    private final javax.swing.border.TitledBorder heading =
            javax.swing.BorderFactory.createTitledBorder("");
    private final Map<String, PulsingCircle> circles = new LinkedHashMap<>();
    private final Map<String, JLabel> statusLabels = new LinkedHashMap<>();
    private final Map<String, JLabel> nameLabels = new LinkedHashMap<>();
    private final Map<String, ServiceStatus> currentStatus = new LinkedHashMap<>();
    private final Map<String, JButton> actionButtons = new LinkedHashMap<>();
    private final JPanel grid = new JPanel(new GridBagLayout());
    private final Translations translations;

    /** Every service there could be a row for, in the order the rows go in. */
    private final List<String> everyService = new java.util.ArrayList<>();

    /**
     * The services a student has chosen, which is exactly the set with a row.
     *
     * <p>
     * A row and a tick are the same thing said twice: the panel lists what is
     * theirs to
     * hand, not what exists. Eleven rows for eleven services, most of which they
     * will
     * never open, is a wall to read past every time they look for the one they
     * want.
     */
    private final java.util.Set<String> chosen = new java.util.LinkedHashSet<>();

    /**
     * Unchosen, but still doing something, so still shown.
     *
     * <p>
     * The one exception to the rule above, and the reason it is worth having: a
     * stop
     * takes time, and an image already downloading cannot be called back. A row
     * that
     * vanished the instant it was unticked would take the only account of that with
     * it,
     * leaving a student watching nothing while Docker finishes two gigabytes.
     */
    private final java.util.Set<String> leaving = new java.util.LinkedHashSet<>();

    /**
     * What stands in the panel's place when a student has chosen nothing at all.
     */
    private final JLabel nothingChosen = new JLabel();

    // How many dots trail a word while something is waiting, and how often one is
    // added.
    // Installing can run for minutes and loading for a good while; a label that
    // never
    // changed would be indistinguishable from an application that had stopped
    // responding.
    private static final int MAX_DOTS = 3;
    private static final int DOT_MILLIS = 450;
    private int dots;
    private final Timer waitingAnimation = new Timer(DOT_MILLIS, e -> {
        dots = (dots + 1) % (MAX_DOTS + 1);
        redrawWaitingLabels();
    });

    public ServicesPanel(List<Database> databases, Consumer<String> onStart, Consumer<String> onStop,
            Translations translations) {
        this.translations = translations;

        for (Database database : databases) {
            addService(database.key(), database.displayName(), onStart, onStop);
        }

        component = new JPanel(new BorderLayout(SECTION_GAP, SECTION_GAP));
        component.setBorder(javax.swing.BorderFactory.createCompoundBorder(heading,
                javax.swing.BorderFactory.createEmptyBorder(PANEL_PADDING, PANEL_PADDING,
                        PANEL_PADDING, PANEL_PADDING)));
        component.add(grid, BorderLayout.CENTER);

        translations.register(() -> {
            // Spaced out from the frame it is cut into: a titled border has no setting
            // for that, and the title otherwise begins hard against the corner.
            heading.setTitle(" " + translations.get(Message.LABEL_SERVICES) + " ");
            component.repaint();
            nothingChosen.setText(translations.get(Message.LABEL_SERVICES_NONE));
            currentStatus.forEach(this::updateAction);
            currentStatus.forEach((key, status) -> statusLabels.get(key).setText(textFor(status)));
        });

        layOutChosen();
    }

    public void addService(String key, String displayName, Consumer<String> onStart,
            Consumer<String> onStop) {
        everyService.add(key);

        PulsingCircle circle = new PulsingCircle();
        circle.setColor(StatusAppearance.colorFor(ServiceStatus.STOPPED));
        circles.put(key, circle);

        JLabel nameLabel = new JLabel(displayName);
        nameLabels.put(key, nameLabel);
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

        updateAction(key, ServiceStatus.STOPPED);
    }

    /**
     * Says whether a service is one the student has chosen.
     *
     * <p>
     * Chosen and shown are the same thing, with one exception: a service unchosen
     * while
     * it is still starting, installing or stopping keeps its row until it has
     * finished
     * doing that. Everything else goes at once, a service that was never started
     * included.
     */
    public void setChosen(String key, boolean isChosen) {
        if (isChosen) {
            chosen.add(key);
            leaving.remove(key);
        } else if (isSettled(currentStatus.getOrDefault(key, ServiceStatus.STOPPED))) {
            chosen.remove(key);
            leaving.remove(key);
        } else {
            leaving.add(key);
        }
        layOutChosen();
    }

    /**
     * Whether nothing is happening to a service and nothing is about to.
     *
     * <p>
     * Both halves matter. A service that is still installing or stopping is plainly not
     * finished; but one that is <em>up</em> is not finished either, because unchoosing it
     * is what asks it to stop, and the stop has not even been sent yet. Reading only the
     * first half made a healthy service vanish the instant it was unticked, taking the
     * stop out of sight with it: the container was on its way down and the panel showed
     * nothing at all.
     */
    private static boolean isSettled(ServiceStatus status) {
        return !status.isWaiting() && !status.isUp();
    }

    /**
     * Builds the panel out of the rows that belong in it, in the order the services
     * are
     * declared in, so a service that comes back returns to its own place rather
     * than to
     * the end.
     */
    private void layOutChosen() {
        grid.removeAll();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(CELL_PADDING, CELL_PADDING, CELL_PADDING, CELL_PADDING);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        for (String key : everyService) {
            if (!isRowShowing(key)) {
                continue;
            }
            gbc.gridy = row++;
            gbc.gridx = 0;
            grid.add(circles.get(key), gbc);
            gbc.gridx = 1;
            grid.add(nameLabels.get(key), gbc);
            gbc.gridx = 2;
            grid.add(statusLabels.get(key), gbc);
            gbc.gridx = 3;
            grid.add(actionButtons.get(key), gbc);
        }

        if (row == 0) {
            // Otherwise the heading sits above an empty rectangle, which reads as a fault
            // rather than as a choice, and says nothing about how to undo it.
            gbc.gridy = 0;
            gbc.gridx = 0;
            gbc.gridwidth = COLUMNS;
            grid.add(nothingChosen, gbc);
        }

        grid.revalidate();
        grid.repaint();
    }

    private boolean isRowShowing(String key) {
        return chosen.contains(key) || leaving.contains(key);
    }

    /**
     * Puts the one action worth offering on the button, and says whether it can be
     * taken.
     */
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
     * The colours come from the palette rather than being fixed, so switching
     * between
     * light and dark has to be followed here; otherwise the indicators keep the
     * contrast
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

    /**
     * The one button beside a service, whichever action it is currently offering.
     */
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

        // An unchosen service was kept only for as long as something was happening to
        // it. Whatever it settled on -- stopped, crashed, or a stop that failed
        // outright
        // -- nothing is happening now, so it goes. Waiting for STOPPED alone would
        // leave
        // a row nothing could ever clear.
        if (leaving.contains(key) && isSettled(status)) {
            leaving.remove(key);
            chosen.remove(key);
            layOutChosen();
        }
    }

    /**
     * What a status reads as. Anything that is a wait trails a growing run of dots,
     * so a
     * service that takes minutes never looks like a launcher that has stopped
     * responding.
     */
    private String textFor(ServiceStatus status) {
        String text = translations.get(status.message());
        return status.isWaiting() ? text + ".".repeat(dots) : text;
    }

    /**
     * Runs the animation only while something is waiting, and stops it otherwise.
     */
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
