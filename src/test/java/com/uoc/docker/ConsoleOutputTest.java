package com.uoc.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Partitions exercised here.
 *
 * <p>The input is client output, which may contain:
 * <ul>
 *   <li>carriage returns: none, one per line, doubled, alone at the end
 *   <li>terminal title sequences: none, one, several, terminated by BEL or by ESC backslash
 *   <li>colour sequences, which must survive untouched
 * </ul>
 * The boundary is an unterminated title sequence: the console must not swallow the rest
 * of the output looking for a terminator that never arrives.
 */
class ConsoleOutputTest {

    private static final String ESC = String.valueOf((char) 27);
    private static final String BEL = String.valueOf((char) 7);

    @Test
    void leavesPlainTextAlone() {
        assertThat(ConsoleOutput.normalize("hola\nmundo\n")).isEqualTo("hola\nmundo\n");
    }

    @Test
    void isEmptyForEmptyOutput() {
        assertThat(ConsoleOutput.normalize("")).isEmpty();
    }

    @Test
    void dropsTheCarriageReturnThatAPseudoTerminalAddsToEveryLine() {
        assertThat(ConsoleOutput.normalize("uno\r\ndos\r\n")).isEqualTo("uno\ndos\n");
    }

    @Test
    void dropsRepeatedCarriageReturns() {
        // A pseudo-terminal was emitting \r\r\n, which showed a blank line between lines.
        assertThat(ConsoleOutput.normalize("uno\r\r\ndos\r\r\n")).isEqualTo("uno\ndos\n");
    }

    @Test
    void dropsACarriageReturnWithNoNewlineAfterIt() {
        assertThat(ConsoleOutput.normalize("uno\r")).isEqualTo("uno");
    }

    @Test
    void removesATitleSequenceTerminatedByBell() {
        // mongosh sets the terminal title, which is invisible in a terminal but would be
        // printed as text in a Swing console.
        assertThat(ConsoleOutput.normalize(ESC + "]0;mongosh" + BEL + "resultado"))
                .isEqualTo("resultado");
    }

    @Test
    void removesATitleSequenceTerminatedByStringTerminator() {
        assertThat(ConsoleOutput.normalize(ESC + "]0;titulo" + ESC + "\\resultado"))
                .isEqualTo("resultado");
    }

    @Test
    void removesEveryTitleSequence() {
        String text = ESC + "]0;uno" + BEL + "a" + ESC + "]0;dos" + BEL + "b";
        assertThat(ConsoleOutput.normalize(text)).isEqualTo("ab");
    }

    @Test
    void keepsColourSequences() {
        // Only title sequences go; the colours are the whole point of the pseudo-terminal.
        String green = ESC + "[32m";
        String reset = ESC + "[0m";
        assertThat(ConsoleOutput.normalize(green + "ok" + reset)).isEqualTo(green + "ok" + reset);
    }

    @ParameterizedTest(name = "unterminated sequence [{index}] is left alone")
    @ValueSource(strings = {"]0;sin final", "]0;", "]"})
    void leavesAnUnterminatedTitleSequenceAloneRatherThanEatingTheOutput(String tail) {
        // The boundary: without a terminator there is no sequence to remove, and a greedy
        // match would delete every result that followed it.
        String text = ESC + tail + "resultado importante";
        assertThat(ConsoleOutput.normalize(text)).contains("resultado importante");
    }

    @Test
    void normalizingTwiceChangesNothingMore() {
        String text = ESC + "]0;t" + BEL + "uno\r\ndos\r\n";
        String once = ConsoleOutput.normalize(text);
        assertThat(ConsoleOutput.normalize(once)).isEqualTo(once);
    }
}
