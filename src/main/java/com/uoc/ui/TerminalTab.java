package com.uoc.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatArrowButton;
import com.formdev.flatlaf.util.UIScale;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermSearchComponent;
import com.jediterm.terminal.ui.JediTermSearchComponentListener;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import com.uoc.ansi.AnsiColor;
import com.uoc.ansi.IAnsiColors;
import com.uoc.ansi.ThemeAnsiColors;
import com.uoc.docker.Database;
import com.uoc.docker.DockerCommand;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service's own client, in a real terminal.
 *
 * <p>
 * This replaced a box to type one query into and a pane to print the answer in. That
 * arrangement ran a fresh process per query, which is not what any of these clients is: a
 * variable set in mongosh was gone by the next line, a client that asked a question got no
 * answer, and "\h" in vsql hung the launcher outright, because vsql pages its help through
 * "--More--" and nothing on this side could press a key.
 *
 * <p>
 * What a student gets now is the client the course talks about, with its own prompt, its
 * own history and its own completion, in a session that lasts. What we keep is the window
 * around it: the heading, the theme's colours, the font and the zoom.
 */
public class TerminalTab {

    /**
     * What the client is told it is running under, in the container rather than here.
     *
     * <p>
     * Left out, the process is started with TERM=dumb, and every client that can asks
     * readline to switch line editing off: no history, no arrow keys, and no colour. It
     * looked for a while as though the terminal was not passing the keys on.
     */
    private static final String TERM = "xterm-256color";

    /** Where the cursor starts before the widget has been laid out and can say. */
    private static final int INITIAL_COLUMNS = 80;
    private static final int INITIAL_ROWS = 24;

    /** The size the interface is drawn at, before the screen scales it. */
    private static final int CONSOLE_FONT_SIZE = 12;

    private final Database database;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final ConsoleLikeSettings settings;
    private final ThemedTerminal terminal;

    private PtyProcess process;

    /** Whether the service is up. Nothing is connected to a container that is not. */
    private boolean ready;

    /** Set when a session has ended and the next key should start another. */
    private boolean awaitingRestart;

    public TerminalTab(Database database, Translations translations) {
        this.database = database;
        this.settings = new ConsoleLikeSettings(translations);
        this.terminal = new ThemedTerminal(settings);

        JLabel heading = new JLabel();
        heading.setBorder(BorderFactory.createEmptyBorder(5, 0, 8, 0));
        translations.register(
                () -> heading.setText(translations.get(database.consoleLabel())));

        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(terminal, BorderLayout.CENTER);


        // Told when the emulator has finished with the stream, which is later than the
        // process ending; see sessionEnded.
        terminal.addListener(widget -> SwingUtilities.invokeLater(this::sessionEnded));

        // A key on a finished session starts another. Not on a timer and not the moment
        // it ends, because a student who typed "\q" meant it and should see it happen.
        terminal.onKeyPressed(() -> {
            if (awaitingRestart) {
                awaitingRestart = false;
                connect();
            }
        });

        panel.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                connectIfPossible();
            }
        });
    }

    public JPanel getPanel() {
        return panel;
    }

    /**
     * Told whether the service is up.
     *
     * <p>
     * A session is opened as soon as it is, so that a student who starts a database finds
     * a prompt waiting rather than a dead rectangle they have to poke.
     */
    public void setReady(boolean serviceIsUp) {
        ready = serviceIsUp;
        connectIfPossible();
    }

    /** What Docker said when it could not do what was asked of it. */
    public void showFailure(String details) {
        if (!isConnected()) {
            write(details);
        }
    }

    /**
     * How the image being fetched is getting on: a line per layer, counting up.
     *
     * <p>
     * Redrawn from the top of a cleared screen every time, which is what makes it a
     * display rather than a transcript. The text is the whole picture as it now stands,
     * not the news since last time, so appending it printed the entire block again on
     * every one of the several hundred updates a download produces.
     *
     * <p>
     * Written into the terminal only while nothing is running in it. Text arriving from
     * elsewhere in the middle of a live session lands wherever the cursor happens to be,
     * and a client redrawing its prompt makes a mess of it.
     */
    public void showInstallProgress(String text) {
        if (isConnected()) {
            return;
        }
        Terminal screen = terminal.getTerminal();
        screen.clearScreen();
        screen.cursorPosition(1, 1);
        writeLines(screen, text);
    }

    /**
     * Writes text that has line breaks in it.
     *
     * <p>
     * The breaks are asked for rather than written: writeCharacters puts characters into
     * the screen where the cursor is, and a line feed among them is a character like any
     * other rather than an instruction to move. Passed straight through, an eleven-line
     * block arrived as one line, and everything past the eightieth column of it was off
     * the edge of the screen.
     */
    private static void writeLines(Terminal screen, String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                screen.carriageReturn();
                screen.newLine();
            }
            screen.writeCharacters(lines[i]);
        }
    }

    /** Everything on the screen as it now stands, which is what a test can read. */
    String screenText() {
        return terminal.getTerminalTextBuffer().getScreenLines();
    }

    /**
     * The download is over, one way or the other.
     *
     * <p>
     * A download that worked is finished business, and what comes next is the client's
     * own prompt: it should open on a clear screen rather than under a list of layer
     * ids. The cursor goes back to the top with it, or the prompt appears wherever the
     * last line of the block happened to leave it, below a screenful of nothing.
     *
     * <p>
     * A download that failed keeps what it printed, that being the only account of why.
     */
    public void endInstallProgress(boolean succeeded) {
        if (succeeded) {
            Terminal screen = terminal.getTerminal();
            screen.clearScreen();
            screen.cursorPosition(1, 1);
        }
    }

    /** The font the rest of the interface is drawn in, which this follows. */
    public void applyFont(String family) {
        settings.family = family;
        terminal.refreshFont();
    }

    /** Redrawn at the size the interface has just been zoomed to. */
    public void applyZoom() {
        terminal.refreshFont();
    }

    /**
     * Repainted in the theme that has just been installed.
     *
     * <p>
     * The colours are read as the terminal paints rather than kept, so text already on
     * screen changes with it: a character remembers which of the sixteen colours it asked
     * for, not what that colour looked like at the time.
     */
    public void applyThemeColors() {
        terminal.refreshFont();
        terminal.repaint();
    }

    private boolean isConnected() {
        return process != null && process.isAlive();
    }

    private void connectIfPossible() {
        if (ready && panel.isShowing() && !isConnected() && !awaitingRestart) {
            connect();
        }
    }

    /**
     * Starts the client inside its container, with a pseudo terminal between it and here.
     *
     * <p>
     * The pseudo terminal is the point. "docker exec -t" alone gives the client a terminal
     * to write to, but nothing on this side listens as a terminal, so no key ever goes
     * back. Pty4J puts a real one in the middle, and what the client asks, the student can
     * answer.
     */
    private void connect() {
        try {
            Map<String, String> environment = new HashMap<>(System.getenv());
            environment.put("TERM", TERM);

            List<String> command = new ArrayList<>(List.of(
                    DockerCommand.EXECUTABLE, "exec", "-it", "-e", "TERM=" + TERM,
                    database.containerName()));
            command.addAll(clientFor(database));

            process = new PtyProcessBuilder()
                    .setCommand(command.toArray(new String[0]))
                    .setEnvironment(environment)
                    .setInitialColumns(INITIAL_COLUMNS)
                    .setInitialRows(INITIAL_ROWS)
                    .start();

            terminal.setTtyConnector(new ClientConnector(process, database.displayName()));
            terminal.start();
            awaitingRestart = false;

            SwingUtilities.invokeLater(terminal::requestFocusInWindow);
        } catch (IOException e) {
            // Almost always the service not being up. Said out loud, because the
            // alternative is a rectangle that explains nothing.
            write(e.getMessage());
        }
    }

    /**
     * The client each service is driven with, and the one place that is written down.
     *
     * <p>
     * Riak and Elasticsearch have no shell of their own: the course drives both with curl
     * over HTTP, so what opens is a shell, and a student types the request the notes give
     * them. Pretending otherwise would mean inventing a console neither product has.
     */
    static List<String> clientFor(Database database) {
        return switch (database) {
            case MONGO -> List.of("mongosh");
            // Without --color it decides by asking whether the output is a file.
            case CASSANDRA -> List.of("cqlsh", "--color");
            case NEO4J, NEO4J_TWITTER -> List.of("cypher-shell");
            case REDIS -> List.of("redis-cli");
            case RIAK, ELASTICSEARCH -> List.of("sh");
            case COCKROACHDB ->
                    List.of("cockroach", "sql", "--insecure", "--host=localhost:26257");
            case VERTICA -> List.of("/opt/vertica/bin/vsql",
                    "-h", "localhost", "-U", "dbadmin", "-d", "VMart");
            case ARANGODB -> List.of("arangosh",
                    "--server.endpoint", "tcp://127.0.0.1:8529",
                    "--server.username", "root",
                    "--server.password", "rootpassword",
                    // Where the IMDB graph is; the module tells the student to pick it.
                    "--server.database", "IMDB");
            default -> throw new IllegalArgumentException("No console for " + database);
        };
    }

    /**
     * Says the session is over, rather than leaving a screen that looks frozen.
     *
     * <p>
     * Said when JediTerm reports the session closed rather than when the process ends.
     * Those are not the same moment: the process is gone while its last bytes are still
     * being read and drawn, so a message written then lands in the middle of the client's
     * own goodbye.
     */
    private void sessionEnded() {
        awaitingRestart = true;
        write("[session ended -- press any key to start another]");
    }

    private void write(String line) {
        Terminal screen = terminal.getTerminal();
        screen.carriageReturn();
        screen.newLine();
        writeLines(screen, line);
        screen.carriageReturn();
        screen.newLine();
    }

    /**
     * What JediTerm asks about how to draw. It asks again every time the font is
     * reinitialised, which is what carries a change of font, zoom or theme to a terminal
     * already on screen.
     */
    static final class ConsoleLikeSettings extends DefaultSettingsProvider {

        private final IAnsiColors colours = new ThemeAnsiColors();
        private final Translations translations;

        private volatile String family = Font.MONOSPACED;

        ConsoleLikeSettings(Translations translations) {
            this.translations = translations;
        }

        // The menu a right click opens, in the language the student picked. JediTerm
        // names these in English and there is no way to hand it a bundle: what there is
        // is one method per item, so each is answered with our own wording and the keys
        // JediTerm already chose. Read as the menu is built, so switching language while
        // it is closed is enough.

        @Override
        public TerminalActionPresentation getCopyActionPresentation() {
            return renamed(Message.TERMINAL_COPY, super.getCopyActionPresentation());
        }

        @Override
        public TerminalActionPresentation getPasteActionPresentation() {
            return renamed(Message.TERMINAL_PASTE, super.getPasteActionPresentation());
        }

        @Override
        public TerminalActionPresentation getFindActionPresentation() {
            return renamed(Message.TERMINAL_FIND, super.getFindActionPresentation());
        }

        @Override
        public TerminalActionPresentation getSelectAllActionPresentation() {
            return renamed(Message.TERMINAL_SELECT_ALL, super.getSelectAllActionPresentation());
        }

        @Override
        public TerminalActionPresentation getClearBufferActionPresentation() {
            return renamed(Message.TERMINAL_CLEAR_BUFFER, super.getClearBufferActionPresentation());
        }

        @Override
        public TerminalActionPresentation getOpenUrlActionPresentation() {
            return renamed(Message.TERMINAL_OPEN_URL, super.getOpenUrlActionPresentation());
        }

        @Override
        public TerminalActionPresentation getPageUpActionPresentation() {
            return renamed(Message.TERMINAL_PAGE_UP, super.getPageUpActionPresentation());
        }

        @Override
        public TerminalActionPresentation getPageDownActionPresentation() {
            return renamed(Message.TERMINAL_PAGE_DOWN, super.getPageDownActionPresentation());
        }

        @Override
        public TerminalActionPresentation getLineUpActionPresentation() {
            return renamed(Message.TERMINAL_LINE_UP, super.getLineUpActionPresentation());
        }

        @Override
        public TerminalActionPresentation getLineDownActionPresentation() {
            return renamed(Message.TERMINAL_LINE_DOWN, super.getLineDownActionPresentation());
        }

        private TerminalActionPresentation renamed(Message name, TerminalActionPresentation given) {
            return new TerminalActionPresentation(translations.get(name), given.getKeyStrokes());
        }

        @Override
        public Font getTerminalFont() {
            return new Font(family, Font.PLAIN, (int) getTerminalFontSize());
        }

        @Override
        public float getTerminalFontSize() {
            // Read afresh rather than remembered: zooming changes what this comes to.
            return UIScale.scale(CONSOLE_FONT_SIZE);
        }

        /**
         * The paper and the ink, asked for as JediTerm paints rather than fixed here.
         * TerminalColor takes a supplier, which is what lets a change of theme reach a
         * terminal that is already on screen.
         */
        @Override
        public TextStyle getDefaultStyle() {
            return new TextStyle(
                    new TerminalColor(() -> asJediTerm(UIManager.getColor("TextPane.foreground"))),
                    new TerminalColor(() -> asJediTerm(UIManager.getColor("TextPane.background"))));
        }

        /**
         * What Ctrl+F paints a match in: the default colours the other way round, with
         * the theme's own yellow standing in for the ink.
         *
         * <p>
         * JediTerm's is black on pure #FFFF00, written into the library and the same
         * under either theme. Our yellow was picked to read against the background it is
         * printed on, so using it as the paper and the background as the ink is a pairing
         * already measured. Suppliers again, so a change of theme reaches a match that is
         * already highlighted.
         *
         * <p>
         * The selection needs nothing: useInverseSelectionColor is on, so JediTerm swaps
         * whatever colours the text already has, and those are ours.
         */
        @Override
        public TextStyle getFoundPatternColor() {
            return new TextStyle(
                    new TerminalColor(() -> asJediTerm(UIManager.getColor("TextPane.background"))),
                    new TerminalColor(() -> asJediTerm(colours.of(AnsiColor.YELLOW))));
        }

        /**
         * The sixteen colours a client asks for by number, answered from the theme.
         *
         * <p>
         * They are the ones defined in themes/FlatLightLaf.properties and its dark twin,
         * measured for contrast against the background each is painted on. JediTerm's own
         * palette is a second set of colours nobody here chose.
         */
        @Override
        public ColorPalette getTerminalColorPalette() {
            return new ColorPalette() {

                @Override
                protected com.jediterm.core.Color getForegroundByColorIndex(int index) {
                    return themeColour(index);
                }

                @Override
                protected com.jediterm.core.Color getBackgroundByColorIndex(int index) {
                    return themeColour(index);
                }
            };
        }

        /**
         * The first sixteen of our own colours are the ANSI sixteen, declared in that
         * order, so the index a client sends is the constant to look up.
         */
        private com.jediterm.core.Color themeColour(int index) {
            return asJediTerm(colours.of(AnsiColor.values()[Math.floorMod(index, 16)]));
        }

        private static com.jediterm.core.Color asJediTerm(Color colour) {
            return colour == null
                    ? new com.jediterm.core.Color(0, 0, 0)
                    : new com.jediterm.core.Color(
                            colour.getRed(), colour.getGreen(), colour.getBlue());
        }
    }

    /** The widget, wearing what the rest of the application wears. */
    private static final class ThemedTerminal extends JediTermWidget {

        private final ConsoleLikeSettings settings;

        private ThemedTerminal(ConsoleLikeSettings settings) {
            super(settings);
            this.settings = settings;
        }

        /**
         * A plain scroll bar, so the look and feel styles it. JediTerm's own is painted to
         * show where the matches of a search are, and there is no search here.
         */
        @Override
        protected JScrollBar createScrollBar() {
            return new JScrollBar();
        }

        /** Ours rather than JediTerm's, which is English and painted in the Basic style. */
        @Override
        protected JediTermSearchComponent createSearchComponent() {
            return new FindBar(settings.translations);
        }

        @Override
        protected TerminalPanel createTerminalPanel(SettingsProvider settings,
                StyleState styleState, TerminalTextBuffer buffer) {
            return new HostTerminalPanel(settings, buffer, styleState);
        }

        /**
         * Reads the font again and lays the screen out for it. JediTerm works this out
         * once, when the panel is built, so without this a change of font, zoom or theme
         * shows up only in a terminal opened afterwards.
         */
        private void refreshFont() {
            if (myTerminalPanel instanceof HostTerminalPanel host) {
                host.refreshFont();
            }
        }

        /** Any key, including the ones a live session would otherwise swallow. */
        private void onKeyPressed(Runnable action) {
            myTerminalPanel.addCustomKeyListener(new KeyAdapter() {

                @Override
                public void keyPressed(KeyEvent event) {
                    action.run();
                }
            });
        }
    }

    /**
     * The panel, taught the two things it has to know about the window it sits in.
     *
     * <p>
     * Both come from the same habit in JediTerm: it overrides a method and does not call
     * the one it overrode, so what Swing would have done never happens.
     */
    private static final class HostTerminalPanel extends TerminalPanel {

        private HostTerminalPanel(SettingsProvider settings, TerminalTextBuffer buffer,
                StyleState styleState) {
            super(settings, buffer, styleState);
        }

        private void refreshFont() {
            reinitFontAndResize();
        }

        /**
         * Lets the window's own shortcuts through before the terminal eats them.
         *
         * <p>
         * JediTerm overrides processKeyEvent and never calls super, and super is where
         * Swing matches a key against the menu bar's accelerators. Every shortcut the
         * application has stopped working while a terminal had the focus, zoom being the
         * one a student notices. Accelerators are looked up rather than named here, so a
         * menu that gains one later keeps working.
         */
        @Override
        public void processKeyEvent(KeyEvent event) {
            if (event.getID() == KeyEvent.KEY_PRESSED && aMenuTookIt(event)) {
                event.consume();
                return;
            }
            super.processKeyEvent(event);
        }

        private boolean aMenuTookIt(KeyEvent event) {
            JRootPane root = SwingUtilities.getRootPane(this);
            if (root == null || root.getJMenuBar() == null) {
                return false;
            }
            KeyStroke pressed = KeyStroke.getKeyStrokeForEvent(event);
            for (MenuElement menu : root.getJMenuBar().getSubElements()) {
                JMenuItem item = itemFor(pressed, menu);
                if (item != null) {
                    item.doClick();
                    return true;
                }
            }
            return false;
        }

        /** The item under this menu, however deep, that answers to the key. */
        private JMenuItem itemFor(KeyStroke pressed, MenuElement element) {
            if (element instanceof JMenuItem item
                    && pressed.equals(item.getAccelerator())
                    && item.isEnabled()) {
                return item;
            }
            for (MenuElement child : element.getSubElements()) {
                JMenuItem found = itemFor(pressed, child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        /**
         * Draws text the way the rest of the window draws it.
         *
         * <p>
         * JediTerm replaces the rendering hints with one of its own, plain grey
         * antialiasing. Windows and most Linux desktops ask for subpixel smoothing
         * instead, which is what every other character in this application is drawn with,
         * so the terminal came out thinner and grubbier than the panel beside it.
         */
        @Override
        protected void setupAntialiasing(Graphics graphics) {
            super.setupAntialiasing(graphics);

            Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
            if (graphics instanceof Graphics2D g && hints instanceof Map<?, ?> desktop) {
                g.addRenderingHints(desktop);
            }
        }
    }

    /**
     * The bar Ctrl+F opens, built out of ordinary Swing widgets.
     *
     * <p>
     * JediTerm's own is nearly right: its text field and its checkbox are painted by
     * whatever look and feel is installed. Its two arrows are not. They are
     * BasicArrowButton, which draws its own triangle and its own border in the Basic
     * style whatever the look and feel says, so they sat next to FlatLaf's widgets
     * looking like something from another decade.
     */
    static final class FindBar implements JediTermSearchComponent {

        /** How much of a query is worth seeing at once, in characters. */
        private static final int QUERY_WIDTH = 20;

        private final JTextField text = new JTextField(QUERY_WIDTH);
        private final JCheckBox ignoreCase = new JCheckBox();
        private final ThemedArrow previous = new ThemedArrow(SwingConstants.NORTH);
        private final ThemedArrow next = new ThemedArrow(SwingConstants.SOUTH);
        private final JLabel found = new JLabel();

        private final List<JediTermSearchComponentListener> listeners = new ArrayList<>();

        /**
         * The bar itself. JediTerm puts this straight into a layered pane over the
         * terminal and then asks it for the focus, meaning the field inside it.
         */
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2)) {

            @Override
            public void requestFocus() {
                text.requestFocus();
            }
        };

        FindBar(Translations translations) {
            translations.register(() -> {
                ignoreCase.setText(translations.get(Message.TERMINAL_IGNORE_CASE));
                previous.setToolTipText(translations.get(Message.TERMINAL_FIND_PREVIOUS));
                next.setToolTipText(translations.get(Message.TERMINAL_FIND_NEXT));
            });
            ignoreCase.setSelected(true);

            text.getDocument().addDocumentListener(new DocumentListener() {

                @Override
                public void insertUpdate(DocumentEvent event) {
                    searchChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    searchChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    searchChanged();
                }
            });
            ignoreCase.addActionListener(event -> searchChanged());
            next.addActionListener(event -> listeners.forEach(
                    JediTermSearchComponentListener::selectNextFindResult));
            previous.addActionListener(event -> listeners.forEach(
                    JediTermSearchComponentListener::selectPrevFindResult));

            // Opaque, or the terminal shows through the bar sitting on top of it.
            panel.setOpaque(true);
            panel.add(text);
            panel.add(ignoreCase);
            panel.add(previous);
            panel.add(next);
            panel.add(found);
        }

        private void searchChanged() {
            listeners.forEach(listener -> listener.searchSettingsChanged(
                    text.getText(), ignoreCase.isSelected()));
        }

        @Override
        public JComponent getComponent() {
            return panel;
        }

        @Override
        public void addListener(JediTermSearchComponentListener listener) {
            listeners.add(listener);
        }

        /** JediTerm listens for Escape and Enter, and it is the field they arrive at. */
        @Override
        public void addKeyListener(KeyListener listener) {
            text.addKeyListener(listener);
        }

        /**
         * Which match of how many, and the field outlined in red when a query matches
         * nothing, which is how FlatLaf says so everywhere else in this application.
         */
        @Override
        public void onResultUpdated(SubstringFinder.FindResult result) {
            boolean nothingFound = result != null && result.getItems().isEmpty();

            found.setText(result == null || result.getItems().isEmpty()
                    ? ""
                    : result.selectedItem().getIndex() + "/" + result.getItems().size());
            text.putClientProperty(FlatClientProperties.OUTLINE,
                    nothingFound && !text.getText().isEmpty()
                            ? FlatClientProperties.OUTLINE_ERROR
                            : null);
        }
    }

    /**
     * FlatLaf's own arrow, kept in step with the theme.
     *
     * <p>
     * This is what the find bar was missing. JediTerm builds its two arrows out of
     * BasicArrowButton, which paints its triangle and its border in the Basic style
     * whatever look and feel is installed, so they sat among FlatLaf's widgets looking
     * like something from another decade.
     *
     * <p>
     * FlatArrowButton is handed its colours once, when it is built, and the bar outlives
     * a change of theme. They are read again whenever the look and feel is reinstalled,
     * which is the moment those colours have new values.
     */
    private static final class ThemedArrow extends FlatArrowButton {

        private ThemedArrow(int direction) {
            super(direction, UIManager.getString("Component.arrowType"),
                    null, null, null, null, null, null);
            takeThemeColours();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            takeThemeColours();
        }

        private void takeThemeColours() {
            updateStyle(UIManager.getString("Component.arrowType"),
                    UIManager.getColor("Button.foreground"),
                    UIManager.getColor("Button.disabledText"),
                    UIManager.getColor("Button.foreground"),
                    UIManager.getColor("Button.toolbar.hoverBackground"),
                    UIManager.getColor("Button.foreground"),
                    UIManager.getColor("Button.toolbar.pressedBackground"));
        }
    }

    /** Ties JediTerm's idea of a terminal to the pseudo terminal Pty4J opened. */
    private static final class ClientConnector extends ProcessTtyConnector {

        private final PtyProcess process;
        private final String name;

        private ClientConnector(PtyProcess process, String name) {
            super(process, StandardCharsets.UTF_8);
            this.process = process;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        /**
         * Told when the window changes shape. A client that does not know how wide the
         * screen is wraps its own output in the wrong place, and pages it by the wrong
         * number of lines.
         */
        @Override
        public void resize(TermSize size) {
            if (process.isAlive()) {
                process.setWinSize(new WinSize(size.getColumns(), size.getRows()));
            }
        }
    }
}
