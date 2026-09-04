package com.uoc.ui.menu;

import com.uoc.docker.Database;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;
import com.uoc.ui.DatabaseTabs;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class DatabasesMenu {

    private DatabasesMenu() {
    }

    public static JMenu build(DatabaseTabs tabs, Consumer<String> onStart, Consumer<String> onStop,
            Translations translations) {
        JMenu menu = new JMenu();
        Map<Database, JCheckBoxMenuItem> items = new EnumMap<>(Database.class);

        Boolean previousWasShownByDefault = null;
        for (Database database : tabs.databases()) {
            // The databases the course does not use are grouped apart from the ones it
            // does.
            if (previousWasShownByDefault != null
                    && previousWasShownByDefault != database.isShownByDefault()) {
                menu.addSeparator();
            }
            previousWasShownByDefault = database.isShownByDefault();

            JCheckBoxMenuItem item = new JCheckBoxMenuItem(database.displayName());
            item.setName(database.key());
            item.setSelected(tabs.isShown(database));
            items.put(database, item);
            item.addActionListener(e -> {
                if (item.isSelected()) {
                    tabs.show(database);
                    onStart.accept(database.key());
                } else {
                    tabs.hide(database);
                    onStop.accept(database.key());
                }
            });
            menu.add(item);
        }

        // The tabs decide what is showing; this menu only reports it. Starting a
        // service from the panel beside the tabs also opens its tab, and the tick has
        // to follow.
        tabs.addVisibilityListener((database, shown) -> {
            JCheckBoxMenuItem item = items.get(database);
            if (item != null) {
                item.setSelected(shown);
            }
        });

        translations.register(() -> menu.setText(translations.get(Message.LABEL_SERVICES)));

        return menu;
    }
}
