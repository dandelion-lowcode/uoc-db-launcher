package com.uoc.ansi;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Both console palettes, as fixed values a test can hold at the same time.
 *
 * <p>
 * The palette lives in the look and feel now, and only one look and feel is
 * installed at
 * a time, so "the light colours" and "the dark colours" cannot both be read
 * from
 * {@link javax.swing.UIManager} at once. This installs each theme in turn and
 * copies what
 * it finds, which is still the values the application uses rather than a second
 * list
 * written down beside them.
 */
final class Palettes {

    private Palettes() {
    }

    static IAnsiColors light() {
        return snapshotOf(false);
    }

    static IAnsiColors dark() {
        return snapshotOf(true);
    }

    private static IAnsiColors snapshotOf(boolean dark) {
        FlatLaf.registerCustomDefaultsSource("themes");
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

        IAnsiColors theme = new ThemeAnsiColors();
        Map<AnsiColor, Color> taken = new EnumMap<>(AnsiColor.class);
        for (AnsiColor colour : AnsiColor.values()) {
            taken.put(colour, theme.of(colour));
        }
        // A reading of one theme, kept so it does not change when another is installed.
        return taken::get;
    }
}
