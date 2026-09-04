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

public class ZoomMenu {

    private ZoomMenu() {
    }

    /**
     * @param onZoomChanged run after every zoom change, for the parts of the interface
     *                      that set their own fonts and would otherwise stay the same size
     */
    public static JMenu build(Window window, Translations translations, Runnable onZoomChanged) {
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
