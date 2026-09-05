package com.uoc.ansi;

/**
 * The colours a console palette holds.
 *
 * <p>
 * Every one of these used to be a method on an interface, an implementation of that
 * method, a line in a switch mapping an escape code to it, an entry in a test's list and
 * a case in a test's switch. Adding one meant touching all five, and the name was typed
 * eleven times before it appeared on screen once. Named here instead, everything else
 * loops over these values.
 *
 * <p>
 * The name is also the key the theme uses, so {@code BRIGHT_MAGENTA} is
 * {@code Ansi.brightMagenta} in {@code themes/FlatLightLaf.properties}. One spelling,
 * derived rather than repeated.
 */
public enum AnsiColor {

    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE,
    BRIGHT_BLACK,
    BRIGHT_RED,
    BRIGHT_GREEN,
    BRIGHT_YELLOW,
    BRIGHT_BLUE,
    BRIGHT_MAGENTA,
    BRIGHT_CYAN,
    BRIGHT_WHITE,

    /**
     * Not an ANSI colour: the sixteen do not include an orange, and the service
     * indicators need a hue that is neither red nor yellow, because a container the
     * kernel killed for its memory use is neither a crash nor a warning.
     */
    ORANGE,

    /** What text is written in when no escape code has asked for anything else. */
    DEFAULT;

    private final String themeKey = "Ansi." + camelCase(name());

    /** Where this colour is written down in the theme. */
    public String themeKey() {
        return themeKey;
    }

    private static String camelCase(String constant) {
        String[] words = constant.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            name.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return name.toString();
    }
}
