package com.uoc.ui.menu;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

public final class TutorialMenu {

    private TutorialMenu() {
    }

    public static JMenu build(Runnable onShow, Translations translations) {
        JMenu menu = new JMenu();
        JMenuItem menuItem = new JMenuItem();
        menuItem.addActionListener(e -> onShow.run());
        menu.add(menuItem);
        translations.register(() -> {
            menu.setText(translations.get(Message.MENU_TUTORIAL));
            menuItem.setText(translations.get(Message.MENU_TUTORIAL_START));
        });
        return menu;
    }
}
