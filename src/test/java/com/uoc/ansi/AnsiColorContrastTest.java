package com.uoc.ansi;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
    private record PaletteColour(String theme, String name,
            IAnsiColors palette, Function<IAnsiColors, Color> accessor,
            Color background) {

        Color colour() {
            return accessor.apply(palette);
        }

        @Override
        public String toString() {
            return theme + " theme, " + name;
        }
    }

    private static Stream<Arguments> everyColourOfEveryTheme() {
        List<Arguments> all = new ArrayList<>();
        all.addAll(coloursOf("light", new LightThemeAnsiColors(), backgroundOf(false)));
        all.addAll(coloursOf("dark", new DarkThemeAnsiColors(), backgroundOf(true)));
        return all.stream();
    }

    private static List<Arguments> coloursOf(String theme, IAnsiColors palette, Color background) {
        record Entry(String name, Function<IAnsiColors, Color> get) {
        }
        List<Entry> entries = List.of(
                new Entry("black", IAnsiColors::black),
                new Entry("red", IAnsiColors::red),
                new Entry("green", IAnsiColors::green),
                new Entry("yellow", IAnsiColors::yellow),
                new Entry("blue", IAnsiColors::blue),
                new Entry("magenta", IAnsiColors::magenta),
                new Entry("cyan", IAnsiColors::cyan),
                new Entry("white", IAnsiColors::white),
                new Entry("brightBlack", IAnsiColors::brightBlack),
                new Entry("brightRed", IAnsiColors::brightRed),
                new Entry("brightGreen", IAnsiColors::brightGreen),
                new Entry("brightYellow", IAnsiColors::brightYellow),
                new Entry("brightBlue", IAnsiColors::brightBlue),
                new Entry("brightMagenta", IAnsiColors::brightMagenta),
                new Entry("brightCyan", IAnsiColors::brightCyan),
                new Entry("brightWhite", IAnsiColors::brightWhite),
                new Entry("default", IAnsiColors::defaultColor));

        List<Arguments> arguments = new ArrayList<>();
        for (Entry entry : entries) {
            arguments.add(Arguments.of(
                    new PaletteColour(theme, entry.name(), palette, entry.get(), background)));
        }
        return arguments;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyColourOfEveryTheme")
    @DisplayName("every console colour is readable on its own background")
    void everyColourClearsTheAccessibilityThreshold(PaletteColour entry) {
        Color colour = entry.colour();

        assertThat(colour).as("%s must define a colour", entry).isNotNull();
        assertThat(contrastRatio(colour, entry.background()))
                .as("%s: %s on %s", entry, hex(colour), hex(entry.background()))
                .isGreaterThanOrEqualTo(MINIMUM_RATIO);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyColourOfEveryTheme")
    @DisplayName("no console colour disappears into its background")
    void everyColourIsDistinctFromTheBackgroundItSitsOn(PaletteColour entry) {
        assertThat(entry.colour()).as("%s", entry).isNotEqualTo(entry.background());
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
