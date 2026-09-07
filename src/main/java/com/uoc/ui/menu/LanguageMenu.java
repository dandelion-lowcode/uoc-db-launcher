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
     * The languages the bundles provide.
     *
     * <p>
     * English is the one without a suffix, which makes it the bundle a missing language
     * resolves to, but it is not what a student is shown: the course is taught in
     * Spanish, so that is where the launcher starts unless the machine asks for Catalan.
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

        /**
         * The language a locale will actually be shown in.
         *
         * <p>
         * Anything the launcher has no bundle for is Spanish, that being the language the
         * course is taught in: a student whose machine is set to something else is far
         * likelier to want the wording their notes use than English.
         */
        public static Language of(Locale locale) {
            for (Language language : values()) {
                if (language.locale.getLanguage().equals(locale.getLanguage())) {
                    return language;
                }
            }
            return SPANISH;
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
