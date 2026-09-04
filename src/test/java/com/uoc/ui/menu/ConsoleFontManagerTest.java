package com.uoc.ui.menu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which monospaced fonts the console offers.
 *
 * <p>What is checked is the promise, not a particular list: the machine running the tests
 * has whatever fonts it happens to have, and so will a student's.
 */
@DisplayName("the monospaced fonts on offer")
class ConsoleFontManagerTest {

    private Preferences prefs;
    private ConsoleFontManager fonts;

    @BeforeEach
    void useAPreferenceNodeOfItsOwn() throws Exception {
        // A node apart from the application's, so a test never changes what the student
        // sees and never reads what they chose.
        prefs = Preferences.userRoot().node("uocdb-test-" + System.nanoTime());
        fonts = new ConsoleFontManager(prefs);
    }

    @AfterEach
    void cleanUp() throws Exception {
        prefs.removeNode();
    }

    @Test
    void thereIsAlwaysSomethingToOffer() {
        // No monospaced font ships with Windows, macOS and Linux alike, so the runtime's
        // own is the only one that can be promised.
        assertThat(fonts.availableFonts()).isNotEmpty();
        assertThat(fonts.availableFonts()).contains(Font.MONOSPACED);
    }

    @Test
    void theOneEverySystemHasComesFirst() {
        assertThat(fonts.availableFonts().get(0)).isEqualTo(ConsoleFontManager.SYSTEM_MONOSPACED);
    }

    @Test
    void nothingIsOfferedThatTheMachineDoesNotHave() {
        List<String> installed = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());

        assertThat(fonts.availableFonts())
                .filteredOn(family -> !family.equals(ConsoleFontManager.SYSTEM_MONOSPACED))
                .allSatisfy(family -> assertThat(installed)
                        .as("%s is offered but not installed", family)
                        .contains(family));
    }

    @Test
    void everyFontIsOfferedOnlyOnce() {
        assertThat(fonts.availableFonts()).doesNotHaveDuplicates();
    }

    @Test
    void theListCannotBeChangedFromOutside() {
        assertThat(fonts.availableFonts()).isUnmodifiable();
    }

    @Test
    void beforeChoosingAnythingTheRuntimeFontIsUsed() {
        assertThat(fonts.selectedFont()).isEqualTo(ConsoleFontManager.SYSTEM_MONOSPACED);
    }

    @Test
    void aChoiceIsRememberedForNextTime() {
        String other = anotherAvailableFont();

        fonts.select(other);

        assertThat(new ConsoleFontManager(prefs).selectedFont()).isEqualTo(other);
    }

    @Test
    void aFontThisMachineDoesNotHaveIsNotAccepted() {
        fonts.select("Una Fuente Que No Existe");

        assertThat(fonts.selectedFont()).isEqualTo(ConsoleFontManager.SYSTEM_MONOSPACED);
    }

    @Test
    void aFontThatWasChosenOnAnotherMachineIsQuietlyForgotten() {
        // A student moves between the classroom and their laptop, or uninstalls a font.
        // Drawing the console in something proportional would be worse than forgetting.
        prefs.put("consoleFont", "Fuente De Otro Ordenador");

        assertThat(new ConsoleFontManager(prefs).selectedFont())
                .isEqualTo(ConsoleFontManager.SYSTEM_MONOSPACED);
    }

    @Test
    void everyFontOfferedCanActuallyBeUsedToDrawWith() {
        for (String family : fonts.availableFonts()) {
            Font font = new Font(family, Font.PLAIN, 12);

            assertThat(font).as("%s cannot be built", family).isNotNull();
            assertThat(font.getSize()).isEqualTo(12);
        }
    }

    private String anotherAvailableFont() {
        return fonts.availableFonts().stream()
                .filter(family -> !family.equals(ConsoleFontManager.SYSTEM_MONOSPACED))
                .findFirst()
                .orElse(ConsoleFontManager.SYSTEM_MONOSPACED);
    }
}
