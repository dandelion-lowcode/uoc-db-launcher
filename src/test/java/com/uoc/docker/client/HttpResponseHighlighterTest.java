package com.uoc.docker.client;

import com.uoc.ansi.AnsiEscCode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpResponseHighlighterTest {

    private static final String RESET = AnsiEscCode.RESET.escCode;
    private static final String BOLD = AnsiEscCode.BOLD.escCode;

    private static String firstLine(String text) {
        return HttpResponseHighlighter.highlight(text).split("\n")[0];
    }

    @Test
    void marksASuccessfulStatusLine() {
        assertEquals(AnsiEscCode.GREEN.escCode + BOLD + "HTTP/1.1 200 OK" + RESET,
                firstLine("HTTP/1.1 200 OK\n\nbody"));
    }

    @Test
    void marksAMissingObjectApart() {
        // The course walks through a 404 on purpose, so it must not look like a failure.
        assertEquals(AnsiEscCode.YELLOW.escCode + BOLD + "HTTP/1.1 404 Object Not Found" + RESET,
                firstLine("HTTP/1.1 404 Object Not Found\n\nnot found"));
    }

    @Test
    void marksAServerFailure() {
        assertEquals(AnsiEscCode.RED.escCode + BOLD + "HTTP/1.1 500 Internal Server Error" + RESET,
                firstLine("HTTP/1.1 500 Internal Server Error\n\n"));
    }

    @Test
    void marksARedirection() {
        assertEquals(AnsiEscCode.CYAN.escCode + BOLD + "HTTP/1.1 301 Moved Permanently" + RESET,
                firstLine("HTTP/1.1 301 Moved Permanently\n\n"));
    }

    @Test
    void separatesHeaderNameFromValue() {
        String highlighted = HttpResponseHighlighter.highlight("HTTP/1.1 200 OK\nContent-Type: text/plain\n\nhi");
        assertEquals(AnsiEscCode.CYAN.escCode + "Content-Type" + RESET
                        + AnsiEscCode.BRIGHT_BLACK.escCode + ": text/plain" + RESET,
                highlighted.split("\n")[1]);
    }

    @Test
    void leavesTheBodyAlone() {
        String body = "{\"a\":1}";
        String highlighted = HttpResponseHighlighter.highlight("HTTP/1.1 200 OK\nContent-Length: 7\n\n" + body);
        assertEquals(body, highlighted.split("\n")[3]);
    }

    @Test
    void doesNotTreatBodyTextAsAHeader() {
        String highlighted = HttpResponseHighlighter.highlight("HTTP/1.1 200 OK\n\nname: still body");
        assertEquals("name: still body", highlighted.split("\n")[2]);
    }

    @Test
    void marksAClientFailure() {
        assertEquals(AnsiEscCode.RED.escCode + BOLD + "curl: (7) Failed to connect" + RESET,
                firstLine("curl: (7) Failed to connect"));
    }

    @Test
    void marksAWrongFlagAndAMistypedCommand() {
        assertEquals(AnsiEscCode.RED.escCode + BOLD + "curl: option --bogus: is unknown" + RESET,
                firstLine("curl: option --bogus: is unknown"));
        assertEquals(AnsiEscCode.RED.escCode + BOLD + "sh: 1: crul: not found" + RESET,
                firstLine("sh: 1: crul: not found"));
    }

    @Test
    void keepsALineInTheHeaderBlockThatIsNotAHeader() {
        // A folded header value, or a name with a character the pattern does not allow,
        // must still reach the console. Dropping it would silently hide part of the answer.
        String response = "HTTP/1.1 200 OK\nX-Riak: uno\n    continuacion del valor\n\ncuerpo";

        assertEquals("    continuacion del valor",
                HttpResponseHighlighter.highlight(response).split("\n")[2]);
    }

    @Test
    void keepsAHeaderWhoseNameThePatternDoesNotRecognise() {
        String response = "HTTP/1.1 200 OK\nX_Guion_Bajo: valor\n\ncuerpo";

        assertEquals("X_Guion_Bajo: valor",
                HttpResponseHighlighter.highlight(response).split("\n")[1]);
    }

    @Test
    void doesNotTreatStoredContentAsAFailure() {
        // Riak stores whatever the student puts in it, including text that reads like a
        // curl failure. Inside a response that text is data, not an error.
        String response = "HTTP/1.1 200 OK\nContent-Type: text/plain\n\ncurl: (7) texto guardado";

        String body = HttpResponseHighlighter.highlight(response).split("\n")[3];

        assertEquals("curl: (7) texto guardado", body);
    }

    @Test
    void doesNotTreatAHeaderNamedLikeACommandAsAFailure() {
        String response = "HTTP/1.1 200 OK\nsh: valor\n\ncuerpo";

        assertEquals(AnsiEscCode.CYAN.escCode + "sh" + RESET
                        + AnsiEscCode.BRIGHT_BLACK.escCode + ": valor" + RESET,
                HttpResponseHighlighter.highlight(response).split("\n")[1]);
    }

    @Test
    void leavesUnrelatedTextUntouched() {
        String text = "nothing to see\nhere at all\n";
        assertEquals(text, HttpResponseHighlighter.highlight(text));
    }

    @Test
    void keepsTheLineCount() {
        String response = "HTTP/1.1 200 OK\nContent-Length: 2\n\nhi\n";
        assertEquals(response.split("\n", -1).length,
                HttpResponseHighlighter.highlight(response).split("\n", -1).length);
    }

    @Test
    void closesEveryColourItOpens() {
        String highlighted = HttpResponseHighlighter.highlight("HTTP/1.1 200 OK\nDate: today\n\nhi");
        int resets = 0;
        for (int i = highlighted.indexOf(RESET); i >= 0; i = highlighted.indexOf(RESET, i + 1)) {
            resets++;
        }
        assertTrue(resets > 0, "nothing was coloured");
        assertTrue(highlighted.endsWith("hi"), "the body must not be left inside a colour");
    }
}
