package com.uoc.ui.menu;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;
import com.uoc.platform.SystemDarkMode;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

public class ThemeManager {

    private enum Theme {
        LIGHT(Message.THEME_LIGHT),
        DARK(Message.THEME_DARK),
        SYSTEM(Message.THEME_SYSTEM);

        private final Message message;

        Theme(Message message) {
            this.message = message;
        }

        Message message() {
            return message;
        }

        static Theme fromPreference(String value) {
            for (Theme theme : values()) {
                if (theme.name().equalsIgnoreCase(value)) {
                    return theme;
                }
            }
            return SYSTEM;
        }
    }

    private static final String THEME_PREF_KEY = "theme";

    /**
     * Where our own definitions live: FlatLaf.properties for what holds under every
     * theme, and one file per theme for what does not.
     */
    private static final String THEME_PACKAGE = "themes";

    private final Preferences prefs;

    public ThemeManager(Preferences prefs) {
        this.prefs = prefs;
    }

    public void applySavedTheme() {
        registerOurColours();
        apply(savedTheme());
    }

    /**
     * Adds the application's own colours to the look and feel.
     *
     * <p>
     * FlatLaf reads {@code themes/FlatLightLaf.properties} and its dark twin the same way
     * it reads its own, so the console palette and the service indicators end up in
     * {@link javax.swing.UIManager} beside every other colour in the interface. Switching
     * theme reloads them along with everything else, and there is no second system to
     * tell about the change.
     *
     * <p>
     * Registered before any theme is installed, or the first one would be built without
     * them and every colour of ours would come back null.
     */
    private static void registerOurColours() {
        FlatLaf.registerCustomDefaultsSource(THEME_PACKAGE);
    }

    private Theme savedTheme() {
        return Theme.fromPreference(prefs.get(THEME_PREF_KEY, Theme.SYSTEM.name()));
    }

    private void apply(Theme theme) {
        boolean dark = theme == Theme.DARK || (theme == Theme.SYSTEM && SystemDarkMode.isEnabled());
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }

    /** Adds the theme choices to a menu that somebody else owns. */
    public void addItemsTo(JMenu menu, Translations translations, Runnable onThemeChanged) {
        Theme saved = savedTheme();
        JRadioButtonMenuItem lightItem = new JRadioButtonMenuItem("", saved == Theme.LIGHT);
        JRadioButtonMenuItem darkItem = new JRadioButtonMenuItem("", saved == Theme.DARK);
        JRadioButtonMenuItem systemItem = new JRadioButtonMenuItem("", saved == Theme.SYSTEM);

        ButtonGroup group = new ButtonGroup();
        group.add(lightItem);
        group.add(darkItem);
        group.add(systemItem);

        ActionListener onChange = e -> {
            Theme selected = lightItem.isSelected() ? Theme.LIGHT
                    : darkItem.isSelected() ? Theme.DARK : Theme.SYSTEM;

            // The window as it looks now is photographed and left on top, so the change
            // happens behind a still image and is then faded out. Without it the window
            // is repainted piece by piece and the switch arrives as a flicker.
            FlatAnimatedLafChange.showSnapshot();

            apply(selected);
            prefs.put(THEME_PREF_KEY, selected.name());

            // Now rather than later: the snapshot is hidden on the line after this one,
            // and deferring the repaint would uncover a window not yet redrawn.
            FlatLaf.updateUI();
            onThemeChanged.run();

            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        };
        lightItem.addActionListener(onChange);
        darkItem.addActionListener(onChange);
        systemItem.addActionListener(onChange);

        menu.add(lightItem);
        menu.add(darkItem);
        menu.add(systemItem);

        translations.register(() -> {
            lightItem.setText(translations.get(Theme.LIGHT.message()));
            darkItem.setText(translations.get(Theme.DARK.message()));
            systemItem.setText(translations.get(Theme.SYSTEM.message()));
        });
    }
}
