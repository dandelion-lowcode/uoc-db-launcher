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
     * The languages the bundles provide. Which of them a session opens in is
     * {@link LanguageManager}'s to answer.
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

        /** How this language names itself in the menu. */
        Message message() {
            return message;
        }

    }

    private LanguageMenu() {
    }

    /**
     * @param languages where a choice is remembered, and which one to show as chosen
     */
    public static JMenu build(Translations translations, LanguageManager languages) {
        Map<Language, JRadioButtonMenuItem> items = new EnumMap<>(Language.class);
        ButtonGroup group = new ButtonGroup();
        JMenu menu = new JMenu();

        for (Language language : Language.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem();
            item.addActionListener(e -> {
                // Written down before the window is redrawn in it: the redrawing is the
                // part that can fail, and a language shown is a language chosen.
                languages.select(language);
                translations.setLocale(language.locale());
            });
            group.add(item);
            menu.add(item);
            items.put(language, item);
        }

        items.get(languages.selectedLanguage()).setSelected(true);

        translations.register(() -> {
            menu.setText(translations.get(Message.MENU_LANGUAGE));
            items.forEach((language, item) -> item.setText(translations.get(language.message)));
        });

        return menu;
    }
}
