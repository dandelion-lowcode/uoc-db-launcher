package com.uoc.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.uoc.ansi.AnsiEscCode;
import com.uoc.docker.Database;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

/**
 * One database's console: the trace it prints into, the box a query is typed in, and the
 * decisions about which of the two the student is looking at.
 *
 * <p>
 * What the text looks like on screen is not decided here. {@link ConsoleTrace} holds it
 * and draws it; this class chooses what goes into it and when -- a worked example while
 * there is nothing else to show, an install's progress while an image is being fetched,
 * and the queries and their answers once there is a database to ask.
 */
public class DatabaseTab {

    private static final int ACTION_ICON_SIZE = 16;
    private static final int INPUT_ROWS = 4;

    // Names for the parts a test drives. Finding a component by name rather than by
    // its
    // position or its label keeps the tests working when the layout or the wording
    // moves.
    static final String INPUT = "input";
    static final String SEND = "send";
    static final String CLEAR = "clear";
    private static final String SEND_ICON = "icons/send.svg";
    private static final String CLEAR_ICON = "icons/eraser.svg";
    private static final String SEND_ACTION = "send";

    private final Database database;
    private final JPanel panel;
    private final JLabel sessionLabel;
    private final ConsoleTrace trace;
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
    private boolean serviceReady;
    private boolean awaitingAnswer;

    // Whether an image is being fetched, which decides both halves of the console: the
    // box for typing is away, and the worked example stays away with it rather than
    // appearing under a download in progress.
    private boolean installing;

    private Translations translations;

    public DatabaseTab(Database database, QueryRunner queryRunner) {
        this.database = database;
        trace = new ConsoleTrace();

        inputArea = new JTextArea(INPUT_ROWS, 0);
        inputArea.setName(INPUT);
        inputArea.setLineWrap(true);
        inputArea.setFont(trace.font());
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
            trace.append("> " + query + "\n");
            inputArea.setText("");
            awaitingAnswer = true;
            updateSendButton();
            queryRunner.run(database.key(), query, output -> {
                trace.append(output.endsWith("\n") ? output : output + "\n");
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
        sessionPanel.add(trace.getComponent(), BorderLayout.CENTER);

        panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(sessionPanel, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);
    }

    /**
     * Redraws the console at the current zoom, the text already on screen included.
     */
    public void applyZoom() {
        trace.applyZoom();
        matchTheTrace();
    }

    /**
     * Redraws the console in another monospaced font, keeping the size and the
     * colours.
     */
    public void applyFont(String family) {
        trace.applyFont(family);
        matchTheTrace();
    }

    /**
     * The box a query is typed in is read alongside the trace, so it is drawn the same
     * way. Left to decide for itself it kept whatever size it was built at while the
     * trace grew, and the two halves of the console stopped matching.
     */
    private void matchTheTrace() {
        inputArea.setFont(trace.font());
    }

    public void applyThemeColors() {
        trace.applyThemeColors();
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
        trace.clear();
        showPlaceholder();
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
        trace.showPlaceholder(ConsoleExamples.placeholderFor(database, translations));
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
        trace.clear();

        // A heading in the ordinary colour, then what Docker is saying in grey. The
        // heading is what the student is meant to read; the lines under it are there to
        // show that something is still happening, not to be followed word by word.
        if (translations != null) {
            trace.append(translations.format(Message.CONSOLE_INSTALLING, database.displayName())
                    + "\n\n");
        }
        trace.append(AnsiEscCode.BRIGHT_BLACK.escCode
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
        trace.append(AnsiEscCode.RED.escCode + text + AnsiEscCode.RESET.escCode + "\n\n");
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
            if (trace.isShowingPlaceholder() || trace.isEmpty()) {
                showPlaceholder();
            }
        });
    }
}
