package com.uoc.ui.menu;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

public final class FileMenu {

    private FileMenu() {
    }

    public static JMenu build(Translations translations) {
        JMenuItem closeItem = new JMenuItem();
        closeItem.addActionListener(e -> System.exit(0));

        JMenu menu = new JMenu();
        menu.add(closeItem);

        translations.register(() -> {
            menu.setText(translations.get(Message.MENU_FILE));
            closeItem.setText(translations.get(Message.MENU_CLOSE));
        });

        return menu;
    }
}
