package com.uoc.docker.client;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Properties that must hold for any HTTP response, not just the handful of real ones the
 * example-based tests capture.
 *
 * <p>The one that matters is that colouring never changes a single visible character:
 * a student reading a response must see exactly what Riak sent. Stripping the escape
 * codes back out has to return the original text, byte for byte.
 */
class HttpResponseHighlighterProperties {

    private static final String ESC = String.valueOf((char) 27);

    private static String withoutColours(String text) {
        return text.replaceAll(ESC + "\\[[0-9;]*m", "");
    }

    @Property
    void colouringNeverChangesTheVisibleText(@ForAll("responses") String response) {
        assertThat(withoutColours(HttpResponseHighlighter.highlight(response)))
                .isEqualTo(response);
    }

    @Property
    void colouringNeverChangesTheNumberOfLines(@ForAll("responses") String response) {
        assertThat(HttpResponseHighlighter.highlight(response).split("\n", -1))
                .hasSameSizeAs(response.split("\n", -1));
    }

    @Property
    void noColourEscapesTheLineItWasOpenedOn(@ForAll("responses") String response) {
        // A colour left open would tint whatever the console printed next, which is how
        // an earlier version left the whole session the colour of the last header.
        for (String line : HttpResponseHighlighter.highlight(response).split("\n", -1)) {
            if (line.contains(ESC)) {
                assertThat(line)
                        .as("line <%s> opens a colour and must close it", line)
                        .endsWith(ESC + "[0m");
            }
        }
    }

    @Property
    void everyEscapeSequenceIsWellFormed(@ForAll("responses") String response) {
        String highlighted = HttpResponseHighlighter.highlight(response);
        // Anything the renderer cannot parse would be printed as visible rubbish.
        assertThat(highlighted.replaceAll(ESC + "\\[[0-9;]*m", "")).doesNotContain(ESC);
    }

    @Property
    void textThatIsNotAResponseComesBackUntouched(@ForAll("plainLines") String text) {
        assertThat(HttpResponseHighlighter.highlight(text)).isEqualTo(text);
    }

    @Property
    void theStatusLineIsAlwaysColouredByItsClass(
            @ForAll @IntRange(min = 100, max = 599) int code) {
        String response = "HTTP/1.1 " + code + " Some Reason\n\nbody";

        String firstLine = HttpResponseHighlighter.highlight(response).split("\n")[0];

        String expected = code < 300 ? "[32m" : code < 400 ? "[36m" : code < 500 ? "[33m" : "[31m";
        assertThat(firstLine).startsWith(ESC + expected);
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    @Provide
    Arbitrary<String> responses() {
        Arbitrary<Integer> codes = Arbitraries.integers().between(100, 599);
        Arbitrary<List<String>> headers = headerLine().list().ofMaxSize(6);
        Arbitrary<String> bodies = bodyText();

        return Arbitraries.oneOf(
                // A full response: status line, headers, blank line, body.
                Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(8)
                        .flatMap(reason -> codes.flatMap(code -> headers.flatMap(hs ->
                                bodies.map(body -> "HTTP/1.1 " + code + " " + reason + "\n"
                                        + String.join("\n", hs)
                                        + (hs.isEmpty() ? "" : "\n")
                                        + "\n" + body)))),
                // Something that is not a response at all, such as a curl failure.
                plainLines());
    }

    @Provide
    Arbitrary<String> plainLines() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz0123456789 {}\":,.-/\n")
                .ofMaxLength(80)
                // A generated line must not accidentally look like a status line.
                .filter(text -> !text.startsWith("HTTP/"));
    }

    private Arbitrary<String> headerLine() {
        Arbitrary<String> names = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-")
                .ofMinLength(1).ofMaxLength(16);
        Arbitrary<String> values = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz0123456789 /.:;=\"<>")
                .ofMaxLength(30);
        return names.flatMap(name -> values.map(value -> name + ": " + value));
    }

    private Arbitrary<String> bodyText() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz0123456789{}\":,. \n")
                .ofMaxLength(60);
    }

    @Property
    void aResponseWithManyHeadersStillColoursEveryHeaderName(
            @ForAll @Size(min = 1, max = 10) List<@net.jqwik.api.constraints.AlphaChars
                    @net.jqwik.api.constraints.StringLength(min = 1, max = 10) String> names) {
        StringBuilder response = new StringBuilder("HTTP/1.1 200 OK\n");
        for (String name : names) {
            response.append(name).append(": valor\n");
        }
        response.append("\ncuerpo");

        String highlighted = HttpResponseHighlighter.highlight(response.toString());

        assertThat(countOf(highlighted, ESC + "[36m")).isEqualTo(names.size());
    }
}
