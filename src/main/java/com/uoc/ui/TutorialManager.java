package com.uoc.ui;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

public class TutorialManager {

    private static final int POPUP_WIDTH = 320;
    private static final int GAP = 8;

    private final Window window;
    private final Translations translations;
    private final List<Message> messages = List.of(
            Message.TUTORIAL_SERVICES,
            Message.TUTORIAL_START_MONGO,
            Message.TUTORIAL_MENU,
            Message.TUTORIAL_JUPYTER);

    private JPanel popup;
    private JLabel messageLabel;
    private JButton nextButton;
    private List<Component> targets;
    private int[] positions;
    private int step;

    public TutorialManager(Window window, Translations translations) {
        this.window = window;
        this.translations = translations;
        translations.register(this::refreshText);
    }

    public void show(Component... targets) {
        show(targets, SwingConstants.BOTTOM, SwingConstants.TOP,
                SwingConstants.TOP, SwingConstants.BOTTOM);
    }

    public void show(Component[] targets, int... positions) {
        if (targets.length != messages.size() || positions.length != messages.size()) {
            throw new IllegalArgumentException("A tutorial needs one target per message");
        }
        this.targets = List.of(targets);
        this.positions = positions.clone();
        step = 0;
        showStep();
    }

    private void showStep() {
        hide();
        Component target = targets.get(step);
        JLayeredPane layeredPane = ((javax.swing.RootPaneContainer) window)
                .getRootPane().getLayeredPane();

        messageLabel = new JLabel();
        messageLabel.setVerticalAlignment(SwingConstants.TOP);

        nextButton = new JButton();
        nextButton.addActionListener(e -> {
            if (step == messages.size() - 1) {
                hide();
            } else {
                step++;
                showStep();
            }
        });

        popup = new JPanel(new BorderLayout(8, 8));
        popup.setOpaque(true);
        popup.setBackground(new Color(255, 249, 196));
        popup.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(new Color(190, 160, 55)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        popup.addMouseListener(new java.awt.event.MouseAdapter() {
        });
        popup.add(messageLabel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(nextButton);
        popup.add(buttons, BorderLayout.SOUTH);
        refreshText();

        layeredPane.add(popup, JLayeredPane.POPUP_LAYER);
        layeredPane.revalidate();
        popup.setVisible(true);

        Point location = SwingUtilities.convertPoint(target, 0, 0, layeredPane);
        Dimension size = popup.getPreferredSize();
        int x = location.x;
        int y = location.y;
        switch (positions[step]) {
            case SwingConstants.TOP -> y -= size.height + GAP;
            case SwingConstants.LEFT -> x -= size.width + GAP;
            case SwingConstants.RIGHT -> x += target.getWidth() + GAP;
            default -> y += target.getHeight() + GAP;
        }
        popup.setBounds(x, y, size.width, size.height);
        layeredPane.repaint();
    }

    public void hide() {
        if (popup == null) {
            return;
        }
        Container parent = popup.getParent();
        if (parent != null) {
            parent.remove(popup);
            parent.repaint();
        }
        popup = null;
        messageLabel = null;
        nextButton = null;
    }

    private void refreshText() {
        if (popup == null) {
            return;
        }
        messageLabel.setText("<html><body style='width: " + (POPUP_WIDTH - 40) + "px'>"
                + translations.get(messages.get(step)) + "</body></html>");
        nextButton.setText(translations.get(step == messages.size() - 1
                ? Message.TUTORIAL_CLOSE
                : Message.TUTORIAL_NEXT));
        popup.revalidate();
        popup.repaint();
    }

    private static final class RoundedBorder extends AbstractBorder {
        private final Color color;

        private RoundedBorder(Color color) {
            this.color = color;
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y,
                int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(color);
                g.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, 12, 12));
            } finally {
                g.dispose();
            }
        }
    }
}
