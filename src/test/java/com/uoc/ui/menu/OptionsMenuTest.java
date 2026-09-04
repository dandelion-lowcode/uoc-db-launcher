package com.uoc.ui.menu;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("the Options menu")
class OptionsMenuTest {

    private final List<String> fontsChosen = new ArrayList<>();
    private Preferences prefs;
    private ConsoleFontManager fonts;
    private JMenu menu;

    @BeforeEach
    void buildTheMenu() throws Exception {
        prefs = Preferences.userRoot().node("uocdb-test-" + System.nanoTime());
        fonts = new ConsoleFontManager(prefs);

        SwingUtilities.invokeAndWait(() -> {
            Translations translations = new Translations(Locale.ENGLISH);
            menu = OptionsMenu.build(translations, new ThemeManager(prefs), fonts,
                    () -> { }, fontsChosen::add);
        });
    }

    @AfterEach
    void cleanUp() throws Exception {
        prefs.removeNode();
    }

    private List<Component> components() {
        return List.of(menu.getMenuComponents());
    }

    private int indexOfSeparator() {
        List<Component> components = components();
        for (int i = 0; i < components.size(); i++) {
            if (components.get(i) instanceof JSeparator) {
                return i;
            }
        }
        return -1;
    }

    private JRadioButtonMenuItem itemNamed(String name) {
        for (Component component : components()) {
            if (component instanceof JRadioButtonMenuItem item && name.equals(item.getName())) {
                return item;
            }
        }
        throw new AssertionError("no item named " + name);
    }

    @Test
    void theThemesAndTheFontsAreKeptApart() {
        assertThat(indexOfSeparator())
                .as("the two are unrelated decisions and must not run together")
                .isGreaterThan(0);
    }

    @Test
    void theThemesComeFirstAndTheFontsAfterTheSeparator() {
        int separator = indexOfSeparator();
        List<Component> components = components();

        // Three themes: light, dark, system.
        assertThat(components.subList(0, separator)).hasSize(3);
        assertThat(components.subList(separator + 1, components.size()))
                .hasSameSizeAs(fonts.availableFonts());
    }

    @Test
    void everyFontTheMachineHasIsOffered() {
        for (String family : fonts.availableFonts()) {
            assertThat(itemNamed(family)).isNotNull();
        }
    }

    @Test
    void theFontInUseIsTheOneTicked() {
        assertThat(itemNamed(fonts.selectedFont()).isSelected()).isTrue();
    }

    @Test
    void onlyOneFontIsTickedAtATime() {
        long ticked = components().stream()
                .filter(JRadioButtonMenuItem.class::isInstance)
                .map(JRadioButtonMenuItem.class::cast)
                .filter(item -> item.getName() != null && item.isSelected())
                .count();

        assertThat(ticked).isEqualTo(1);
    }

    @Test
    void choosingAFontAppliesItAndRemembersIt() throws Exception {
        String other = anotherFont();
        org.junit.jupiter.api.Assumptions.assumeTrue(
                !other.equals(ConsoleFontManager.SYSTEM_MONOSPACED),
                "this machine has no second monospaced font to choose");

        SwingUtilities.invokeAndWait(() -> itemNamed(other).doClick());

        assertThat(fontsChosen).containsExactly(other);
        assertThat(new ConsoleFontManager(prefs).selectedFont()).isEqualTo(other);
    }

    @Test
    void theRuntimeFontIsNamedInWordsRatherThanLeftAsAnIdentifier() throws Exception {
        // "Monospaced" is what the runtime calls it, not something a student would read.
        Translations spanish = new Translations(Locale.of("es"));

        assertThat(itemNamed(ConsoleFontManager.SYSTEM_MONOSPACED).getText())
                .isEqualTo(new Translations(Locale.ENGLISH).get(Message.FONT_SYSTEM))
                .isNotEqualTo(ConsoleFontManager.SYSTEM_MONOSPACED);
        assertThat(spanish.get(Message.FONT_SYSTEM)).isNotBlank();
    }

    @Test
    void theOtherFontsKeepTheNameTheirMakersGaveThem() {
        for (String family : fonts.availableFonts()) {
            if (!family.equals(ConsoleFontManager.SYSTEM_MONOSPACED)) {
                assertThat(itemNamed(family).getText()).isEqualTo(family);
            }
        }
    }

    private String anotherFont() {
        return fonts.availableFonts().stream()
                .filter(family -> !family.equals(ConsoleFontManager.SYSTEM_MONOSPACED))
                .findFirst()
                .orElse(ConsoleFontManager.SYSTEM_MONOSPACED);
    }
}
