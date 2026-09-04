package com.uoc.ansi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The console renderer, exercised against a plain document rather than a
 * window, so the
 * checks run headless and fast.
 *
 * <p>
 * Every case here comes from something that once went wrong: escape codes
 * printed as
 * visible text, a colour bleeding into the whole session, a black box painted
 * behind
 * coloured runs, and a crash on the multi-parameter codes that mongosh emits.
 */
class AnsiEditorKitTest {

    private static final String ESC = String.valueOf((char) 27);

    private final IAnsiColors colors = new LightThemeAnsiColors();
    private final AnsiEditorKit kit = new AnsiEditorKit(colors);
    private final StyledDocument document = new DefaultStyledDocument();

    private String text() throws BadLocationException {
        return document.getText(0, document.getLength());
    }

    private AttributeSet attributesAt(int offset) {
        return document.getCharacterElement(offset).getAttributes();
    }

    @Test
    void plainTextArrivesUnchanged() throws BadLocationException {
        kit.insertAnsi(document, "hola mundo");

        assertThat(text()).isEqualTo("hola mundo");
    }

    @Test
    void escapeCodesNeverAppearAsText() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[32mverde" + ESC + "[0m normal");

        // The whole point: the codes steer the colours and are not themselves printed.
        assertThat(text()).isEqualTo("verde normal").doesNotContain(ESC).doesNotContain("[32m");
    }

    @Test
    void aColourAppliesToTheTextThatFollowsIt() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[31mrojo");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.red());
    }

    @Test
    void aResetPutsTheColourBack() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[31mrojo" + ESC + "[0mnormal");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.red());
        assertThat(StyleConstants.getForeground(attributesAt(4))).isNotEqualTo(colors.red());
    }

    @Test
    void colouredRunsGetNoBackgroundOfTheirOwn() throws BadLocationException {
        // A black box used to be painted behind every coloured run, because an unset
        // background reads back as white and was mistaken for a colour to remap.
        kit.insertAnsi(document, ESC + "[31mrojo" + ESC + "[0m");

        assertThat(attributesAt(0).isDefined(StyleConstants.Background))
                .as("a coloured run must not carry a background of its own")
                .isFalse();
    }

    @ParameterizedTest(name = "in the {2} theme, code {0} paints {1}")
    @CsvSource({
            "30, black, light", "31, red, light", "32, green, light", "33, yellow, light",
            "34, blue, light", "35, magenta, light", "36, cyan, light", "37, white, light",
            "90, brightBlack, light", "91, brightRed, light", "92, brightGreen, light",
            "93, brightYellow, light", "94, brightBlue, light", "95, brightMagenta, light",
            "96, brightCyan, light", "97, brightWhite, light",
            "30, black, dark", "31, red, dark", "32, green, dark", "33, yellow, dark",
            "34, blue, dark", "35, magenta, dark", "36, cyan, dark", "37, white, dark",
            "90, brightBlack, dark", "91, brightRed, dark", "92, brightGreen, dark",
            "93, brightYellow, dark", "94, brightBlue, dark", "95, brightMagenta, dark",
            "96, brightCyan, dark", "97, brightWhite, dark"
    })
    void everyColourCodePaintsTheColourItNames(String code, String colourName, String theme)
            throws BadLocationException {
        // Both palettes, because a colour that happens to match the default in one theme
        // would hide a code that painted nothing at all.
        IAnsiColors palette = theme.equals("dark")
                ? new DarkThemeAnsiColors() : new LightThemeAnsiColors();
        AnsiEditorKit themed = new AnsiEditorKit(palette);
        StyledDocument doc = new DefaultStyledDocument();

        themed.insertAnsi(doc, ESC + "[" + code + "mtexto");

        AttributeSet attributes = doc.getCharacterElement(0).getAttributes();
        assertThat(attributes.isDefined(StyleConstants.Foreground))
                .as("code %s painted no colour at all", code)
                .isTrue();
        assertThat(StyleConstants.getForeground(attributes))
                .isEqualTo(colourOf(palette, colourName));
    }

    private Color colourOf(IAnsiColors colors, String name) {
        return switch (name) {
            case "black" -> colors.black();
            case "red" -> colors.red();
            case "green" -> colors.green();
            case "yellow" -> colors.yellow();
            case "blue" -> colors.blue();
            case "magenta" -> colors.magenta();
            case "cyan" -> colors.cyan();
            case "white" -> colors.white();
            case "brightBlack" -> colors.brightBlack();
            case "brightRed" -> colors.brightRed();
            case "brightGreen" -> colors.brightGreen();
            case "brightYellow" -> colors.brightYellow();
            case "brightBlue" -> colors.brightBlue();
            case "brightMagenta" -> colors.brightMagenta();
            case "brightCyan" -> colors.brightCyan();
            case "brightWhite" -> colors.brightWhite();
            default -> throw new IllegalArgumentException(name);
        };
    }

    @Test
    void boldTextIsActuallyBold() throws BadLocationException {
        // cqlsh prints its column headers bold, which is what tells them apart.
        kit.insertAnsi(document, ESC + "[1mcabecera");

        assertThat(StyleConstants.isBold(attributesAt(0))).isTrue();
    }

    @Test
    void aResetTakesTheBoldOffAgain() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[1mnegrita" + ESC + "[0mnormal");

        assertThat(StyleConstants.isBold(attributesAt(0))).isTrue();
        assertThat(StyleConstants.isBold(attributesAt(7))).isFalse();
    }

    @Test
    void aResetKeepsTheConsoleMonospaced() throws BadLocationException {
        // The reset clears the styling but must not take the font with it, or the
        // tables
        // the clients draw would lose their alignment halfway through a line.
        kit.insertAnsi(document, ESC + "[31mrojo" + ESC + "[0mnormal");

        assertThat(StyleConstants.getFontFamily(attributesAt(5))).isEqualTo("Monospaced");
        assertThat(StyleConstants.getFontSize(attributesAt(5)))
                .isEqualTo(StyleConstants.getFontSize(attributesAt(0)));
    }

    @Test
    void theConsoleUsesTheFontSizeItWasBuiltWith() throws BadLocationException {
        AnsiEditorKit large = new AnsiEditorKit(20, colors);
        StyledDocument doc = new DefaultStyledDocument();

        large.insertAnsi(doc, "texto");

        assertThat(StyleConstants.getFontSize(doc.getCharacterElement(0).getAttributes()))
                .isEqualTo(20);
    }

    @Test
    void aCodeWithSeveralParametersAppliesAllOfThem() throws BadLocationException {
        // cqlsh draws its column headers with 0;1;35, meaning reset, bold, magenta, all
        // in one code. Dropping it would leave the table headers plain.
        kit.insertAnsi(document, ESC + "[0;1;35mcabecera" + ESC + "[0m");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.magenta());
        assertThat(StyleConstants.isBold(attributesAt(0))).isTrue();
    }

    @Test
    void aCodeWithSeveralParametersStillWorksForValues() throws BadLocationException {
        // The values in a cqlsh table come as 0;1;33.
        kit.insertAnsi(document, ESC + "[0;1;33m5.0.9" + ESC + "[0m");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.yellow());
    }

    @Test
    void anErrorFromCassandraIsPaintedRed() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[0;1;31mInvalidRequest" + ESC + "[0m");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.red());
        assertThat(StyleConstants.isBold(attributesAt(0))).isTrue();
    }

    @Test
    void anErrorFromNeo4jIsPaintedRed() throws BadLocationException {
        // cypher-shell uses the bright red code rather than the plain one.
        kit.insertAnsi(document, ESC + "[91msyntax error");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.brightRed());
    }

    @Test
    void aNumberFromMongoIsPaintedYellow() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[33m1" + ESC + "[39m");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.yellow());
    }

    @Test
    void italicTextIsActuallyItalic() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[3mcursiva");

        assertThat(StyleConstants.isItalic(attributesAt(0))).isTrue();
    }

    @Test
    void underlinedTextIsActuallyUnderlined() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[4msubrayado");

        assertThat(StyleConstants.isUnderline(attributesAt(0))).isTrue();
    }

    @Test
    void theCodeForNormalWeightTakesTheBoldOff() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[1mnegrita" + ESC + "[22mnormal");

        assertThat(StyleConstants.isBold(attributesAt(7))).isFalse();
    }

    @Test
    void theDefaultColourCodeGivesTheTextItsColourBack() throws BadLocationException {
        // Code 39 puts the foreground back without clearing bold or underline, which is
        // how a client says "this word only was coloured".
        kit.insertAnsi(document, ESC + "[31mrojo" + ESC + "[39mnormal");

        assertThat(attributesAt(5).isDefined(StyleConstants.Foreground)).isFalse();
    }

    @Test
    void theDefaultBackgroundCodeTakesTheBackgroundOff() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[41mfondo" + ESC + "[49mnormal");

        assertThat(attributesAt(6).isDefined(StyleConstants.Background)).isFalse();
    }

    @Test
    void aBackgroundCodePaintsABackground() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[41mfondo");

        assertThat(StyleConstants.getBackground(attributesAt(0))).isEqualTo(colors.red());
    }

    @ParameterizedTest(name = "code {0} keeps the text")
    @ValueSource(strings = { "0", "1", "39", "49", "22", "27" })
    void codesThatCarryNoColourStillLeaveTheTextAlone(String code) throws BadLocationException {
        kit.insertAnsi(document, ESC + "[" + code + "mtexto");

        assertThat(text()).isEqualTo("texto");
    }

    @ParameterizedTest(name = "code {0} does not break the console")
    @ValueSource(strings = { "0;1;35", "38;5;208", "38;2;255;0;0", "1;4;31", "99", "", "1;", ";" })
    void codesThatAreNotSupportedAreSkippedRatherThanThrown(String code) throws BadLocationException {
        // Cassandra prints 0;1;35 and mongosh prints 256-colour codes. An unknown or
        // partly known code must cost its colour, never the text and never the session.
        kit.insertAnsi(document, ESC + "[" + code + "mtexto");

        assertThat(text()).isEqualTo("texto");
    }

    @Test
    void anEmptyCodeIsNotPrintedAsText() throws BadLocationException {
        // cypher-shell emits a bare ESC[m, which used to leak through as "[m".
        kit.insertAnsi(document, "antes" + ESC + "[m" + "despues");

        assertThat(text()).isEqualTo("antesdespues");
    }

    @Test
    void textIsAppendedInOrder() throws BadLocationException {
        kit.insertAnsi(document, "uno\n");
        kit.insertAnsi(document, ESC + "[32mdos\n" + ESC + "[0m");
        kit.insertAnsi(document, "tres");

        assertThat(text()).isEqualTo("uno\ndos\ntres");
    }

    @Test
    void eachInsertKeepsItsOwnColours() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[31mrojo" + ESC + "[0m");
        int afterFirst = document.getLength();
        kit.insertAnsi(document, ESC + "[32mverde" + ESC + "[0m");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.red());
        assertThat(StyleConstants.getForeground(attributesAt(afterFirst))).isEqualTo(colors.green());
    }

    @Test
    void theConsoleIsMonospacedSoThatColumnsLineUp() throws BadLocationException {
        // cqlsh draws tables with spaces; a proportional font turns them into a mess.
        kit.insertAnsi(document, "columna");

        assertThat(StyleConstants.getFontFamily(attributesAt(0))).isEqualTo("Monospaced");
    }

    @Test
    void changingTheThemeChangesTheColoursOfWhatComesNext() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[31mclaro" + ESC + "[0m");
        int afterFirst = document.getLength();

        IAnsiColors dark = new DarkThemeAnsiColors();
        kit.setAnsiColors(dark);
        kit.insertAnsi(document, ESC + "[31moscuro" + ESC + "[0m");

        assertThat(kit.getAnsiColors()).isSameAs(dark);
        assertThat(StyleConstants.getForeground(attributesAt(afterFirst))).isEqualTo(dark.red());
    }

    @Test
    void nothingIsInsertedForEmptyText() throws BadLocationException {
        kit.insertAnsi(document, "");

        assertThat(document.getLength()).isZero();
    }

    @Test
    void codesWithNoTextAroundThemInsertNothing() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[31m" + ESC + "[0m");

        assertThat(document.getLength()).isZero();
    }

    @Test
    void aNegativeOffsetIsRejected() {
        assertThatThrownBy(() -> kit.insertAnsi(document, "texto", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theKitDeclaresTheContentTypeSwingNeeds() {
        assertThat(kit.getContentType()).isNotBlank();
    }

    @Test
    void aColourThatIsNeverClosedStillOnlyPaintsWhatFollowsIt() throws BadLocationException {
        kit.insertAnsi(document, "antes" + ESC + "[31mdespues");

        Color plain = StyleConstants.getForeground(attributesAt(0));
        assertThat(plain).isNotEqualTo(colors.red());
        assertThat(StyleConstants.getForeground(attributesAt(6))).isEqualTo(colors.red());
    }
}
