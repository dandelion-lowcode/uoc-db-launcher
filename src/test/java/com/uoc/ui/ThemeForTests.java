package com.uoc.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;

/**
 * Puts a look and feel in place for tests that build parts of the interface.
 *
 * <p>
 * The application's colours live in the look and feel, in
 * {@code themes/FlatLightLaf.properties} and its dark twin, which FlatLaf merges into
 * {@link UIManager}. A test that builds a console without installing one therefore finds
 * no colours at all, and the console says so rather than painting nothing.
 *
 * <p>
 * In the application this is {@code ThemeManager}'s first act. Here it is this, which
 * does the same two things in the same order: the source has to be registered before a
 * theme is built, or the theme is built without it.
 */
final class ThemeForTests {

    private static boolean installed;

    private ThemeForTests() {
    }

    /** Installs the light theme, once, however many tests ask. */
    static synchronized void install() {
        if (installed) {
            return;
        }
        FlatLaf.registerCustomDefaultsSource("themes");
        FlatLightLaf.setup();
        installed = true;
    }

    /**
     * Builds the theme again, for a test that took a colour out of it on purpose and has
     * to put everything back for the tests that follow.
     */
    static synchronized void reinstall() {
        FlatLaf.registerCustomDefaultsSource("themes");
        FlatLightLaf.setup();
        installed = true;
    }
}
