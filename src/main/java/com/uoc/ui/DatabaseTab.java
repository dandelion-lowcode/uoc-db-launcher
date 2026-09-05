package com.uoc.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import com.uoc.ansi.AnsiColor;
import com.uoc.ansi.AnsiEditorKit;
import com.uoc.ansi.AnsiEscCode;
import com.uoc.ansi.IAnsiColors;
import com.uoc.ansi.ThemeAnsiColors;
import com.uoc.docker.Database;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;
import com.uoc.ui.menu.ConsoleFontManager;

public class DatabaseTab {

    private static final int ACTION_ICON_SIZE = 16;

    // The size the console is read at before any zooming. Everything the student
    // reads
    // and types is drawn at this size scaled by the zoom, so the menus and the
    // console
    // grow together instead of the console staying small while the rest gets
    // bigger.
    private static final int CONSOLE_FONT_SIZE = 12;
    private static final int INPUT_ROWS = 4;

    // Names for the parts a test drives. Finding a component by name rather than by
    // its
    // position or its label keeps the tests working when the layout or the wording
    // moves.
    static final String SESSION = "session";
    static final String INPUT = "input";
    static final String SEND = "send";
    static final String CLEAR = "clear";
    private static final String SEND_ICON = "icons/send.svg";
    private static final String CLEAR_ICON = "icons/eraser.svg";
    private static final String SEND_ACTION = "send";

    private final Database database;
    private final JPanel panel;
    private final JLabel sessionLabel;
    private final JTextPane sessionPane;
    private final AnsiEditorKit ansiEditorKit;
    private final JTextArea inputArea;
    private final JButton sendButton;
    private final JButton clearButton;
    // Taken away while an image is being fetched: there is nothing to send a query
    // to
    // yet, and the box invites typing one for minutes before anything can answer.
    private final JPanel inputPanel;

    // Two separate things decide whether a query can be sent: whether the service
    // is up,
    // which Docker reports every few seconds, and whether the last query has been
    // answered, which only this class knows. Without keeping them apart, a status
    // poll
    // reopens the console on top of a query that is still on its way.
    // The console is read in whatever monospaced font was chosen; the runtime's own
    // is
    // the one every system is guaranteed to have.
    private String fontFamily = ConsoleFontManager.SYSTEM_MONOSPACED;
    private boolean serviceReady;
    private boolean awaitingAnswer;
    private boolean installing;

    // The worked example standing in for an empty trace. It is content like any
    // other as
    // far as the document is concerned, so it has to be remembered to be taken away
    // again
    // the moment there is something real to show.
    private boolean showingPlaceholder;
    private Translations translations;

    public DatabaseTab(Database database, QueryRunner queryRunner) {
        this.database = database;
        ansiEditorKit = new AnsiEditorKit(scaledFontSize(), currentThemeColors());
        ansiEditorKit.setFontFamily(fontFamily);

        sessionPane = new JTextPane();
        sessionPane.setName(SESSION);
        sessionPane.setEditorKit(ansiEditorKit);
        sessionPane.setFont(consoleFont());
        sessionPane.setEditable(false);
        JScrollPane sessionScroll = new JScrollPane(sessionPane);

        inputArea = new JTextArea(INPUT_ROWS, 0);
        inputArea.setName(INPUT);
        inputArea.setLineWrap(true);
        inputArea.setFont(consoleFont());
        JScrollPane inputScroll = new JScrollPane(inputArea);

        sendButton = new JButton(themedIcon(SEND_ICON));
        sendButton.setName(SEND);
        sendButton.setEnabled(false);

        clearButton = new JButton(themedIcon(CLEAR_ICON));
        clearButton.setName(CLEAR);
        clearButton.addActionListener(e -> clearSession());

        Runnable sendAction = () -> {
            if (!sendButton.isEnabled()) {
                return;
            }
            String query = inputArea.getText().trim();
            if (query.isEmpty()) {
                return;
            }
            appendToSession("> " + query + "\n");
            inputArea.setText("");
            awaitingAnswer = true;
            updateSendButton();
            queryRunner.run(database.key(), query, output -> {
                appendToSession(output.endsWith("\n") ? output : output + "\n");
                awaitingAnswer = false;
                updateSendButton();
            });
        };
        sendButton.addActionListener(e -> sendAction.run());

        // Whichever key this system uses for its menu shortcuts, which is Command on a
        // Mac and Control everywhere else. Written as Control it was the one shortcut
        // in
        // the application that did not follow the platform: the zoom already asked the
        // toolkit and so answered to Command, while sending a query did not.
        inputArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                        Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                SEND_ACTION);
        inputArea.getActionMap().put(SEND_ACTION, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendAction.run();
            }
        });

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftButtons.add(clearButton);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightButtons.add(sendButton);

        JPanel buttonRow = new JPanel(new BorderLayout(5, 5));
        buttonRow.add(leftButtons, BorderLayout.WEST);
        buttonRow.add(rightButtons, BorderLayout.EAST);

        inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(buttonRow, BorderLayout.SOUTH);

        sessionLabel = new JLabel();
        sessionLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        JPanel sessionPanel = new JPanel(new BorderLayout(5, 5));
        sessionPanel.add(sessionLabel, BorderLayout.NORTH);
        sessionPanel.add(sessionScroll, BorderLayout.CENTER);

        panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(sessionPanel, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);
    }

    private static int scaledFontSize() {
        return UIScale.scale(CONSOLE_FONT_SIZE);
    }

    private Font consoleFont() {
        return new Font(fontFamily, Font.PLAIN, scaledFontSize());
    }

    /**
     * Redraws the console at the current zoom, the text already on screen included.
     */
    public void applyZoom() {
        if (scaledFontSize() != ansiEditorKit.getFontSize()) {
            redrawConsole();
        }
    }

    /**
     * Redraws the console in another monospaced font, keeping the size and the
     * colours.
     */
    public void applyFont(String family) {
        if (family == null || family.equals(fontFamily)) {
            return;
        }
        fontFamily = family;
        redrawConsole();
    }

    /**
     * Puts the current font and size on the console and on everything already
     * printed in
     * it, so a session never ends up written half one way and half another.
     */
    private void redrawConsole() {
        int size = scaledFontSize();
        ansiEditorKit.setFontSize(size);
        ansiEditorKit.setFontFamily(fontFamily);

        Font font = consoleFont();
        sessionPane.setFont(font);
        inputArea.setFont(font);

        SimpleAttributeSet restyled = new SimpleAttributeSet();
        StyleConstants.setFontSize(restyled, size);
        StyleConstants.setFontFamily(restyled, fontFamily);
        StyledDocument doc = sessionPane.getStyledDocument();
        doc.setCharacterAttributes(0, doc.getLength(), restyled, false);
    }

    private static IAnsiColors currentThemeColors() {
        return new ThemeAnsiColors();
    }

    public void applyThemeColors() {
        IAnsiColors oldColors = ansiEditorKit.getAnsiColors();
        IAnsiColors newColors = currentThemeColors();
        ansiEditorKit.setAnsiColors(newColors);
        remapExistingColors(oldColors, newColors);
    }

    private void remapExistingColors(IAnsiColors oldColors, IAnsiColors newColors) {
        // What was painted in the old theme's colours has to be repainted in the new
        // one's, colour for colour. Walking the palette says that once; naming each
        // colour said it seventeen times and would have said it eighteen the next time
        // one was added.
        Map<Color, Color> colorMap = new HashMap<>();
        for (AnsiColor colour : AnsiColor.values()) {
            colorMap.put(oldColors.of(colour), newColors.of(colour));
        }

        StyledDocument doc = sessionPane.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element paragraph = root.getElement(p);
            for (int c = 0; c < paragraph.getElementCount(); c++) {
                Element run = paragraph.getElement(c);
                AttributeSet attrs = run.getAttributes();

                Color newForeground = attrs.isDefined(StyleConstants.Foreground)
                        ? colorMap.get(StyleConstants.getForeground(attrs))
                        : null;
                Color newBackground = attrs.isDefined(StyleConstants.Background)
                        ? colorMap.get(StyleConstants.getBackground(attrs))
                        : null;

                if (newForeground == null && newBackground == null) {
                    continue;
                }

                SimpleAttributeSet change = new SimpleAttributeSet();
                if (newForeground != null) {
                    StyleConstants.setForeground(change, newForeground);
                }
                if (newBackground != null) {
                    StyleConstants.setBackground(change, newBackground);
                }
                doc.setCharacterAttributes(run.getStartOffset(), run.getEndOffset() - run.getStartOffset(),
                        change, false);
            }
        }
    }

    // The action icons ship with a fixed light grey fill, so they are remapped to
    // the
    // current theme's foreground instead of staying invisible on a light
    // background.
    private static FlatSVGIcon themedIcon(String resource) {
        FlatSVGIcon icon = new FlatSVGIcon(resource, ACTION_ICON_SIZE, ACTION_ICON_SIZE);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> UIManager.getColor("Button.foreground")));
        return icon;
    }

    private void clearSession() {
        emptySession();
        showPlaceholder();
    }

    private void emptySession() {
        try {
            StyledDocument doc = sessionPane.getStyledDocument();
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Fills an empty trace with something worth reading: what this console is for,
     * and a
     * query for this database with the answer it gives.
     *
     * <p>
     * An empty box says nothing about what to type into it, and the six databases
     * here
     * are each driven in a different language. A worked example is the shortest way
     * to
     * say which one this tab expects.
     */
    private void showPlaceholder() {
        if (translations == null || installing) {
            return;
        }
        showingPlaceholder = true;
        appendAnsi(ConsoleExamples.placeholderFor(database, translations));
    }

    private void appendToSession(String text) {
        // Real output replaces the example rather than following it, so a student's
        // first
        // answer does not arrive underneath a query they never ran.
        if (showingPlaceholder) {
            showingPlaceholder = false;
            emptySession();
        }
        appendAnsi(text);
    }

    private void appendAnsi(String text) {
        try {
            StyledDocument doc = sessionPane.getStyledDocument();
            ansiEditorKit.insertAnsi(doc, text, doc.getLength());
            sessionPane.setCaretPosition(doc.getLength());
            SwingUtilities.invokeLater(() -> {
                try {
                    sessionPane.scrollRectToVisible(sessionPane.modelToView2D(doc.getLength()).getBounds());
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Shows how an install is going, replacing whatever the trace was showing.
     *
     * <p>
     * Replacing rather than adding is the whole point: fetching an image is the
     * same
     * handful of lines counting up, and appending each report would scroll hundreds
     * of
     * near-identical lines past faster than anyone could read them. Rewritten in
     * place,
     * they read the way Docker's own display does.
     *
     * <p>
     * The box for typing queries goes away while this runs. There is nothing to ask
     * yet,
     * and leaving it there invites a student to type into it for the minutes a
     * download
     * takes.
     */
    public void showInstallProgress(String text) {
        if (!installing) {
            installing = true;
            inputPanel.setVisible(false);
            panel.revalidate();
        }
        emptySession();
        showingPlaceholder = false;

        // A heading in the ordinary colour, then what Docker is saying in grey. The
        // heading is what the student is meant to read; the lines under it are there to
        // show that something is still happening, not to be followed word by word.
        if (translations != null) {
            appendAnsi(translations.format(Message.CONSOLE_INSTALLING, database.displayName())
                    + "\n\n");
        }
        appendAnsi(AnsiEscCode.BRIGHT_BLACK.escCode
                + (text.endsWith("\n") ? text : text + "\n")
                + AnsiEscCode.RESET.escCode);
    }

    /**
     * The install is over, whether it worked or not, so the console comes back.
     *
     * <p>
     * What the install printed is left on screen: when it failed, that text is the
     * only
     * account of why.
     */
    public void endInstallProgress(boolean succeeded) {
        if (!installing) {
            return;
        }
        installing = false;
        inputPanel.setVisible(true);
        panel.revalidate();

        // An install that worked leaves nothing worth reading: a wall of layer
        // identifiers and the word "Pulled" is the tail of something already over, and it
        // sits where the student is about to work. The console goes back to what it shows
        // when it is empty, which is the one thing that helps at that moment: a query
        // they can try.
        //
        // A failed one keeps every line. That text is the only account of what went
        // wrong, and clearing it would leave a red dot and no explanation.
        if (succeeded) {
            emptySession();
            showingPlaceholder = false;
            showPlaceholder();
        }
    }

    public JPanel getPanel() {
        return panel;
    }

    public void focusInput() {
        inputArea.requestFocusInWindow();
    }

    /** Tells the console whether the service behind it is up and can be queried. */
    public void setSendEnabled(boolean enabled) {
        serviceReady = enabled;
        updateSendButton();
    }

    private void updateSendButton() {
        sendButton.setEnabled(serviceReady && !awaitingAnswer);
    }

    /**
     * Shows something the console did not ask for, such as why Docker refused to
     * start the
     * service. Without this the only sign of a failure is a red dot on the side
     * panel.
     */
    public void showFailure(String text) {
        appendToSession(AnsiEscCode.RED.escCode + text + AnsiEscCode.RESET.escCode + "\n\n");
    }

    /**
     * How the send shortcut is written out, in the words this system uses for it:
     * Ctrl on
     * Windows and Linux, and the Command symbol on a Mac.
     */
    private static String shortcutName() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        return java.awt.event.InputEvent.getModifiersExText(mask)
                + "+" + KeyEvent.getKeyText(KeyEvent.VK_ENTER);
    }

    public void registerTranslations(Translations translations) {
        this.translations = translations;
        translations.register(() -> {
            sessionLabel.setText(translations.get(database.consoleLabel()));
            sendButton.setText(translations.get(Message.BUTTON_SEND));
            // The shortcut is named from the key that is actually bound rather than
            // written into the wording. Spelled out in the translations it read
            // "Ctrl+Enter" on a Mac, where the binding is Command.
            sendButton.setToolTipText(
                    translations.get(Message.BUTTON_SEND) + " (" + shortcutName() + ")");
            clearButton.setText(translations.get(Message.BUTTON_CLEAR));
            // Written again in the new language. Only while it is the only thing there:
            // a session the student has worked in is theirs, and rewriting it would
            // throw away what they had done.
            if (showingPlaceholder || sessionPane.getDocument().getLength() == 0) {
                emptySession();
                showPlaceholder();
            }
        });
    }
}
