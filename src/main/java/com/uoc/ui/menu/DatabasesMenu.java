package com.uoc.ui.menu;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;

import com.uoc.docker.Database;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;
import com.uoc.ui.DatabaseTabs;

public class DatabasesMenu {

    private DatabasesMenu() {
    }

    /**
     * @param onStop         what unticking does. It stops the service and gives back the
     *                       disk its image was using, which is why it is asked about
     *                       first.
     * @param confirmRemoval answers whether the student really means it. Passed in rather
     *                       than opened here so that a test can answer without a dialog
     *                       standing in front of it for ever.
     */
    public static JMenu build(DatabaseTabs tabs, Consumer<String> onStart, Consumer<String> onStop,
            java.util.function.Predicate<Database> confirmRemoval, Translations translations) {
        JMenu menu = new JMenu();
        Map<Database, JCheckBoxMenuItem> items = new EnumMap<>(Database.class);

        Database previous = null;
        for (Database database : tabs.databases()) {
            // A line wherever the block changes. Which block each service is in is said
            // once, in the enum, so adding one never means coming back here.
            if (previous != null && previous.group() != database.group()) {
                menu.addSeparator();
            }
            previous = database;

            JCheckBoxMenuItem item = new JCheckBoxMenuItem(database.displayName());
            item.setName(database.key());
            item.setSelected(tabs.isShown(database));
            items.put(database, item);
            item.addActionListener(e -> {
                if (item.isSelected()) {
                    // Brought to the front, not merely added: ticking a service here is
                    // asking to work with it, and leaving its tab behind the one already
                    // open makes the menu look as though it did nothing.
                    tabs.reveal(database);
                    onStart.accept(database.key());
                } else if (confirmRemoval.test(database)) {
                    tabs.hide(database);
                    onStop.accept(database.key());
                } else {
                    // Put the tick back. The item selects itself the moment it is
                    // clicked, so saying no here has to undo that, or the menu would
                    // disagree with a service that is still there.
                    item.setSelected(true);
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
