package com.uoc.docker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns what Compose prints while fetching an image into something worth watching.
 *
 * <p>
 * Compose repeats itself. Every few tenths of a second it writes another line for each
 * layer it is fetching, so a download of eight layers produces hundreds of lines that
 * differ only in a number:
 *
 * <pre>
 *  eab79789b661 Downloading 1.2MB/45MB
 *  eab79789b661 Downloading 4.7MB/45MB
 * </pre>
 *
 * <p>
 * Printed one after another they scroll past faster than they can be read and say very
 * little. Kept one per thing being fetched, and rewritten in place, they become the
 * display Docker itself shows on a terminal: a line per layer, each counting up.
 *
 * <p>
 * The lines are read rather than parsed: only enough of each is understood to know which
 * earlier line it replaces, and anything unrecognised is kept as it came. Compose is free
 * to word things differently in a later version without this hiding what it said.
 */
public class InstallProgress {

    /** What Docker calls a layer: twelve hexadecimal characters. */
    private static final Pattern LAYER = Pattern.compile("[0-9a-f]{12}");

    /**
     * Things Compose reports on by name rather than by id, where the name is the word
     * after this one and the two together identify it.
     */
    private static final java.util.Set<String> NAMED =
            java.util.Set.of("Image", "Container", "Volume", "Network", "Service");

    // Insertion-ordered, so what is shown keeps the order the work was announced in
    // rather than jumping about as lines are replaced.
    private final Map<String, String> latest = new LinkedHashMap<>();

    /**
     * Takes in one line of output.
     *
     * @param line as Compose wrote it
     */
    public void accept(String line) {
        if (line == null) {
            return;
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        latest.put(identityOf(trimmed), trimmed);
    }

    /**
     * What identifies the thing a line is about, so a later line about the same thing
     * replaces it instead of piling up underneath.
     *
     * <p>
     * A line nothing here recognises is its own identity, which means it is kept as an
     * ordinary line and never replaces anything.
     */
    private static String identityOf(String line) {
        String[] words = line.split("\\s+");
        if (LAYER.matcher(words[0]).matches()) {
            return words[0];
        }
        if (words.length > 1 && NAMED.contains(words[0])) {
            return words[0] + " " + words[1];
        }
        return line;
    }

    /** Everything being reported on, one line each, in the order it was first announced. */
    public String text() {
        return String.join("\n", latest.values());
    }

    /** Whether anything has been said yet. */
    public boolean isEmpty() {
        return latest.isEmpty();
    }
}
