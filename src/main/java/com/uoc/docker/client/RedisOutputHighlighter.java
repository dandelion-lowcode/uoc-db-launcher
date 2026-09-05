package com.uoc.docker.client;


import com.uoc.ansi.AnsiEscCode;

/**
 * Marks the answers redis-cli refuses.
 *
 * <p>
 * Unlike the other clients, redis-cli prints everything the same way, so a
 * refused
 * command reads exactly like a result and is easy to scroll straight past. The
 * mark added
 * here is the same one the other consoles already show, and it is put only
 * where
 * redis-cli itself says the command failed.
 */
final class RedisOutputHighlighter {

    /**
     * redis-cli begins every refusal with this, whatever the message that follows.
     */
    private static final String ERROR_PREFIX = "(error) ";

    private static final String RESET = AnsiEscCode.RESET.escCode;
    private static final String ERROR = AnsiEscCode.RED.escCode + AnsiEscCode.BOLD.escCode;

    private RedisOutputHighlighter() {
    }

    /**
     * @param output what redis-cli wrote; never {@code null}
     * @return the same text with refusals marked; every visible character is
     *         unchanged
     */
    static String highlight(String output) {
        String[] lines = output.split("\n", -1);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append('\n');
            }
            result.append(lines[i].startsWith(ERROR_PREFIX)
                    ? ERROR + lines[i] + RESET
                    : lines[i]);
        }
        return result.toString();
    }
}
