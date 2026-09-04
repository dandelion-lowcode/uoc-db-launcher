package com.uoc.ui;

import com.uoc.docker.Database;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;
import com.uoc.ui.menu.ConsoleFontManager;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import com.uoc.ansi.AnsiEditorKit;
import com.uoc.ansi.AnsiEscCode;
import com.uoc.ansi.DarkThemeAnsiColors;
import com.uoc.ansi.IAnsiColors;
import com.uoc.ansi.LightThemeAnsiColors;

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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class DatabaseTab {

    private static final int ACTION_ICON_SIZE = 16;

    // The size the console is read at before any zooming. Everything the student reads
    // and types is drawn at this size scaled by the zoom, so the menus and the console
    // grow together instead of the console staying small while the rest gets bigger.
    private static final int CONSOLE_FONT_SIZE = 12;
    private static final int INPUT_ROWS = 4;

    // Names for the parts a test drives. Finding a component by name rather than by its
    // position or its label keeps the tests working when the layout or the wording moves.
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

    // Two separate things decide whether a query can be sent: whether the service is up,
    // which Docker reports every few seconds, and whether the last query has been
    // answered, which only this class knows. Without keeping them apart, a status poll
    // reopens the console on top of a query that is still on its way.
    // The console is read in whatever monospaced font was chosen; the runtime's own is
    // the one every system is guaranteed to have.
    private String fontFamily = ConsoleFontManager.SYSTEM_MONOSPACED;
    private boolean serviceReady;
    private boolean awaitingAnswer;

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

        inputArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), SEND_ACTION);
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

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
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

    /** Redraws the console at the current zoom, the text already on screen included. */
    public void applyZoom() {
        if (scaledFontSize() != ansiEditorKit.getFontSize()) {
            redrawConsole();
        }
    }

    /** Redraws the console in another monospaced font, keeping the size and the colours. */
    public void applyFont(String family) {
        if (family == null || family.equals(fontFamily)) {
            return;
        }
        fontFamily = family;
        redrawConsole();
    }

    /**
     * Puts the current font and size on the console and on everything already printed in
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
        return FlatLaf.isLafDark() ? new DarkThemeAnsiColors() : new LightThemeAnsiColors();
    }

    public void applyThemeColors() {
        IAnsiColors oldColors = ansiEditorKit.getAnsiColors();
        IAnsiColors newColors = currentThemeColors();
        ansiEditorKit.setAnsiColors(newColors);
        remapExistingColors(oldColors, newColors);
    }

    private void remapExistingColors(IAnsiColors oldColors, IAnsiColors newColors) {
        Map<Color, Color> colorMap = new HashMap<>();
        colorMap.put(oldColors.black(), newColors.black());
        colorMap.put(oldColors.red(), newColors.red());
        colorMap.put(oldColors.green(), newColors.green());
        colorMap.put(oldColors.yellow(), newColors.yellow());
        colorMap.put(oldColors.blue(), newColors.blue());
        colorMap.put(oldColors.magenta(), newColors.magenta());
        colorMap.put(oldColors.cyan(), newColors.cyan());
        colorMap.put(oldColors.white(), newColors.white());
        colorMap.put(oldColors.brightBlack(), newColors.brightBlack());
        colorMap.put(oldColors.brightRed(), newColors.brightRed());
        colorMap.put(oldColors.brightGreen(), newColors.brightGreen());
        colorMap.put(oldColors.brightYellow(), newColors.brightYellow());
        colorMap.put(oldColors.brightBlue(), newColors.brightBlue());
        colorMap.put(oldColors.brightMagenta(), newColors.brightMagenta());
        colorMap.put(oldColors.brightCyan(), newColors.brightCyan());
        colorMap.put(oldColors.brightWhite(), newColors.brightWhite());

        StyledDocument doc = sessionPane.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        for (int p = 0; p < root.getElementCount(); p++) {
            Element paragraph = root.getElement(p);
            for (int c = 0; c < paragraph.getElementCount(); c++) {
                Element run = paragraph.getElement(c);
                AttributeSet attrs = run.getAttributes();

                Color newForeground = attrs.isDefined(StyleConstants.Foreground)
                        ? colorMap.get(StyleConstants.getForeground(attrs)) : null;
                Color newBackground = attrs.isDefined(StyleConstants.Background)
                        ? colorMap.get(StyleConstants.getBackground(attrs)) : null;

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

    // The action icons ship with a fixed light grey fill, so they are remapped to the
    // current theme's foreground instead of staying invisible on a light background.
    private static FlatSVGIcon themedIcon(String resource) {
        FlatSVGIcon icon = new FlatSVGIcon(resource, ACTION_ICON_SIZE, ACTION_ICON_SIZE);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> UIManager.getColor("Button.foreground")));
        return icon;
    }

    private void clearSession() {
        try {
            StyledDocument doc = sessionPane.getStyledDocument();
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void appendToSession(String text) {
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
     * Shows something the console did not ask for, such as why Docker refused to start the
     * service. Without this the only sign of a failure is a red dot on the side panel.
     */
    public void showFailure(String text) {
        appendToSession(AnsiEscCode.RED.escCode + text + AnsiEscCode.RESET.escCode + "\n\n");
    }

    public void registerTranslations(Translations translations) {
        translations.register(() -> {
            sessionLabel.setText(translations.get(database.consoleLabel()));
            sendButton.setText(translations.get(Message.BUTTON_SEND));
            clearButton.setText(translations.get(Message.BUTTON_CLEAR));
        });
    }
}
