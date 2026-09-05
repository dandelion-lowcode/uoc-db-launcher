package com.uoc.ansi;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.UIManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Checks the console palettes against the console backgrounds they are painted
 * on.
 *
 * <p>
 * The palettes were built to clear the WCAG AA contrast ratio for normal text,
 * which
 * is the difference between a student reading a red error and squinting at it.
 * That claim
 * is only worth anything if something verifies it, so this measures every
 * colour of both
 * themes rather than trusting the numbers that were written down when they were
 * chosen.
 */
class AnsiColorContrastTest {

    /** WCAG AA for normal-size text. */
    private static final double MINIMUM_RATIO = 4.5;

    /**
     * The backgrounds are read from the look and feel rather than written down
     * here, so
     * the check measures the colours the student actually sees. A hard-coded value
     * would
     * quietly stop matching the moment FlatLaf changed its theme.
     */
    private static Color backgroundOf(boolean dark) {
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        Color background = UIManager.getColor("TextPane.background");
        assertThat(background).as("the look and feel must define a console background").isNotNull();
        return background;
    }

    /** Relative luminance as defined by WCAG 2.1. */
    private static double luminance(Color color) {
        double r = channel(color.getRed());
        double g = channel(color.getGreen());
        double b = channel(color.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static double contrastRatio(Color foreground, Color background) {
        double lighter = Math.max(luminance(foreground), luminance(background));
        double darker = Math.min(luminance(foreground), luminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    /**
     * The colour is carried as the accessor rather than as an already-read value,
     * so the
     * call happens inside the test. Reading it while building the arguments would
     * put it
     * outside every test, where a mutation testing tool cannot tell which test
     * covers it.
     */
    private record PaletteColour(String theme, boolean dark, AnsiColor colour,
            Color background) {

        /**
         * The palette now lives in the look and feel, so the theme has to be the one
         * installed at the moment the colour is read. Installing it here rather than
         * while the arguments are built is what keeps the reading inside the test.
         */
        Color value() {
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            return new ThemeAnsiColors().of(colour);
        }

        @Override
        public String toString() {
            return theme + " theme, " + colour.themeKey();
        }
    }

    private static Stream<Arguments> everyColourOfEveryTheme() {
        // Registered before any theme is built, or the theme is built without our
        // colours in it and every one of them comes back missing.
        com.formdev.flatlaf.FlatLaf.registerCustomDefaultsSource("themes");

        List<Arguments> all = new ArrayList<>();
        all.addAll(coloursOf("light", false, backgroundOf(false)));
        all.addAll(coloursOf("dark", true, backgroundOf(true)));
        return all.stream();
    }

    /**
     * Every colour the palette holds, without listing them: they are named once, in
     * {@link AnsiColor}, and everything that walks them loops over that.
     */
    private static List<Arguments> coloursOf(String theme, boolean dark, Color background) {
        List<Arguments> arguments = new ArrayList<>();
        for (AnsiColor colour : AnsiColor.values()) {
            arguments.add(Arguments.of(new PaletteColour(theme, dark, colour, background)));
        }
        return arguments;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyColourOfEveryTheme")
    @DisplayName("every console colour is readable on its own background")
    void everyColourClearsTheAccessibilityThreshold(PaletteColour entry) {
        Color colour = entry.value();

        assertThat(colour).as("%s must define a colour", entry).isNotNull();
        assertThat(contrastRatio(colour, entry.background()))
                .as("%s: %s on %s", entry, hex(colour), hex(entry.background()))
                .isGreaterThanOrEqualTo(MINIMUM_RATIO);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyColourOfEveryTheme")
    @DisplayName("no console colour disappears into its background")
    void everyColourIsDistinctFromTheBackgroundItSitsOn(PaletteColour entry) {
        assertThat(entry.value()).as("%s", entry).isNotEqualTo(entry.background());
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
