package com.uoc.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.ui.FlatDropShadowBorder;
import com.formdev.flatlaf.ui.FlatEmptyBorder;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.BasicStroke;
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
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * The tour of the window a student is given on their first run.
 *
 * <p>
 * The balloons are FlatLaf's own: the toolkit's demo points at its themes and its fonts
 * with exactly this shape, and a student who has met one anywhere else recognises it. The
 * border below is FlatLaf's, brought over rather than approximated, and every colour in
 * it is read from the theme, so the tour follows a change of theme like everything else.
 */
public class TutorialManager {

    /**
     * The distance between a balloon and what it points at, before scaling. FlatLaf's
     * hints use six, and the arrow is drawn to reach across it.
     */
    private static final int GAP = 6;

    /** How wide the text may run before it wraps, before scaling. */
    private static final int TEXT_WIDTH = 200;

    /**
     * The space around the text, and between the text and the button.
     *
     * <p>
     * FlatLaf lays its hints out with MigLayout, asking for "insets dialog" and a
     * paragraph gap. That layout manager is not a dependency here, for one panel, so
     * these are what those two come to on screen.
     */
    private static final int PADDING = 12;
    private static final int PARAGRAPH_GAP = 14;

    /** The balloon's own fill, which the theme defines beside our other colours. */
    private static final String BACKGROUND_KEY = "HintPanel.backgroundColor";

    /** What FlatLaf falls back to under a look and feel that is not one of its own. */
    private static final String FALLBACK_BACKGROUND_KEY = "info";

    private static final String BORDER_KEY = "PopupMenu.borderColor";

    private final Window window;
    private final Translations translations;
    private final List<Message> messages = List.of(
            Message.TUTORIAL_SERVICES,
            Message.TUTORIAL_START_MONGO,
            Message.TUTORIAL_MENU,
            Message.TUTORIAL_JUPYTER);

    /** Transparent, and sized to include the shadow the balloon casts. */
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
        // Nothing in a balloon is reached by tabbing: it is read and dismissed.
        nextButton.setFocusable(false);
        nextButton.addActionListener(e -> {
            if (step == messages.size() - 1) {
                hide();
            } else {
                step++;
                showStep();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(nextButton);

        Point location = SwingUtilities.convertPoint(target, 0, 0, layeredPane);

        // Where it was asked to go, unless there is no room for it there. A balloon that
        // runs past the edge of the window is cut off by it, and the half a student needs
        // is as likely to be the missing half as not.
        int side = sideThatFits(positions[step], location, target, layeredPane);

        Dimension size = measure(side);
        Point at = nudgedIntoView(cornerFor(side, location, target, size), size, layeredPane);

        Balloon balloon = new Balloon(side, arrowOffset(side, at, size, location, target));
        balloon.add(messageLabel, BorderLayout.CENTER);
        balloon.add(buttons, BorderLayout.SOUTH);

        // The balloon sits inside a transparent panel because its shadow is painted
        // inside its own border: the bounds set below have to leave room for it.
        popup = new JPanel(new BorderLayout());
        popup.setOpaque(false);
        popup.add(balloon);

        refreshText();

        layeredPane.add(popup, JLayeredPane.POPUP_LAYER);
        layeredPane.revalidate();
        popup.setVisible(true);

        popup.setBounds(at.x, at.y, size.width, size.height);
        layeredPane.repaint();
    }

    /**
     * How far along its edge the arrow sits, so that it lands on the middle of what the
     * step is talking about.
     *
     * <p>
     * FlatLaf puts the arrow at a fixed distance from the corner, which is right when the
     * balloon starts where its subject does. Here a balloon slides along the window to
     * stay inside it, and one wide balloon pointing at one small button would otherwise
     * end up with its arrow over nothing. That fixed distance is kept as the closest the
     * arrow may come to either corner, so it never runs into the rounding.
     */
    private static int arrowOffset(int side, Point at, Dimension size, Point location,
            Component target) {
        int shadow = UIScale.scale(BalloonBorder.SHADOW_SIZE);
        int arrow = UIScale.scale(BalloonBorder.ARROW_SIZE);
        int margin = UIScale.scale(BalloonBorder.ARROW_XY);

        boolean horizontal = side == SwingConstants.TOP || side == SwingConstants.BOTTOM;
        int middle = horizontal
                ? location.x + target.getWidth() / 2
                : location.y + target.getHeight() / 2;
        int start = horizontal ? at.x : at.y;
        int along = horizontal ? size.width : size.height;

        // The arrow is two arrow-widths long and points from its middle.
        int wanted = middle - start - shadow - arrow;
        int furthest = along - (shadow + shadow) - margin - arrow - arrow;
        return Math.max(margin, Math.min(wanted, Math.max(margin, furthest)));
    }

    /**
     * The side to put the balloon on: the one asked for while it fits, the opposite one
     * when it does not.
     *
     * <p>
     * Only the two sides are tried, never a third: a balloon points at what it is talking
     * about, and moving it round to the side of a thing the step describes as being above
     * or below would say something the words do not.
     */
    private int sideThatFits(int asked, Point location, Component target,
            JLayeredPane layeredPane) {
        // The size is the same whichever side the arrow is on, so one balloon is enough
        // to measure with.
        Dimension size = measure(asked);
        if (fits(cornerFor(asked, location, target, size), size, layeredPane)) {
            return asked;
        }
        int opposite = switch (asked) {
            case SwingConstants.TOP -> SwingConstants.BOTTOM;
            case SwingConstants.BOTTOM -> SwingConstants.TOP;
            case SwingConstants.LEFT -> SwingConstants.RIGHT;
            default -> SwingConstants.LEFT;
        };
        return fits(cornerFor(opposite, location, target, size), size, layeredPane)
                ? opposite
                : asked;
    }

    /** What this step's balloon comes to, laid out but never shown. */
    private Dimension measure(int side) {
        JLabel text = new JLabel(textFor(step));
        JButton button = new JButton(buttonTextFor(step));

        // Any offset will do: where the arrow sits along an edge does not change how much
        // room the balloon takes.
        Balloon balloon = new Balloon(side, UIScale.scale(BalloonBorder.ARROW_XY));
        balloon.add(text, BorderLayout.CENTER);
        balloon.add(button, BorderLayout.SOUTH);

        JPanel measured = new JPanel(new BorderLayout());
        measured.add(balloon);
        return measured.getPreferredSize();
    }

    private Point cornerFor(int side, Point location, Component target, Dimension size) {
        int gap = UIScale.scale(GAP);
        int x = location.x;
        int y = location.y;
        switch (side) {
            case SwingConstants.TOP -> y -= size.height + gap;
            case SwingConstants.LEFT -> x -= size.width + gap;
            case SwingConstants.RIGHT -> x += target.getWidth() + gap;
            default -> y += target.getHeight() + gap;
        }
        return new Point(x, y);
    }

    private static boolean fits(Point at, Dimension size, JLayeredPane layeredPane) {
        return at.x >= 0 && at.y >= 0
                && at.x + size.width <= layeredPane.getWidth()
                && at.y + size.height <= layeredPane.getHeight();
    }

    /**
     * The same balloon, slid along the window's edge until all of it is inside.
     *
     * <p>
     * This is what is left when neither side fits, which happens when the window is
     * narrower than the balloon is wide. The arrow no longer lands on what it points at,
     * and a balloon a little off its mark still says more than one with its text cut in
     * half.
     */
    private static Point nudgedIntoView(Point at, Dimension size, JLayeredPane layeredPane) {
        int x = Math.max(0, Math.min(at.x, layeredPane.getWidth() - size.width));
        int y = Math.max(0, Math.min(at.y, layeredPane.getHeight() - size.height));
        return new Point(x, y);
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
        messageLabel.setText(textFor(step));
        nextButton.setText(buttonTextFor(step));
        popup.revalidate();
        popup.repaint();
    }

    /** The step's own words, wrapped at the width FlatLaf's hints wrap at. */
    private String textFor(int step) {
        return "<html><body style='width: " + UIScale.scale(TEXT_WIDTH) + "px'>"
                + translations.get(messages.get(step)) + "</body></html>";
    }

    private String buttonTextFor(int step) {
        return translations.get(step == messages.size() - 1
                ? Message.TUTORIAL_CLOSE
                : Message.TUTORIAL_NEXT);
    }

    /**
     * The note itself. It is transparent, because what fills it is the border: the shape
     * with the arrow on it is one outline, and filling it anywhere else would leave the
     * arrow hollow.
     */
    private static final class Balloon extends JPanel {

        private final int position;
        private final int arrowOffset;
        private boolean built;

        private Balloon(int position, int arrowOffset) {
            super(new BorderLayout(0, UIScale.scale(PARAGRAPH_GAP)));
            this.position = position;
            this.arrowOffset = arrowOffset;
            this.built = true;
            setOpaque(false);
            dress();
            // Every mouse event stops here, so that a component the balloon happens to
            // cover does not receive a click meant for the balloon.
            addMouseListener(new MouseAdapter() {
            });
        }

        @Override
        public void updateUI() {
            super.updateUI();
            // Called once from the constructor of JPanel, before there is a position to
            // point at. The colours are read again here so a change of theme reaches a
            // balloon that is already on screen.
            if (built) {
                dress();
            }
        }

        private void dress() {
            setBackground(UIManager.getLookAndFeel() instanceof FlatLaf
                    ? UIManager.getColor(BACKGROUND_KEY)
                    // nonUIResource because some look and feels will not fill a
                    // background that is one.
                    : FlatUIUtils.nonUIResource(UIManager.getColor(FALLBACK_BACKGROUND_KEY)));

            setBorder(BorderFactory.createCompoundBorder(
                    new BalloonBorder(arrowTowards(position), arrowOffset,
                            FlatUIUtils.getUIColor(BORDER_KEY, Color.gray)),
                    new FlatEmptyBorder(new Insets(PADDING, PADDING, PADDING, PADDING))));
        }

        /**
         * Which way the arrow points, given where the balloon sits. A balloon below what
         * it describes has its arrow on top, and so on round.
         */
        private static int arrowTowards(int position) {
            return switch (position) {
                case SwingConstants.LEFT -> SwingConstants.RIGHT;
                case SwingConstants.TOP -> SwingConstants.BOTTOM;
                case SwingConstants.RIGHT -> SwingConstants.LEFT;
                default -> SwingConstants.TOP;
            };
        }
    }

    /**
     * A rounded rectangle with an arrow on one side and a shadow under it.
     *
     * <p>
     * Taken from FlatLaf's own HintManager, by Karl Tauber, under the Apache License 2.0,
     * so that the tutorial's balloons are the toolkit's balloons and not a drawing that
     * resembles them. The numbers are what make it recognisable and are left exactly as
     * they are; every colour comes from the theme.
     */
    private static final class BalloonBorder extends FlatEmptyBorder {

        private static final int ARC = 8;
        private static final int ARROW_XY = 16;
        private static final int ARROW_SIZE = 8;
        private static final int SHADOW_SIZE = 6;
        private static final int SHADOW_TOP_SIZE = 3;
        private static final int SHADOW_SIZE2 = SHADOW_SIZE + 2;

        private final int direction;
        private final int arrowOffset;
        private final Color borderColor;
        private final Border shadowBorder;

        private BalloonBorder(int direction, int arrowOffset, Color borderColor) {
            super(1 + SHADOW_TOP_SIZE, 1 + SHADOW_SIZE, 1 + SHADOW_SIZE, 1 + SHADOW_SIZE);

            this.direction = direction;
            this.arrowOffset = arrowOffset;
            this.borderColor = borderColor;

            switch (direction) {
                case SwingConstants.LEFT -> left += ARROW_SIZE;
                case SwingConstants.TOP -> top += ARROW_SIZE;
                case SwingConstants.RIGHT -> right += ARROW_SIZE;
                default -> bottom += ARROW_SIZE;
            }

            shadowBorder = UIManager.getLookAndFeel() instanceof FlatLaf
                    ? new FlatDropShadowBorder(
                            UIManager.getColor("Popup.dropShadowColor"),
                            new Insets(SHADOW_SIZE2, SHADOW_SIZE2, SHADOW_SIZE2, SHADOW_SIZE2),
                            FlatUIUtils.getUIFloat("Popup.dropShadowOpacity", 0.5f))
                    : null;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                FlatUIUtils.setRenderingHints(g2);
                g2.translate(x, y);

                // The shadow is cast by the body of the balloon, not by its arrow, so the
                // side the arrow is on is taken off before it is drawn.
                int sx = 0;
                int sy = 0;
                int sw = width;
                int sh = height;
                int arrowSize = UIScale.scale(ARROW_SIZE);
                switch (direction) {
                    case SwingConstants.LEFT -> {
                        sx += arrowSize;
                        sw -= arrowSize;
                    }
                    case SwingConstants.TOP -> {
                        sy += arrowSize;
                        sh -= arrowSize;
                    }
                    case SwingConstants.RIGHT -> sw -= arrowSize;
                    default -> sh -= arrowSize;
                }

                if (shadowBorder != null) {
                    shadowBorder.paintBorder(c, g2, sx, sy, sw, sh);
                }

                int bx = UIScale.scale(SHADOW_SIZE);
                int by = UIScale.scale(SHADOW_TOP_SIZE);
                int bw = width - UIScale.scale(SHADOW_SIZE + SHADOW_SIZE);
                int bh = height - UIScale.scale(SHADOW_TOP_SIZE + SHADOW_SIZE);
                g2.translate(bx, by);
                Shape shape = createBalloonShape(bw, bh);

                g2.setColor(c.getBackground());
                g2.fill(shape);

                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(UIScale.scale(1f)));
                g2.draw(shape);
            } finally {
                g2.dispose();
            }
        }

        private Shape createBalloonShape(int width, int height) {
            int arc = UIScale.scale(ARC);
            // Where FlatLaf has a constant, because its balloons always begin at what
            // they point at. Ours are moved to stay inside the window, so the arrow is
            // told where to go; see arrowOffset.
            int xy = arrowOffset;
            int awh = UIScale.scale(ARROW_SIZE);

            Shape rect;
            Shape arrow;
            switch (direction) {
                case SwingConstants.LEFT -> {
                    rect = new RoundRectangle2D.Float(awh, 0, width - 1f - awh, height - 1f,
                            arc, arc);
                    arrow = FlatUIUtils.createPath(awh, xy, 0, xy + awh, awh, xy + awh + awh);
                }
                case SwingConstants.TOP -> {
                    rect = new RoundRectangle2D.Float(0, awh, width - 1f, height - 1f - awh,
                            arc, arc);
                    arrow = FlatUIUtils.createPath(xy, awh, xy + awh, 0, xy + awh + awh, awh);
                }
                case SwingConstants.RIGHT -> {
                    rect = new RoundRectangle2D.Float(0, 0, width - 1f - awh, height - 1f,
                            arc, arc);
                    int x = width - 1 - awh;
                    arrow = FlatUIUtils.createPath(x, xy, x + awh, xy + awh, x, xy + awh + awh);
                }
                default -> {
                    rect = new RoundRectangle2D.Float(0, 0, width - 1f, height - 1f - awh,
                            arc, arc);
                    int y = height - 1 - awh;
                    arrow = FlatUIUtils.createPath(xy, y, xy + awh, y + awh, xy + awh + awh, y);
                }
            }

            Area area = new Area(rect);
            area.add(new Area(arrow));
            return area;
        }
    }
}
