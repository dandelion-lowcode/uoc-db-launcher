package com.uoc;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.uoc.docker.Database;
import com.uoc.docker.DockerAvailability;
import com.uoc.docker.DockerManager;
import com.uoc.docker.ServiceStatus;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;
import com.uoc.ui.DatabaseTabs;
import com.uoc.ui.ServicesPanel;
import com.uoc.ui.TutorialManager;
import com.uoc.ui.UtilitiesPanel;
import com.uoc.ui.menu.ConsoleFontManager;
import com.uoc.ui.menu.DatabasesMenu;
import com.uoc.ui.menu.FileMenu;
import com.uoc.ui.menu.HelpMenu;
import com.uoc.ui.menu.LanguageManager;
import com.uoc.ui.menu.LanguageMenu;
import com.uoc.ui.menu.OptionsMenu;
import com.uoc.ui.menu.ThemeManager;
import com.uoc.ui.menu.TutorialMenu;
import com.uoc.ui.menu.ZoomMenu;

public class Launcher {

    private static final String APP_ICON = "icons/icon.svg";
    private static final String JUPYTER_URL = "http://localhost:8888/tree/notebooks";

    /**
     * The window a student sees the first time, sixteen by nine.
     *
     * <p>
     * Wide enough for the eleven tabs to sit in one row without a scroll button, and tall
     * enough that the transcript and the box below it both have room to read: the console
     * is where the session is spent, not the panel beside it. It is a starting size, not
     * a limit, and it is brought down to fit a screen that is smaller than it.
     */
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final int CONTENT_GAP = 10;

    public static void main(String[] args) {
        Preferences preferences = Preferences.userNodeForPackage(Launcher.class);
        // What was chosen last time, or -- only on the first run on this machine -- what
        // the machine itself is set to. The menu is handed the same manager, so the tick
        // beside the language and the window it is drawn in cannot disagree.
        LanguageManager languageManager = new LanguageManager(preferences);
        Translations translations = new Translations(
                languageManager.selectedLanguage().locale());
        ThemeManager themeManager = new ThemeManager(preferences);
        themeManager.applySavedTheme();
        ConsoleFontManager fontManager = new ConsoleFontManager(preferences);

        if (!DockerAvailability.isRunning()) {
            DockerAvailability.showMissingDialog(translations);
            System.exit(1);
        }
        SwingUtilities.invokeLater(
                () -> createAndShowGui(themeManager, fontManager, languageManager,
                        translations, preferences));
    }

    private static void createAndShowGui(ThemeManager themeManager,
            ConsoleFontManager fontManager, LanguageManager languageManager,
            Translations translations, Preferences preferences) {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(new FlatSVGIcon(APP_ICON).getImage());
        translations.register(() -> frame.setTitle(translations.get(Message.APP_TITLE)));

        List<Database> databases = List.of(Database.values());
        DatabaseTabs tabs = new DatabaseTabs(databases, translations,
                () -> openJupyter(translations), preferences);

        DockerManager dockerManager = new DockerManager().start();
        // Starting a service from the panel also brings its console into view, because
        // that is what the student is about to use it for.
        java.util.function.Consumer<String> startAndReveal = key -> {
            tabs.reveal(Database.fromKey(key));
            dockerManager.start(key);
        };
        ServicesPanel servicesPanel = new ServicesPanel(databases,
                startAndReveal, dockerManager::stop, translations);
        TutorialManager tutorialManager = new TutorialManager(frame, translations);

        UtilitiesPanel utilitiesPanel = new UtilitiesPanel(Launcher::openInBrowser, translations);

        // A service is listed beside the tabs exactly while it is one the student has
        // chosen, which is the same thing its tab and its tick say. The panel is told
        // what was chosen rather than working it out, and it alone decides when a
        // service that is on its way out stops being shown.
        databases.forEach(database ->
                servicesPanel.setChosen(database.key(), tabs.isShown(database)));
        tabs.addVisibilityListener(
                (database, shown) -> servicesPanel.setChosen(database.key(), shown));

        // The utilities belong to whichever tab is in front rather than to the window, so
        // they follow the tab rather than what has been chosen.
        tabs.addFrontTabListener(utilitiesPanel::setFrontTab);

        // What the student is looking at is what they mean to use. Bringing a tab to the
        // front starts its service if it is not running, so that a database they clicked
        // on is one they can type into. Nothing is started while something is already
        // happening to it: clicking about during a ten-minute image pull must not queue
        // commands against a service that is busy.
        Map<Database, ServiceStatus> lastKnown = new EnumMap<>(Database.class);
        tabs.addActivationListener(database -> {
            ServiceStatus status = lastKnown.getOrDefault(database, ServiceStatus.STOPPED);
            if (!status.isWaiting() && !status.isUp()) {
                dockerManager.start(database.key());
            }
        });

        dockerManager.setListener((key, status) -> {
            lastKnown.put(Database.fromKey(key), status);
            servicesPanel.updateStatus(key, status);
            tabs.setSendEnabled(key, status == ServiceStatus.HEALTHY);
            // The console comes back as soon as the download is over, whichever way it
            // ended. What it printed stays on screen: when it failed, that is the only
            // account of why.
            if (status != ServiceStatus.INSTALLING) {
                tabs.endInstallProgress(key, !status.isFailure());
            }
        });
        dockerManager.setFailureListener(tabs::showFailure);
        dockerManager.setProgressListener(tabs::showInstallProgress);
        databases.forEach(database -> dockerManager.refreshStatus(database.key()));

        // The console starts in whatever font was chosen last time, if this machine
        // still has it.
        tabs.applyFont(fontManager.selectedFont());

        frame.setJMenuBar(buildMenuBar(frame, themeManager, fontManager, languageManager,
                translations, tabs,
                dockerManager, servicesPanel, tutorialManager, preferences));
        frame.setContentPane(buildContentPane(tabs.getComponent(), servicesPanel,
                utilitiesPanel));
        frame.setSize(startingSize(frame));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * The starting size, or as much of it as the screen has room for.
     *
     * <p>
     * A 1366 by 768 laptop, which is what a good many students bring, cannot show 1280 by
     * 720 once the taskbar has taken its share. A window taller than the screen puts its
     * own bottom -- the box the student types into -- somewhere they cannot reach, and
     * they have no reason to suspect the window rather than the launcher.
     */
    private static Dimension startingSize(JFrame frame) {
        GraphicsConfiguration screen = frame.getGraphicsConfiguration() != null
                ? frame.getGraphicsConfiguration()
                : GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration();

        Rectangle bounds = screen.getBounds();
        Insets taken = Toolkit.getDefaultToolkit().getScreenInsets(screen);

        return new Dimension(
                Math.min(WINDOW_WIDTH, bounds.width - taken.left - taken.right),
                Math.min(WINDOW_HEIGHT, bounds.height - taken.top - taken.bottom));
    }

    private static JMenuBar buildMenuBar(JFrame frame, ThemeManager themeManager,
            ConsoleFontManager fontManager, LanguageManager languageManager,
            Translations translations,
            DatabaseTabs tabs, DockerManager dockerManager, ServicesPanel servicesPanel,
            TutorialManager tutorialManager, Preferences preferences) {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(FileMenu.build(translations));
        // Unticking is a student saying they are finished with a service, so it gives the
        // disk back as well as stopping it. The button beside the service only stops it:
        // that one keeps their place, and coming back to it should not be a download.
        var servicesMenu = DatabasesMenu.build(tabs, dockerManager::start,
                dockerManager::stopAndDiscardImage,
                database -> confirmUninstall(frame, database, translations), translations);
        menuBar.add(servicesMenu);
        menuBar.add(ZoomMenu.build(frame, preferences, translations, tabs::applyZoom));
        // The indicators take their colours from the theme's palette, so the panel has
        // to
        // be repainted alongside the consoles when the theme changes.
        menuBar.add(OptionsMenu.build(translations, themeManager, fontManager,
                () -> {
                    tabs.applyThemeColors();
                    servicesPanel.applyThemeColors();
                }, tabs::applyFont));
        menuBar.add(LanguageMenu.build(translations, languageManager));
        menuBar.add(TutorialMenu.build(() -> tutorialManager.show(
                servicesPanel.getComponent(), servicesPanel.actionButtonFor(Database.MONGO.key()),
                servicesMenu), translations));
        menuBar.add(HelpMenu.build(frame, translations));
        return menuBar;
    }

    private static JPanel buildContentPane(JTabbedPane tabbedPane, ServicesPanel servicesPanel,
            UtilitiesPanel utilitiesPanel) {
        // The utilities sit under the services in their own box, and the box takes no
        // room at all while there is nothing in it to offer.
        JPanel besideTheTabs = new JPanel(new BorderLayout(CONTENT_GAP, CONTENT_GAP));
        besideTheTabs.add(servicesPanel.getComponent(), BorderLayout.NORTH);
        besideTheTabs.add(utilitiesPanel.getComponent(), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(besideTheTabs, BorderLayout.NORTH);

        JPanel contentPane = new JPanel(new BorderLayout(CONTENT_GAP, CONTENT_GAP));
        contentPane.setBorder(BorderFactory.createEmptyBorder(CONTENT_GAP, CONTENT_GAP, CONTENT_GAP, CONTENT_GAP));
        contentPane.add(tabbedPane, BorderLayout.CENTER);
        contentPane.add(rightPanel, BorderLayout.EAST);
        return contentPane;
    }

    /**
     * Asks before putting a service away, because putting it away is not undoable.
     *
     * <p>
     * Unticking stops the service, removes its container and removes its image. The
     * image is the part worth asking about: choosing the service again is a fresh
     * download, and the Twitter graph is half a gigabyte of it. Everything the container
     * was holding goes too, which for a student who has spent an evening loading data is
     * the whole evening.
     *
     * @return whether the student said yes. Anything else -- No, Escape, closing the
     *         dialog -- leaves the service exactly as it was.
     */
    private static boolean confirmUninstall(JFrame frame, Database database,
            Translations translations) {
        int answer = javax.swing.JOptionPane.showConfirmDialog(frame,
                translations.format(Message.DIALOG_UNINSTALL_MESSAGE, database.displayName()),
                translations.get(Message.DIALOG_UNINSTALL_TITLE),
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        return answer == javax.swing.JOptionPane.YES_OPTION;
    }

    /**
     * Opens a URL in whatever browser the machine uses.
     *
     * <p>
     * Every service publishes its ports on this machine, so these all answer on
     * localhost. A machine with no browser it can reach from Java is left alone rather
     * than told about it: nothing the student did has failed, and the address is one they
     * can type themselves.
     */
    private static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // Nothing useful to say: the service is running either way.
        }
    }

    private static void openJupyter(Translations translations) {
        try {
            setJupyterLanguage(translations.locale());
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(JUPYTER_URL));
            }
        } catch (Exception ignored) {
        }
    }

    private static void setJupyterLanguage(Locale locale) throws IOException {
        String jupyterLocale = switch (locale.getLanguage()) {
            case "es" -> "es_ES";
            case "ca" -> "ca_ES";
            default -> "default";
        };
        byte[] body = ("{\"raw\":\"{\\\"locale\\\":\\\"" + jupyterLocale + "\\\"}\"}")
                .getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://localhost:8888/lab/api/settings/@jupyterlab/translation-extension:plugin")
                .toURL().openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.getOutputStream().write(body);
        connection.getInputStream().close();
    }

}
