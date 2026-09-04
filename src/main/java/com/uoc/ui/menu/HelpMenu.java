package com.uoc.ui.menu;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

public final class HelpMenu {

    private HelpMenu() {
    }

    public static JMenu build(JFrame frame, Translations translations) {
        JMenuItem aboutItem = new JMenuItem();
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                new JLabel(translations.get(Message.ABOUT_TEXT)), aboutItem.getText(),
                JOptionPane.PLAIN_MESSAGE));

        JMenu menu = new JMenu();
        menu.add(aboutItem);

        translations.register(() -> {
            menu.setText(translations.get(Message.MENU_HELP));
            aboutItem.setText(translations.get(Message.MENU_ABOUT));
        });

        return menu;
    }
}
