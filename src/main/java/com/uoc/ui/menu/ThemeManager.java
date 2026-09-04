package com.uoc.ui.menu;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;
import com.uoc.platform.SystemDarkMode;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

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

    private final Preferences prefs;

    public ThemeManager(Preferences prefs) {
        this.prefs = prefs;
    }

    public void applySavedTheme() {
        apply(savedTheme());
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
            apply(selected);
            prefs.put(THEME_PREF_KEY, selected.name());
            FlatLaf.updateUILater();
            onThemeChanged.run();
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
