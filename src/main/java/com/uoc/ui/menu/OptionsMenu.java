package com.uoc.ui.menu;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import java.util.function.Consumer;

/**
 * The Options menu: how the interface looks, and what the console is read in.
 *
 * <p>The two sit together because both are about reading comfort, and are kept apart by a
 * separator because choosing a theme and choosing a font are unrelated decisions.
 */
public final class OptionsMenu {

    private OptionsMenu() {
    }

    /**
     * @param onThemeChanged run after the theme changes
     * @param onFontChanged  given the font family the console should be drawn in
     */
    public static JMenu build(Translations translations,
            ThemeManager themeManager, ConsoleFontManager fontManager,
            Runnable onThemeChanged, Consumer<String> onFontChanged) {
        JMenu menu = new JMenu();

        themeManager.addItemsTo(menu, translations, onThemeChanged);
        menu.addSeparator();
        addFontItems(menu, translations, fontManager, onFontChanged);

        translations.register(() -> menu.setText(translations.get(Message.MENU_OPTIONS)));

        return menu;
    }

    private static void addFontItems(JMenu menu, Translations translations,
            ConsoleFontManager fontManager, Consumer<String> onFontChanged) {
        String selected = fontManager.selectedFont();
        ButtonGroup group = new ButtonGroup();

        for (String family : fontManager.availableFonts()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem("", family.equals(selected));
            item.setName(family);
            item.addActionListener(e -> {
                fontManager.select(family);
                onFontChanged.accept(family);
            });
            group.add(item);
            menu.add(item);

            // Only the runtime's own font needs a name the student can read; the rest are
            // called what their makers call them, in every language.
            if (family.equals(ConsoleFontManager.SYSTEM_MONOSPACED)) {
                translations.register(() -> item.setText(translations.get(Message.FONT_SYSTEM)));
            } else {
                item.setText(family);
            }
        }
    }
}
