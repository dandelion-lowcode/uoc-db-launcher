package com.uoc.ui.menu;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * The monospaced fonts the console can be read in.
 *
 * <p>No monospaced font is installed by default on Windows, macOS and Linux alike, so
 * rather than naming one and hoping, this asks the machine which of the usual ones it
 * actually has and offers only those. The font the Java runtime calls "Monospaced" is
 * always offered first, because it is the one guaranteed to exist.
 *
 * <p>Reading the fonts of a system can fail in ways that are not worth crashing over: a
 * headless machine, a broken font cache, a restricted environment. Any failure here means
 * the console is drawn in the runtime's own monospaced font, which is what it did before
 * this choice existed.
 */
public class ConsoleFontManager {

    /** What the Java runtime maps to whatever the system considers monospaced. */
    public static final String SYSTEM_MONOSPACED = Font.MONOSPACED;

    /**
     * The monospaced fonts worth offering when they are present. The first group ships
     * with Windows, the second with macOS, the third with the common Linux desktops, and
     * the last are open fonts a student may well have installed already.
     */
    private static final List<String> CANDIDATES = List.of(
            "Cascadia Mono", "Cascadia Code", "Consolas", "Lucida Console",
            "Menlo", "Monaco", "SF Mono",
            "Ubuntu Mono", "DejaVu Sans Mono", "Liberation Mono", "Noto Sans Mono",
            "Courier New",
            "JetBrains Mono", "IBM Plex Mono", "Source Code Pro", "Fira Code", "Hack");

    private static final String PREF_KEY = "consoleFont";

    private final Preferences prefs;
    private final List<String> available;

    public ConsoleFontManager(Preferences prefs) {
        this.prefs = prefs;
        this.available = findAvailable();
    }

    /** The fonts to offer, the runtime's own monospaced first. Never empty. */
    public List<String> availableFonts() {
        return available;
    }

    /**
     * The font the console should use: what was chosen last time, as long as this machine
     * still has it. A student moving between machines, or uninstalling a font, gets the
     * runtime's monospaced back rather than a console drawn in something proportional.
     */
    public String selectedFont() {
        String saved = prefs.get(PREF_KEY, SYSTEM_MONOSPACED);
        return available.contains(saved) ? saved : SYSTEM_MONOSPACED;
    }

    public void select(String family) {
        if (available.contains(family)) {
            prefs.put(PREF_KEY, family);
        }
    }

    private static List<String> findAvailable() {
        List<String> fonts = new ArrayList<>();
        fonts.add(SYSTEM_MONOSPACED);

        Set<String> installed = installedFamilies();
        for (String candidate : CANDIDATES) {
            if (installed.contains(candidate)) {
                fonts.add(candidate);
            }
        }
        return List.copyOf(fonts);
    }

    private static Set<String> installedFamilies() {
        try {
            return new LinkedHashSet<>(Arrays.asList(GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()));
        } catch (Exception | Error e) {
            // Not worth failing over: the console still has a font to be drawn in.
            return Set.of();
        }
    }
}
