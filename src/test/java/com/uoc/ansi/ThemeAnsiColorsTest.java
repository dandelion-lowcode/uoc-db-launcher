package com.uoc.ansi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import javax.swing.UIManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * The palette read from whatever theme is installed.
 *
 * <p>
 * There is nothing here to hold a colour wrong: the class either finds one in the theme
 * or it does not. What is worth pinning is the second case, because a colour that is
 * missing paints nothing, and a console printing invisible text is a very quiet way to
 * fail -- quiet enough to reach a student.
 */
@DisplayName("reading the console palette out of the theme")
class ThemeAnsiColorsTest {

    private final IAnsiColors colours = new ThemeAnsiColors();

    @ParameterizedTest(name = "{0}")
    @EnumSource(AnsiColor.class)
    void everyColourTheConsoleCanAskForIsInTheTheme(AnsiColor colour) {
        ourTheme();

        assertThat(colours.of(colour)).as("%s", colour).isNotNull();
    }

    @Test
    void aThemeWithoutOurColoursSaysWhichOneIsMissing() throws Exception {
        // Any look and feel that is not ours. The message names the key because the only
        // way to arrive here is a line left out of the properties.
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        try {
            assertThat(catchThrowable(() -> colours.of(AnsiColor.RED)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(AnsiColor.RED.themeKey());
        } finally {
            ourTheme();
        }
    }

    @Test
    void theSameInstanceFollowsAChangeOfThemeRatherThanKeepingWhatItFirstRead() {
        // One of these is built per console and kept for the life of the window, and the
        // theme can be changed under it from the menu at any point.
        ourTheme();
        java.awt.Color light = colours.of(AnsiColor.GREEN);

        com.formdev.flatlaf.FlatDarkLaf.setup();
        try {
            assertThat(colours.of(AnsiColor.GREEN)).isNotEqualTo(light);
        } finally {
            ourTheme();
        }
    }

    /** The application's own theme, colours included. */
    private static void ourTheme() {
        FlatLaf.registerCustomDefaultsSource("themes");
        FlatLightLaf.setup();
    }
}
