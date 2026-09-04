package com.uoc.ansi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Pattern;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;

/**
 * The AnsiEditorKit is a specialized {@link StyledEditorKit} that is able to
 * created {@link StyledDocument}s based on
 * text containing ANSI escape codes for styling the text.
 * The documents are created with the Monospaced font to simulate an
 * old-fashioned text console for displaying ANSI
 * graphics.
 */
public class AnsiEditorKit extends StyledEditorKit {

    private int fontSize;
    private String fontFamily = "Monospaced";
    private IAnsiColors ansiColors;

    private final Pattern ansiEscCodePattern = Pattern.compile("\u001b\\[[0-9;]*m");

    // The palette is always required. The original library defaulted to one built for a
    // black terminal, which would be unreadable on the light theme and, unlike the two
    // palettes here, has never been checked for contrast.

    /**
     * Creates a AnsiEditorKit using a monospaced font size of 14, and the ANSI
     * colors set as input parameter.
     *
     * @param ansiColors is the {@link IAnsiColors} to use for the ANSI Colors.
     *                   Default is the the light or dark palette.
     */
    public AnsiEditorKit(IAnsiColors ansiColors) {
        this(14, ansiColors);
    }

    /**
     * Creates a AnsiEditorKit using a monospaced font size set as input parameter,
     * and the ANSI colors set as input parameter.
     *
     * @param fontSize   is the monospaced font size to use across an entire
     *                   document. Default is 14.
     * @param ansiColors is the {@link IAnsiColors} to use for the ANSI Colors.
     *                   Default is the the light or dark palette.
     */
    public AnsiEditorKit(int fontSize, IAnsiColors ansiColors) {
        this.fontSize = fontSize;
        this.ansiColors = ansiColors;
    }

    /** The size new text is written at, which changes when the student zooms. */
    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public int getFontSize() {
        return fontSize;
    }

    /** The monospaced family new text is written in, which the student can change. */
    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setAnsiColors(IAnsiColors ansiColors) {
        this.ansiColors = ansiColors;
    }

    public IAnsiColors getAnsiColors() {
        return ansiColors;
    }

    @Override
    public String getContentType() {
        return "text/x-ansi";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void read(InputStream inputStream, Document doc, int pos) throws IOException, BadLocationException {
        read(new BufferedReader(new InputStreamReader(inputStream)), doc, pos);
    }

    /**
     * {@inheritDoc}
     */
    public void read(Reader reader, Document doc, int pos) throws IOException, BadLocationException {
        if (!(doc instanceof StyledDocument))
            throw new IllegalArgumentException("The document must be a StyledDocument for this kit");

        String text = readText(reader);
        insertAnsi((StyledDocument) doc, text, pos);
    }

    /**
     * {@inheritDoc}
     */
    public void write(OutputStream outputStream, Document doc, int pos, int len)
            throws IOException, BadLocationException {
        write(new BufferedWriter(new OutputStreamWriter(outputStream)), doc, pos, len);
    }

    /**
     * {@inheritDoc}
     */
    public void write(Writer writer, Document doc, int pos, int len) throws BadLocationException, IOException {
        writer.write(doc.getText(pos, len));
    }

    /**
     * Inserts an ANSI text into the tail of the document.
     * ANSI escape codes are converted into {@link AttributeSet}s to style the
     * inserted text.
     *
     * @param doc      is a {@link StyledDocument} the ANSI text is inserted into.
     * @param ansiText is the ANSI text to insert into the document.
     */
    public void insertAnsi(StyledDocument doc, String ansiText) throws BadLocationException {
        insertAnsi(doc, ansiText, doc.getLength());
    }

    /**
     * Inserts an ANSI text into a specific text position of the document.
     * ANSI escape codes are converted into {@link AttributeSet}s to style the
     * inserted text.
     *
     * @param doc      is a {@link StyledDocument} the ANSI text is inserted into.
     * @param ansiText is the ANSI text to insert into the document.
     * @param offset   is the offset into the document where the text will be
     *                 inserted.
     */
    public void insertAnsi(StyledDocument doc, String ansiText, int offset) throws BadLocationException {
        if (offset < 0)
            throw new IllegalArgumentException("Offset cannot be negative. Was: " + offset);

        MutableAttributeSet attributes = new SimpleAttributeSet(doc.getCharacterElement(offset).getAttributes());
        StyleConstants.setFontFamily(attributes, fontFamily);
        StyleConstants.setFontSize(attributes, fontSize);

        var matcher = ansiEscCodePattern.matcher(ansiText);
        int insertPos = offset;
        int textStart = 0;

        while (matcher.find()) {
            String plainText = ansiText.substring(textStart, matcher.start());
            if (!plainText.isEmpty()) {
                doc.insertString(insertPos, plainText, attributes);
                insertPos += plainText.length();
            }
            attributes = applyEscCode(attributes, ansiText.substring(matcher.start(), matcher.end()));
            textStart = matcher.end();
        }

        String remaining = ansiText.substring(textStart);
        if (!remaining.isEmpty()) {
            doc.insertString(insertPos, remaining, attributes);
        }
    }

    private MutableAttributeSet applyEscCode(MutableAttributeSet attributes, String escCode) {
        String esc = escCode.substring(0, 1);
        String params = escCode.substring(2, escCode.length() - 1);
        if (params.isEmpty()) {
            params = "0";
        }

        MutableAttributeSet result = attributes;
        for (String param : params.split(";")) {
            String single = param.isEmpty() ? "0" : param;
            AnsiEscCode known = AnsiEscCode.fromEscCodeOrNull(esc + "[" + single + "m");
            if (known != null) {
                result = AnsiAttributesUtil.updateAnsi(result, known, ansiColors);
            }
        }
        return result;
    }

    private static String readText(Reader reader) throws IOException {
        try (reader) {
            char[] arr = new char[8 * 1024];
            var buffer = new StringBuilder();
            int numCharsRead;
            while ((numCharsRead = reader.read(arr, 0, arr.length)) != -1) {
                buffer.append(arr, 0, numCharsRead);
            }
            return buffer.toString();
        }
    }
}
