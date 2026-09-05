package com.uoc.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.uoc.docker.Database;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Translations;

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

    /** The longer side of a tab's icon. The label beside it is what names the service. */
    private static final int TAB_ICON_SIZE = 28;
    private static final String SHOWN_PREF_KEY = "shownServices";

    private final List<Database> databases;
    private final java.util.prefs.Preferences preferences;
    // Every service has a panel; only a database has a console behind it. Keeping
    // the two
    // apart is what lets Jupyter sit among the tabs without every console operation
    // having to ask whether this one can be typed at.
    private final Map<Database, JPanel> panels = new LinkedHashMap<>();
    private final Map<Database, DatabaseTab> tabs = new LinkedHashMap<>();
    private final Map<Database, FlatSVGIcon> icons = new LinkedHashMap<>();
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final List<VisibilityListener> visibilityListeners = new ArrayList<>();

    // There is one of these, and it lives here with every other tab. It used to be
    // built
    // a second time and added separately, which left two Jupyter tabs: the menu
    // showed
    // and hid one of them while the other, always present, was the one on screen.
    private JupyterTab notebooks;

    /**
     * @param onOpenNotebooks what the notebook tab's button does. It is passed in
     *                        rather
     *                        than decided here because opening the notebooks means
     *                        more
     *                        than opening a browser: the language they are shown in
     *                        is
     *                        put in step with the launcher's first.
     */
    public DatabaseTabs(List<Database> databases, QueryRunner queryRunner,
            Translations translations, Runnable onOpenNotebooks) {
        this(databases, queryRunner, translations, onOpenNotebooks, null);
    }

    /**
     * @param preferences where the open services are remembered between sessions,
     *                    or
     *                    {@code null} to remember nothing, which is what the tests
     *                    want
     */
    public DatabaseTabs(List<Database> databases, QueryRunner queryRunner,
            Translations translations, Runnable onOpenNotebooks,
            java.util.prefs.Preferences preferences) {
        this.databases = databases;
        this.preferences = preferences;

        // Which tabs to open: the ones left open last time, or the course's own three
        // the
        // first time the application is run on this machine.
        java.util.Set<Database> toShow = remembered();

        for (Database database : databases) {
            if (database.hasQueryConsole()) {
                DatabaseTab tab = new DatabaseTab(database, queryRunner);
                tab.registerTranslations(translations);
                tabs.put(database, tab);
                panels.put(database, tab.getPanel());
            } else {
                notebooks = new JupyterTab(onOpenNotebooks, translations);
                panels.put(database, notebooks.getPanel());
            }
            icons.put(database, tabIcon(database));
            if (toShow.contains(database)) {
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

    /**
     * A service's icon at tab size, with the shape it was drawn in.
     *
     * <p>
     * Asking FlatSVGIcon for a square icon does not fit the drawing into a square: it
     * scales width and height separately, so anything that is not square arrives
     * stretched. Vertica's logo is five times as wide as it is tall and was reaching the
     * tab as a blot. Here the longer side is what gets the tab size, and the shorter one
     * follows from the drawing's own proportions.
     */
    static FlatSVGIcon tabIcon(Database database) {
        FlatSVGIcon drawn = new FlatSVGIcon(database.iconResource());
        double aspect = (double) drawn.getIconWidth() / drawn.getIconHeight();

        int width = aspect >= 1 ? TAB_ICON_SIZE : scaled(TAB_ICON_SIZE * aspect);
        int height = aspect >= 1 ? scaled(TAB_ICON_SIZE / aspect) : TAB_ICON_SIZE;

        return new FlatSVGIcon(database.iconResource(), width, height);
    }

    /** Never rounded away to nothing: a very long, very thin drawing is still drawn. */
    private static int scaled(double side) {
        return Math.max(1, (int) Math.round(side));
    }

    public JTabbedPane getComponent() {
        return tabbedPane;
    }

    /** The button that opens the notebooks, which the tutorial points at. */
    public JButton notebooksButton() {
        return notebooks.getOpenButton();
    }

    public List<Database> databases() {
        return databases;
    }

    /**
     * The console behind a tab, or {@code null} for a service that has none. Prefer
     * the
     * two methods below, which already know what to do when there is no console.
     */
    public DatabaseTab tabFor(String key) {
        return tabs.get(Database.fromKey(key));
    }

    /**
     * Opens or closes the console for typing, following whether the service is
     * ready.
     *
     * <p>
     * A service with no console is left alone rather than guarded against at every
     * call
     * site, which is how the first version of this went wrong.
     */
    public void setSendEnabled(String key, boolean enabled) {
        DatabaseTab tab = tabFor(key);
        if (tab != null) {
            tab.setSendEnabled(enabled);
        }
    }

    /**
     * Prints in the console why Docker refused to start a service.
     *
     * <p>
     * A service with no console has nowhere to print it; the indicator beside the
     * tabs
     * still turns to show something went wrong.
     */
    public void showFailure(String key, String details) {
        DatabaseTab tab = tabFor(key);
        if (tab != null) {
            tab.showFailure(details);
        }
    }

    /**
     * Shows how an install is going in the trace, rewriting it as the news arrives.
     */
    public void showInstallProgress(String key, String text) {
        DatabaseTab tab = tabFor(key);
        if (tab != null) {
            tab.showInstallProgress(text);
        }
    }

    /**
     * The install is over, so the box for typing queries comes back.
     *
     * @param succeeded whether the service came up. When it did, the trace is cleared:
     *                  what the install printed is finished business. When it did not, it
     *                  is kept, being the only account of why.
     */
    public void endInstallProgress(String key, boolean succeeded) {
        DatabaseTab tab = tabFor(key);
        if (tab != null) {
            tab.endInstallProgress(succeeded);
        }
    }

    public void applyThemeColors() {
        tabs.values().forEach(DatabaseTab::applyThemeColors);

        // Hiding a tab takes its panel out of the tabbed pane altogether, so it belongs
        // to no window. What repaints a theme change walks the windows that are
        // showing,
        // which means a hidden tab keeps the look of the theme it was last seen under
        // and
        // comes back wrong: light panels in a dark window. Each one is refreshed here
        // by
        // name, since nothing else will reach it.
        for (Map.Entry<Database, JPanel> entry : panels.entrySet()) {
            if (!isShown(entry.getKey())) {
                SwingUtilities.updateComponentTreeUI(entry.getValue());
            }
        }
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
        return tabbedPane.indexOfComponent(panels.get(database)) >= 0;
    }

    public void show(Database database) {
        if (isShown(database)) {
            return;
        }
        tabbedPane.insertTab(database.displayName(), icons.get(database),
                panels.get(database), null, indexFor(database));
        announce(database, true);
    }

    public void hide(Database database) {
        if (!isShown(database)) {
            return;
        }
        tabbedPane.remove(panels.get(database));
        announce(database, false);
    }

    /** Brings a tab to the front. The tab has to be showing for there to be one. */
    public void select(Database database) {
        if (isShown(database)) {
            tabbedPane.setSelectedComponent(panels.get(database));
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
        remember();
        for (VisibilityListener listener : visibilityListeners) {
            listener.onVisibilityChanged(database, shown);
        }
    }

    /**
     * Writes down which services are open, so the next session starts where this
     * one left
     * off.
     *
     * <p>
     * A student working through the Cassandra exercises should not have to tick
     * Cassandra
     * again every time they open the launcher, and the containers themselves are
     * still
     * running from last time, so the tabs are the only part that had been
     * forgetting.
     */
    private void remember() {
        if (preferences == null) {
            return;
        }
        preferences.put(SHOWN_PREF_KEY, databases.stream().filter(this::isShown)
                .map(Database::key).reduce((one, other) -> one + "," + other).orElse(""));
    }

    /**
     * Which services were open last time, or the ones the course starts with when
     * this is
     * the first run.
     *
     * <p>
     * A name that no longer matches a service is skipped rather than failing: the
     * list is
     * a convenience, and a database renamed between versions should not stop the
     * application from opening.
     */
    private java.util.Set<Database> remembered() {
        String saved = preferences == null ? null : preferences.get(SHOWN_PREF_KEY, null);
        if (saved == null) {
            return databases.stream().filter(Database::isShownByDefault)
                    .collect(java.util.stream.Collectors.toSet());
        }
        java.util.Set<Database> shown = new java.util.HashSet<>();
        for (String key : saved.split(",")) {
            if (key.isBlank()) {
                continue;
            }
            try {
                shown.add(Database.fromKey(key.strip()));
            } catch (IllegalArgumentException ignored) {
                // A service this version no longer has.
            }
        }
        return shown;
    }

    // Keeps a tab that comes back in the declared database order instead of
    // appending it last.
    private int indexFor(Database database) {
        int index = 0;
        for (Database candidate : databases) {
            if (candidate == database) {
                break;
            }
            if (tabbedPane.indexOfComponent(panels.get(candidate)) >= 0) {
                index++;
            }
        }
        return index;
    }
}
