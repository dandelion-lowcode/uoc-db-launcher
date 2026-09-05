package com.uoc.ui;

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.util.UIScale;
import com.uoc.ansi.AnsiColor;
import com.uoc.ansi.AnsiEditorKit;
import com.uoc.ansi.IAnsiColors;
import com.uoc.ansi.ThemeAnsiColors;
import com.uoc.ui.menu.ConsoleFontManager;

/**
 * The box a console prints into: the text on screen, and the font and the colours it is
 * read in.
 *
 * <p>
 * It knows nothing about databases, queries or installs. It holds text, it knows whether
 * what it holds is a placeholder standing in for output that has not arrived, and it can
 * redraw everything already printed when the zoom, the font or the theme changes.
 * Deciding what to put in it, and when, belongs to {@link DatabaseTab}.
 */
final class ConsoleTrace {

    /**
     * The name a test finds this by. Finding a component by name rather than by its
     * position or its label keeps the tests working when the layout or the wording moves.
     */
    static final String SESSION = "session";

    // The size the console is read at before any zooming. Everything the student reads
    // and types is drawn at this size scaled by the zoom, so the menus and the console
    // grow together instead of the console staying small while the rest gets bigger.
    private static final int CONSOLE_FONT_SIZE = 12;

    private final JTextPane pane;
    private final JScrollPane scroll;
    private final AnsiEditorKit ansiEditorKit;

    // The console is read in whatever monospaced font was chosen; the runtime's own is
    // the one every system is guaranteed to have.
    private String fontFamily = ConsoleFontManager.SYSTEM_MONOSPACED;

    // The worked example standing in for an empty trace. It is content like any other as
    // far as the document is concerned, so it has to be remembered to be taken away again
    // the moment there is something real to show.
    private boolean showingPlaceholder;

    ConsoleTrace() {
        ansiEditorKit = new AnsiEditorKit(scaledFontSize(), currentThemeColors());
        ansiEditorKit.setFontFamily(fontFamily);

        pane = new JTextPane();
        pane.setName(SESSION);
        pane.setEditorKit(ansiEditorKit);
        pane.setFont(font());
        pane.setEditable(false);
        scroll = new JScrollPane(pane);
    }

    JComponent getComponent() {
        return scroll;
    }

    /** The font the trace is drawn in, which whatever sits beside it has to match. */
    Font font() {
        return new Font(fontFamily, Font.PLAIN, scaledFontSize());
    }

    private static int scaledFontSize() {
        return UIScale.scale(CONSOLE_FONT_SIZE);
    }

    /** Prints output, taking a placeholder away rather than printing underneath it. */
    void append(String text) {
        // Real output replaces the example rather than following it, so a student's first
        // answer does not arrive underneath a query they never ran.
        if (showingPlaceholder) {
            clear();
        }
        insertAnsi(text);
    }

    /**
     * Puts text on screen that the first real output will wipe, in place of whatever was
     * there before.
     */
    void showPlaceholder(String text) {
        clear();
        showingPlaceholder = true;
        insertAnsi(text);
    }

    void clear() {
        showingPlaceholder = false;
        try {
            StyledDocument doc = pane.getStyledDocument();
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    boolean isEmpty() {
        return pane.getDocument().getLength() == 0;
    }

    boolean isShowingPlaceholder() {
        return showingPlaceholder;
    }

    private void insertAnsi(String text) {
        try {
            StyledDocument doc = pane.getStyledDocument();
            ansiEditorKit.insertAnsi(doc, text, doc.getLength());
            pane.setCaretPosition(doc.getLength());
            SwingUtilities.invokeLater(() -> {
                try {
                    pane.scrollRectToVisible(pane.modelToView2D(doc.getLength()).getBounds());
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Redraws the trace at the current zoom, the text already on screen included. */
    void applyZoom() {
        if (scaledFontSize() != ansiEditorKit.getFontSize()) {
            redraw();
        }
    }

    /** Redraws the trace in another monospaced font, keeping the size and the colours. */
    void applyFont(String family) {
        if (family == null || family.equals(fontFamily)) {
            return;
        }
        fontFamily = family;
        redraw();
    }

    /**
     * Puts the current font and size on the trace and on everything already printed in
     * it, so a session never ends up written half one way and half another.
     */
    private void redraw() {
        int size = scaledFontSize();
        ansiEditorKit.setFontSize(size);
        ansiEditorKit.setFontFamily(fontFamily);

        pane.setFont(font());

        SimpleAttributeSet restyled = new SimpleAttributeSet();
        StyleConstants.setFontSize(restyled, size);
        StyleConstants.setFontFamily(restyled, fontFamily);
        StyledDocument doc = pane.getStyledDocument();
        doc.setCharacterAttributes(0, doc.getLength(), restyled, false);
    }

    private static IAnsiColors currentThemeColors() {
        return new ThemeAnsiColors();
    }

    void applyThemeColors() {
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

        StyledDocument doc = pane.getStyledDocument();
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
}
