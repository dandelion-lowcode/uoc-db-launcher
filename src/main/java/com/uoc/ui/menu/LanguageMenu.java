package com.uoc.ui.menu;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class LanguageMenu {

    /**
     * The languages the bundles provide. English is the one without a suffix, so it is also
     * what a system set to any other language falls back to.
     */
    public enum Language {
        // The order here is the order of the menu.
        CATALAN(Locale.of("ca"), Message.MENU_CATALAN),
        SPANISH(Locale.of("es"), Message.MENU_SPANISH),
        ENGLISH(Locale.ENGLISH, Message.MENU_ENGLISH);

        private final Locale locale;
        private final Message message;

        Language(Locale locale, Message message) {
            this.locale = locale;
            this.message = message;
        }

        public Locale locale() {
            return locale;
        }

        /** The language a locale will actually be shown in, English being the fallback. */
        public static Language of(Locale locale) {
            for (Language language : values()) {
                if (language.locale.getLanguage().equals(locale.getLanguage())) {
                    return language;
                }
            }
            return ENGLISH;
        }
    }

    private LanguageMenu() {
    }

    public static JMenu build(Translations translations) {
        Map<Language, JRadioButtonMenuItem> items = new EnumMap<>(Language.class);
        ButtonGroup group = new ButtonGroup();
        JMenu menu = new JMenu();

        for (Language language : Language.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem();
            item.addActionListener(e -> translations.setLocale(language.locale()));
            group.add(item);
            menu.add(item);
            items.put(language, item);
        }

        items.get(Language.of(Locale.getDefault())).setSelected(true);

        translations.register(() -> {
            menu.setText(translations.get(Message.MENU_LANGUAGE));
            items.forEach((language, item) -> item.setText(translations.get(language.message)));
        });

        return menu;
    }
}
