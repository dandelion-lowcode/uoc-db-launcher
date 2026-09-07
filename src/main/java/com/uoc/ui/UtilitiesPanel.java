package com.uoc.ui;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.uoc.docker.Database;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

/**
 * The things a service can be used with besides its own console.
 *
 * <p>
 * Every service publishes its ports on this machine already, so a graph can be opened in
 * a browser and a collection in whatever tool a student prefers. What was missing was
 * anywhere saying so: a student had no reason to guess that Neo4j has a web interface, or
 * which of the two is on which port.
 *
 * <p>
 * What is offered belongs to the tab in front of it: these are ways of using the database
 * a student is working in, not of the window at large. Whether that service is running
 * does not come into it -- that Neo4j has a browser is a standing fact about Neo4j, worth
 * knowing before starting it rather than only afterwards. The panel appears only while it
 * has a button in it, an empty box headed "Utilities" being a standing question about
 * what is missing.
 */
public class UtilitiesPanel {

    private static final int ICON_SIZE = 16;
    private static final int BUTTON_HEIGHT = 30;
    private static final int GAP = 4;

    /** Room between the box's own frame and the buttons inside it. */
    private static final int PANEL_PADDING = 6;

    /**
     * Another way of getting at one database, and what to call it.
     *
     * @param note    a line under the button, or {@code null} for none. It takes the
     *                address below as its one argument, so the link is written once here
     *                rather than three times in the translations.
     * @param tooltip what the button will do, or {@code null} when the wording says it
     * @param subject the address the tooltip is about
     * @param open    what pressing it does
     */
    private record Utility(Database database, String icon, Message name,
            Message note, String noteUrl, Message tooltip, String subject, Runnable open) {
    }

    /** Where MongoDB answers on this machine, which is what a client has to be told. */
    private static final String MONGO_CONNECTION = "mongodb://localhost:27017";

    private final List<Utility> known;

    private final JPanel component = new JPanel();
    private final TitledBorder heading = BorderFactory.createTitledBorder("");
    private final Map<Utility, JButton> buttons = new LinkedHashMap<>();
    private final Map<Utility, JComponent> notes = new LinkedHashMap<>();


    /** The tab the student is looking at, which is what these belong to. */
    private Database frontTab;

    /**
     * @param openInBrowser what opens a URL, passed in rather than decided here so that a
     *                      test can watch what would have been opened
     */
    public UtilitiesPanel(Consumer<String> openInBrowser, Translations translations) {
        component.setBorder(BorderFactory.createCompoundBorder(heading,
                BorderFactory.createEmptyBorder(PANEL_PADDING, PANEL_PADDING,
                        PANEL_PADDING, PANEL_PADDING)));

        component.setLayout(new BoxLayout(component, BoxLayout.Y_AXIS));

        known = new ArrayList<>();
        // The Twitter graph answers on a port of its own, the plain Neo4j having taken
        // 7474 first.
        known.add(new Utility(Database.NEO4J_TWITTER, "icons/neo4j-browser.svg",
                Message.BUTTON_OPEN_NEO4J_BROWSER, null, null, null, null,
                () -> openInBrowser.accept("http://localhost:17474")));

        // The address rather than the program. Starting Studio 3T from here would mean
        // knowing where it is, and that is three operating systems, three editions and a
        // vendor folder with a version in it -- a guess that breaks on the first machine
        // that installed it anywhere else. The connection string is the one thing every
        // MongoDB client asks for, and it is the same on every machine.
        known.add(new Utility(Database.MONGO, "icons/studio3t.svg",
                Message.BUTTON_COPY_CONNECTION, Message.LABEL_STUDIO3T_DOWNLOAD,
                "https://robomongo.org/download.php",
                Message.TOOLTIP_COPY_CONNECTION, MONGO_CONNECTION,
                () -> copyToClipboard(MONGO_CONNECTION)));

        for (Utility utility : known) {
            JButton button = new JButton();
            button.setName(utility.database().key());
            button.setIcon(new FlatSVGIcon(utility.icon(), ICON_SIZE, ICON_SIZE));
            button.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, BUTTON_HEIGHT));
            button.addActionListener(event -> utility.open().run());
            buttons.put(utility, button);

            if (utility.note() != null) {
                notes.put(utility, noteUnder(utility, openInBrowser));
            }
        }

        translations.register(() -> {
            heading.setTitle(" " + translations.get(Message.LABEL_UTILITIES) + " ");
            buttons.forEach((utility, button) -> {
                button.setText(translations.get(utility.name()));
                if (utility.tooltip() != null) {
                    button.setToolTipText(
                            translations.format(utility.tooltip(), utility.subject()));
                }
            });
            notes.forEach((utility, note) -> ((JEditorPane) note)
                    .setText(translations.format(utility.note(), utility.noteUrl())));
            component.repaint();
        });

        showWhatIsAvailable();
    }

    /**
     * The line under a button, with a working link in it.
     *
     * <p>
     * A JLabel would draw the anchor and ignore the click, so this is the same editor
     * pane the missing-Docker dialog uses: transparent, not editable, and carrying the
     * panel's own font rather than the one HTML defaults to.
     */
    private static JComponent noteUnder(Utility utility, Consumer<String> openInBrowser) {
        JEditorPane note = new JEditorPane("text/html", "");
        note.setEditable(false);
        note.setOpaque(false);
        // Room above it, so the line reads as a note about the button rather than as
        // part of it.
        note.setBorder(BorderFactory.createEmptyBorder(GAP, 0, GAP, 0));
        note.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        note.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        note.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openInBrowser.accept(utility.noteUrl());
            }
        });
        return note;
    }

    /**
     * Puts an address where the next paste will find it.
     *
     * <p>
     * A clipboard belongs to the desktop rather than to us, and a desktop that will not
     * give it up -- another application holding it, or none at all -- is not something a
     * student did or can undo.
     */
    private static void copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        } catch (Exception ignored) {
            // Nothing useful to say: the address is on the button's own tooltip.
        }
    }

    public JPanel getComponent() {
        return component;
    }

    /**
     * Told which tab the student is looking at.
     *
     * <p>
     * These belong to one database rather than to the window: Neo4j's browser is a way of
     * reading the graph in the tab beside it, and offering it while somebody is working
     * in Mongo is an invitation to open the wrong thing.
     */
    public void setFrontTab(Database database) {
        frontTab = database;
        showWhatIsAvailable();
    }

    /** The button for a service's web interface, which the tests reach for. */
    JButton buttonFor(Database database) {
        return buttons.entrySet().stream()
                .filter(entry -> entry.getKey().database() == database)
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private void showWhatIsAvailable() {
        component.removeAll();

        for (var entry : buttons.entrySet()) {
            if (!isUsable(entry.getKey().database())) {
                continue;
            }
            component.add(entry.getValue());
            JComponent note = notes.get(entry.getKey());
            if (note != null) {
                component.add(note);
            }
        }

        // Nothing to offer, so nothing to look at. The panel takes no room at all rather
        // than standing empty under its own heading.
        component.setVisible(component.getComponentCount() > 0);
        component.revalidate();
        component.repaint();
    }

    /**
     * Whether this belongs to the tab in front of it, which is the whole of the question.
     *
     * <p>
     * Not whether the service is running. What is offered is a standing fact about a
     * database -- Neo4j has a browser, Mongo can be opened in Studio 3T -- and it is
     * worth knowing before starting anything, not only afterwards. A box that came and
     * went as a service settled would be one more thing moving on screen.
     */
    private boolean isUsable(Database database) {
        return database == frontTab;
    }
}
