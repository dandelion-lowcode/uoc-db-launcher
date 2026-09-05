package com.uoc.ui.menu;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public class ZoomMenu {

    private static final String ZOOM_PREF_KEY = "zoom";
    private static final float NO_ZOOM = 1f;

    private ZoomMenu() {
    }

    /**
     * The zoom last chosen on this machine, or none.
     *
     * <p>
     * A factor the application no longer offers is ignored rather than forced: the list
     * of steps could be shortened in a later version, and a setting left over from an
     * older one should not leave the interface at a size the menu cannot show as ticked.
     */
    private static float savedZoom(Preferences prefs) {
        float saved = prefs.getFloat(ZOOM_PREF_KEY, NO_ZOOM);
        for (float supported : UIScale.getSupportedZoomFactors()) {
            if (supported == saved) {
                return saved;
            }
        }
        return NO_ZOOM;
    }

    /**
     * @param prefs         where the chosen zoom is remembered between sessions
     * @param onZoomChanged run after every zoom change, for the parts of the interface
     *                      that set their own fonts and would otherwise stay the same size
     */
    public static JMenu build(Window window, Preferences prefs, Translations translations,
            Runnable onZoomChanged) {
        JMenu menu = new JMenu();

        JMenuItem resetItem = new JMenuItem();
        resetItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        resetItem.addActionListener(e -> {
            if (UIScale.zoomReset()) {
                FlatLaf.updateUI();
            }
        });
        menu.add(resetItem);

        JMenuItem inItem = new JMenuItem();
        inItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        inItem.addActionListener(e -> {
            if (UIScale.zoomIn()) {
                FlatLaf.updateUI();
            }
        });
        menu.add(inItem);

        JMenuItem outItem = new JMenuItem();
        outItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        outItem.addActionListener(e -> {
            if (UIScale.zoomOut()) {
                FlatLaf.updateUI();
            }
        });
        menu.add(outItem);

        UIScale.setSupportedZoomFactors(new float[] { 0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.75f, 2f });

        // Restored before the items are built, so the one that is ticked is the one in
        // use. A student who needs the interface larger needs it larger every time, not
        // once per session.
        UIScale.setZoomFactor(savedZoom(prefs));
        float currentZoomFactor = UIScale.getZoomFactor();

        ButtonGroup group = new ButtonGroup();
        Map<Float, JCheckBoxMenuItem> items = new LinkedHashMap<>();

        menu.addSeparator();
        for (float zoomFactor : UIScale.getSupportedZoomFactors()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem((int) (zoomFactor * 100) + "%");
            item.setSelected(zoomFactor == currentZoomFactor);
            item.addActionListener(e -> {
                if (UIScale.setZoomFactor(zoomFactor)) {
                    FlatLaf.updateUI();
                }
            });
            menu.add(item);
            group.add(item);
            items.put(zoomFactor, item);
        }

        UIScale.addPropertyChangeListener(e -> {
            if (UIScale.PROP_ZOOM_FACTOR.equals(e.getPropertyName())) {
                JCheckBoxMenuItem item = items.get(UIScale.getZoomFactor());
                if (item != null) {
                    item.setSelected(true);
                }
                adjustWindowBounds(window, (float) e.getOldValue(), (float) e.getNewValue());
                // Saved here rather than beside each of the four ways to change the zoom,
                // so a fifth could not be added that forgets to.
                prefs.putFloat(ZOOM_PREF_KEY, UIScale.getZoomFactor());
                onZoomChanged.run();
            }
        });

        translations.register(() -> {
            menu.setText(translations.get(Message.MENU_ZOOM));
            resetItem.setText(translations.get(Message.ZOOM_RESET));
            inItem.setText(translations.get(Message.ZOOM_IN));
            outItem.setText(translations.get(Message.ZOOM_OUT));
        });

        return menu;
    }

    private static void adjustWindowBounds(Window window, float oldZoomFactor, float newZoomFactor) {
        if (window instanceof Frame && ((Frame) window).getExtendedState() != Frame.NORMAL) {
            return;
        }

        Rectangle oldBounds = window.getBounds();
        float factor = (1f / oldZoomFactor) * newZoomFactor;
        int newWidth = (int) (oldBounds.width * factor);
        int newHeight = (int) (oldBounds.height * factor);
        int newX = oldBounds.x - ((newWidth - oldBounds.width) / 2);
        int newY = oldBounds.y - ((newHeight - oldBounds.height) / 2);

        GraphicsConfiguration gc = window.getGraphicsConfiguration();
        Rectangle screenBounds = gc.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle maxBounds = new Rectangle(
                screenBounds.x + screenInsets.left,
                screenBounds.y + screenInsets.top,
                screenBounds.width - screenInsets.left - screenInsets.right,
                screenBounds.height - screenInsets.top - screenInsets.bottom);

        newWidth = Math.min(newWidth, maxBounds.width);
        newHeight = Math.min(newHeight, maxBounds.height);
        newX = Math.max(Math.min(newX, maxBounds.width - newWidth), maxBounds.x);
        newY = Math.max(Math.min(newY, maxBounds.height - newHeight), maxBounds.y);

        window.setBounds(newX, newY, newWidth, newHeight);
    }
}
