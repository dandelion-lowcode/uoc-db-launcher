package com.uoc.docker;

import java.util.regex.Pattern;

/**
 * Cleans up what a database client wrote before it reaches the console.
 *
 * <p>The clients run under a pseudo-terminal so they emit their usual colours, which also
 * means they emit things meant for a terminal rather than for a reader.
 */
public final class ConsoleOutput {

    // Terminal title sequences (ESC ] ... BEL, or ESC ] ... ESC \) carry no visible text.
    private static final Pattern OSC_SEQUENCE = buildOscPattern();

    private static Pattern buildOscPattern() {
        String esc = String.valueOf((char) 27);
        String bel = String.valueOf((char) 7);
        return Pattern.compile(esc + "\\][^" + bel + esc + "]*(?:" + bel + "|" + esc + "\\\\)");
    }

    private ConsoleOutput() {
    }

    /**
     * @param text raw client output; never {@code null}
     * @return the same text without terminal bookkeeping; never {@code null}
     */
    public static String normalize(String text) {
        // A pseudo-terminal ends every line with a carriage return before the newline;
        // dropping all of them avoids a blank line between each line of output.
        String withoutCarriageReturns = text.replace("\r", "");
        return OSC_SEQUENCE.matcher(withoutCarriageReturns).replaceAll("");
    }
}
