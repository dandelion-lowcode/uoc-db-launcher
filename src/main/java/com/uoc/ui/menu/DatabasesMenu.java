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

        Database previous = null;
        for (Database database : tabs.databases()) {
            // Three groups, each divided from the last: the databases the course works
            // through, the ones it only mentions, and Jupyter, which is not a database.
            if (previous != null && (previous.kind() != database.kind()
                    || previous.isShownByDefault() != database.isShownByDefault())) {
                menu.addSeparator();
            }
            previous = database;

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
