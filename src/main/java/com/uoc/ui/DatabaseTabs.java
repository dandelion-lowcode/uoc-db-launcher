package com.uoc.ui;

import com.uoc.docker.Database;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Translations;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.JTabbedPane;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tabbed pane and the console inside each tab, kept together because
 * nothing useful
 * can be done with one without the others: showing a database again means
 * knowing its
 * icon, its console and where the tab belongs in the declared order.
 */
public class DatabaseTabs {

    /**
     * Told when a database gains or loses its tab, so the menu can agree with the
     * tabs.
     */
    public interface VisibilityListener {
        void onVisibilityChanged(Database database, boolean shown);
    }

    private static final int TAB_ICON_SIZE = 32;

    private final List<Database> databases;
    private final Map<Database, DatabaseTab> tabs = new LinkedHashMap<>();
    private final Map<Database, FlatSVGIcon> icons = new LinkedHashMap<>();
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final List<VisibilityListener> visibilityListeners = new ArrayList<>();

    public DatabaseTabs(List<Database> databases, QueryRunner queryRunner, Translations translations) {
        this.databases = databases;
        for (Database database : databases) {
            DatabaseTab tab = new DatabaseTab(database, queryRunner);
            tab.registerTranslations(translations);
            tabs.put(database, tab);
            icons.put(database, new FlatSVGIcon(database.iconResource(), TAB_ICON_SIZE, TAB_ICON_SIZE));
            if (database.isShownByDefault()) {
                show(database);
            }
        }

        tabbedPane.addChangeListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            for (DatabaseTab tab : tabs.values()) {
                if (tab.getPanel() == selected) {
                    tab.focusInput();
                    break;
                }
            }
        });
    }

    public JTabbedPane getComponent() {
        return tabbedPane;
    }

    public void addUtilityTab(String title, FlatSVGIcon icon, Component component) {
        tabbedPane.addTab(title, icon, component);
    }

    public List<Database> databases() {
        return databases;
    }

    public DatabaseTab tabFor(String key) {
        return tabs.get(Database.fromKey(key));
    }

    public void applyThemeColors() {
        tabs.values().forEach(DatabaseTab::applyThemeColors);
    }

    /**
     * Redraws every console at the current zoom, including the tabs not on screen.
     */
    public void applyZoom() {
        tabs.values().forEach(DatabaseTab::applyZoom);
    }

    /** Redraws every console in another font, including the tabs not on screen. */
    public void applyFont(String family) {
        tabs.values().forEach(tab -> tab.applyFont(family));
    }

    public void addVisibilityListener(VisibilityListener listener) {
        visibilityListeners.add(listener);
    }

    public boolean isShown(Database database) {
        return tabbedPane.indexOfComponent(tabs.get(database).getPanel()) >= 0;
    }

    public void show(Database database) {
        if (isShown(database)) {
            return;
        }
        tabbedPane.insertTab(database.displayName(), icons.get(database),
                tabs.get(database).getPanel(), null, indexFor(database));
        announce(database, true);
    }

    public void hide(Database database) {
        if (!isShown(database)) {
            return;
        }
        tabbedPane.remove(tabs.get(database).getPanel());
        announce(database, false);
    }

    /** Brings a tab to the front. The tab has to be showing for there to be one. */
    public void select(Database database) {
        if (isShown(database)) {
            tabbedPane.setSelectedComponent(tabs.get(database).getPanel());
        }
    }

    /**
     * Puts a database in front of the student, which is what starting one is for. A
     * database that is already showing is left where it is: they asked for it to
     * run,
     * not to be taken away from whatever tab they were reading.
     */
    public void reveal(Database database) {
        if (isShown(database)) {
            return;
        }
        show(database);
        select(database);
    }

    private void announce(Database database, boolean shown) {
        for (VisibilityListener listener : visibilityListeners) {
            listener.onVisibilityChanged(database, shown);
        }
    }

    // Keeps a tab that comes back in the declared database order instead of
    // appending it last.
    private int indexFor(Database database) {
        int index = 0;
        for (Database candidate : databases) {
            if (candidate == database) {
                break;
            }
            if (tabbedPane.indexOfComponent(tabs.get(candidate).getPanel()) >= 0) {
                index++;
            }
        }
        return index;
    }
}
