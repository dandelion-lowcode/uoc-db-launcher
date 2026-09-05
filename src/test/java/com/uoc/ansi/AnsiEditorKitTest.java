package com.uoc.ansi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.util.stream.Stream;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    private final IAnsiColors colors = Palettes.light();
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

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.of(AnsiColor.RED));
    }

    @Test
    void aResetPutsTheColourBack() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[31mrojo" + ESC + "[0mnormal");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.of(AnsiColor.RED));
        assertThat(StyleConstants.getForeground(attributesAt(4))).isNotEqualTo(colors.of(AnsiColor.RED));
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

    /**
     * Every code that paints the text, in both themes.
     *
     * <p>
     * Taken from the codes themselves rather than from a list written here. A list
     * has to
     * be added to whenever a colour is, and the one it replaced covered exactly the
     * thirty-two rows somebody had thought to type; this covers every code there
     * is.
     */
    static Stream<Arguments> everyColourCodeInBothThemes() {
        return java.util.Arrays.stream(AnsiEscCode.values())
                .filter(code -> code.colour() != null && !code.isBackground())
                .flatMap(code -> Stream.of(Arguments.of(code, false), Arguments.of(code, true)));
    }

    @ParameterizedTest(name = "{0} in the {1} theme")
    @MethodSource("everyColourCodeInBothThemes")
    void everyColourCodePaintsTheColourItNames(AnsiEscCode code, boolean dark)
            throws BadLocationException {
        // Both palettes, because a colour that happens to match the default in one
        // theme
        // would hide a code that painted nothing at all.
        IAnsiColors palette = dark ? Palettes.dark() : Palettes.light();
        AnsiEditorKit themed = new AnsiEditorKit(palette);
        StyledDocument doc = new DefaultStyledDocument();

        themed.insertAnsi(doc, code.escCode + "texto");

        AttributeSet attributes = doc.getCharacterElement(0).getAttributes();
        assertThat(attributes.isDefined(StyleConstants.Foreground))
                .as("%s painted no colour at all", code)
                .isTrue();
        assertThat(StyleConstants.getForeground(attributes))
                .isEqualTo(palette.of(code.colour()));
    }

    @ParameterizedTest(name = "{0} in the {1} theme")
    @MethodSource("everyColourCodeInBothThemes")
    void theBackgroundTwinOfEveryColourCodePaintsBehindTheTextInstead(AnsiEscCode code,
            boolean dark) throws BadLocationException {
        // The background codes used to have a case each of their own, so nothing
        // checked
        // that the two halves agreed. Now one rule covers both and this says so.
        AnsiEscCode behind = AnsiEscCode.valueOf(code.name() + "_BACKGROUND");
        IAnsiColors palette = dark ? Palettes.dark() : Palettes.light();
        AnsiEditorKit themed = new AnsiEditorKit(palette);
        StyledDocument doc = new DefaultStyledDocument();

        themed.insertAnsi(doc, behind.escCode + "texto");

        AttributeSet attributes = doc.getCharacterElement(0).getAttributes();
        assertThat(StyleConstants.getBackground(attributes))
                .isEqualTo(palette.of(code.colour()));
        assertThat(attributes.isDefined(StyleConstants.Foreground))
                .as("%s must not touch the text colour", behind)
                .isFalse();
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

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.of(AnsiColor.MAGENTA));
        assertThat(StyleConstants.isBold(attributesAt(0))).isTrue();
    }

    @Test
    void aCodeWithSeveralParametersStillWorksForValues() throws BadLocationException {
        // The values in a cqlsh table come as 0;1;33.
        kit.insertAnsi(document, ESC + "[0;1;33m5.0.9" + ESC + "[0m");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.of(AnsiColor.YELLOW));
    }

    @Test
    void anErrorFromCassandraIsPaintedRed() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[0;1;31mInvalidRequest" + ESC + "[0m");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.of(AnsiColor.RED));
        assertThat(StyleConstants.isBold(attributesAt(0))).isTrue();
    }

    @Test
    void anErrorFromNeo4jIsPaintedRed() throws BadLocationException {
        // cypher-shell uses the bright red code rather than the plain one.
        kit.insertAnsi(document, ESC + "[91msyntax error");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.of(AnsiColor.BRIGHT_RED));
    }

    @Test
    void aNumberFromMongoIsPaintedYellow() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[33m1" + ESC + "[39m");

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.of(AnsiColor.YELLOW));
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

        assertThat(StyleConstants.getBackground(attributesAt(0))).isEqualTo(colors.of(AnsiColor.RED));
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

        assertThat(StyleConstants.getForeground(attributesAt(0))).isEqualTo(colors.of(AnsiColor.RED));
        assertThat(StyleConstants.getForeground(attributesAt(afterFirst))).isEqualTo(colors.of(AnsiColor.GREEN));
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

        IAnsiColors dark = Palettes.dark();
        kit.setAnsiColors(dark);
        kit.insertAnsi(document, ESC + "[31moscuro" + ESC + "[0m");

        assertThat(kit.getAnsiColors()).isSameAs(dark);
        assertThat(StyleConstants.getForeground(attributesAt(afterFirst))).isEqualTo(dark.of(AnsiColor.RED));
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
        assertThat(plain).isNotEqualTo(colors.of(AnsiColor.RED));
        assertThat(StyleConstants.getForeground(attributesAt(6))).isEqualTo(colors.of(AnsiColor.RED));
    }
    @Test
    void clearingTheScreenEmptiesTheConsoleRatherThanPrintingTheInstruction()
            throws BadLocationException {
        // What mongosh sends for "cls": put the cursor at the top, then wipe what is
        // below. Neither is a colour, so both used to be left in the document and read as
        // "[1;1H[0J" while nothing was cleared.
        kit.insertAnsi(document, "algo que estaba antes\n");

        kit.insertAnsi(document, ESC + "[1;1H" + ESC + "[0J");

        assertThat(text()).isEmpty();
    }

    @Test
    void whatFollowsAClearIsAllThatIsLeft() throws BadLocationException {
        kit.insertAnsi(document, "viejo\n" + ESC + "[2J" + "nuevo");

        assertThat(text()).isEqualTo("nuevo");
    }

    @Test
    void movingTheCursorIsSwallowedRatherThanPrinted() throws BadLocationException {
        // There is no cursor to move here. Printing the instruction is the one thing that
        // must not happen.
        kit.insertAnsi(document, "antes" + ESC + "[1;1H" + "despues");

        assertThat(text()).isEqualTo("antesdespues");
    }

    @ParameterizedTest(name = "ESC[{0}")
    @ValueSource(strings = { "2A", "1B", "10C", "3D", "s", "u", "6n", "?25l", "?25h", "1K" })
    void everyOtherControlSequenceDisappearsWithoutTakingTextWithIt(String sequence)
            throws BadLocationException {
        kit.insertAnsi(document, "antes" + ESC + "[" + sequence + "despues");

        assertThat(text()).isEqualTo("antesdespues");
    }

    @Test
    void aClearDoesNotDisturbTheColourOfWhatComesAfterIt() throws BadLocationException {
        kit.insertAnsi(document, ESC + "[31m" + ESC + "[2J" + "rojo");

        assertThat(text()).isEqualTo("rojo");
        assertThat(StyleConstants.getForeground(attributesAt(0)))
                .isEqualTo(colors.of(AnsiColor.RED));
    }
}
