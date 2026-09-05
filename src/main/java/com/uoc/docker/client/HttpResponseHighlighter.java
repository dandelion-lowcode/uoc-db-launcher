package com.uoc.docker.client;

import com.uoc.ansi.AnsiEscCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds colour to a raw HTTP response.
 *
 * <p>Riak is taught through its REST API, so its console shows whatever curl prints:
 * a status line, a block of headers and the body. The database clients of the other
 * tabs colour their own output, and this brings Riak to the same level by marking the
 * status line by response class and pushing the headers into the background, leaving
 * the body plain because that is what the exercises look at.
 *
 * <p>When a request cannot be made at all there is no response to colour, only the
 * message curl prints; those lines are marked as the failures they are. Anything else
 * that is not part of a response is returned untouched.
 */
final class HttpResponseHighlighter {

    private static final Pattern STATUS_LINE = Pattern.compile("^HTTP/[0-9.]+ ([0-9]{3})\\b.*");
    private static final Pattern HEADER_LINE = Pattern.compile("^([A-Za-z0-9-]+): (.*)$");
    /**
     * Both the client and the shell that runs it report failures the same way, whether
     * the request could not be made, the flags were wrong or the command was mistyped.
     *
     * <p>
     * Two fixed prefixes, so they are compared as such. Written as the pattern
     * {@code ^(curl|sh): .*} it said the same thing in a language that has to be parsed
     * to be read, for a test any two string comparisons make.
     */
    private static final String[] ERROR_PREFIXES = { "curl: ", "sh: " };

    private static final String RESET = AnsiEscCode.RESET.escCode;
    private static final String BOLD = AnsiEscCode.BOLD.escCode;
    private static final String HEADER_NAME = AnsiEscCode.CYAN.escCode;
    private static final String HEADER_VALUE = AnsiEscCode.BRIGHT_BLACK.escCode;
    private static final String SEPARATOR = ": ";

    private HttpResponseHighlighter() {
    }

    static String highlight(String response) {
        String[] lines = response.split("\n", -1);
        StringBuilder result = new StringBuilder();
        boolean inHeaders = false;
        boolean responseStarted = false;

        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append('\n');
            }
            result.append(decorate(lines[i], inHeaders, responseStarted));

            if (!inHeaders && STATUS_LINE.matcher(lines[i]).matches()) {
                inHeaders = true;
                responseStarted = true;
            } else if (inHeaders && lines[i].isEmpty()) {
                inHeaders = false;
            }
        }
        return result.toString();
    }

    /** Whether the line is the client or its shell complaining rather than a response. */
    private static boolean isClientFailure(String line) {
        for (String prefix : ERROR_PREFIXES) {
            if (line.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String decorate(String line, boolean inHeaders, boolean responseStarted) {
        // A client or shell failure means no response arrived at all. Once one has, the
        // same words are part of what the database stored and must be left as content.
        if (!responseStarted && isClientFailure(line)) {
            return AnsiEscCode.RED.escCode + BOLD + line + RESET;
        }
        if (!inHeaders) {
            Matcher status = STATUS_LINE.matcher(line);
            if (status.matches()) {
                int code = Integer.parseInt(status.group(1));
                return statusColour(code) + BOLD + line + RESET;
            }
            return line;
        }

        Matcher header = HEADER_LINE.matcher(line);
        if (header.matches()) {
            return HEADER_NAME + header.group(1) + RESET
                    + HEADER_VALUE + SEPARATOR + header.group(2) + RESET;
        }
        return line;
    }

    private static String statusColour(int code) {
        if (code < 300) {
            return AnsiEscCode.GREEN.escCode;
        }
        if (code < 400) {
            return AnsiEscCode.CYAN.escCode;
        }
        if (code < 500) {
            return AnsiEscCode.YELLOW.escCode;
        }
        return AnsiEscCode.RED.escCode;
    }
}
