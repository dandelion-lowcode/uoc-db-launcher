package com.uoc.docker.client;

import com.uoc.ansi.AnsiEscCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * redis-cli prints everything the same colour, which is to say none: a refused command
 * looks exactly like a result. These are the marks that tell them apart.
 *
 * <p>The marker is the one redis-cli itself uses, {@code (error)} at the start of the
 * line, so nothing has to be guessed from the wording of the message.
 */
class RedisOutputHighlighterTest {

    private static final String RESET = AnsiEscCode.RESET.escCode;
    private static final String RED = AnsiEscCode.RED.escCode;
    private static final String BOLD = AnsiEscCode.BOLD.escCode;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "(error) ERR unknown command 'COMANDO-INVENTADO'",
            "(error) ERR wrong number of arguments for 'set' command",
            "(error) ERR value is not an integer or out of range",
            "(error) WRONGTYPE Operation against a key holding the wrong kind of value",
            "(error) NOAUTH Authentication required."
    })
    void everyRefusalIsMarked(String line) {
        assertThat(RedisOutputHighlighter.highlight(line))
                .isEqualTo(RED + BOLD + line + RESET);
    }

    @ParameterizedTest(name = "{0} is left alone")
    @ValueSource(strings = {
            "PONG", "OK", "\"hola\"", "(integer) 2", "(nil)", "1) \"clave\"",
            "# Server", "redis_version:7.4.1", "", "   "
    })
    void everyOrdinaryAnswerIsLeftAlone(String line) {
        // Marking anything else would teach the student to distrust the colour.
        assertThat(RedisOutputHighlighter.highlight(line)).isEqualTo(line);
    }

    @Test
    void onlyTheLineThatFailedIsMarked() {
        String output = "1) \"uno\"\n(error) ERR algo\n2) \"dos\"";

        String[] lines = RedisOutputHighlighter.highlight(output).split("\n", -1);

        assertThat(lines[0]).isEqualTo("1) \"uno\"");
        assertThat(lines[1]).startsWith(RED);
        assertThat(lines[2]).isEqualTo("2) \"dos\"");
    }

    @Test
    void aValueThatOnlyMentionsAnErrorIsNotMarked() {
        // A student can store the word anywhere; only redis-cli's own prefix counts.
        String output = "\"(error) esto es un valor guardado\"";

        assertThat(RedisOutputHighlighter.highlight(output)).isEqualTo(output);
    }

    @Test
    void theNumberOfLinesNeverChanges() {
        String output = "OK\n(error) ERR algo\n(nil)\n";

        assertThat(RedisOutputHighlighter.highlight(output).split("\n", -1))
                .hasSameSizeAs(output.split("\n", -1));
    }

    @Test
    void theVisibleTextNeverChanges() {
        String output = "OK\n(error) ERR algo\n(nil)";

        String stripped = RedisOutputHighlighter.highlight(output)
                .replaceAll(String.valueOf((char) 27) + "\\[[0-9;]*m", "");

        assertThat(stripped).isEqualTo(output);
    }

    @Test
    void nothingIsMarkedInEmptyOutput() {
        assertThat(RedisOutputHighlighter.highlight("")).isEmpty();
    }
}
