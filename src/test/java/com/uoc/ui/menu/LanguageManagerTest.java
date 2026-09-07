package com.uoc.ui.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.uoc.ui.menu.LanguageMenu.Language;

/**
 * Which language a session opens in.
 *
 * <p>
 * Two rules, and the second is what makes the first bearable: the machine is asked once,
 * on the first run, and from then on the answer is whatever the student last picked. A
 * choice made and then forgotten is worse than no choice at all -- a student who switches
 * to Catalan and finds Spanish again the next morning cannot tell whether they clicked it.
 */
@DisplayName("the language the launcher opens in")
class LanguageManagerTest {

    private Preferences preferences;
    private LanguageManager languages;

    @BeforeEach
    void aMachineNobodyHasChosenOnYet() throws Exception {
        preferences = Preferences.userRoot().node("com/uoc/test/language");
        preferences.clear();
        languages = new LanguageManager(preferences);
    }

    @AfterEach
    void leaveNothingBehind() throws Exception {
        preferences.removeNode();
    }

    // --- The first run on a machine -------------------------------------------------

    @Test
    void aMachineSetToCatalanOpensInCatalan() {
        assertThat(LanguageManager.firstRunLanguage(Locale.of("ca")))
                .isEqualTo(Language.CATALAN);
    }

    @Test
    void theCountryDoesNotComeIntoIt() {
        assertThat(LanguageManager.firstRunLanguage(Locale.of("ca", "ES")))
                .isEqualTo(Language.CATALAN);
    }

    @ParameterizedTest(name = "a machine set to {0}")
    @ValueSource(strings = { "es", "en", "de", "ja", "gl", "eu", "fr", "pt" })
    void everyOtherMachineOpensInTheLanguageTheCourseIsTaughtIn(String language) {
        // English included: it is the bundle without a suffix, not the language the
        // course is written in. A student can still pick it from the menu.
        assertThat(LanguageManager.firstRunLanguage(Locale.of(language)))
                .isEqualTo(Language.SPANISH);
    }

    @Test
    void withNothingRememberedTheMachineIsWhatIsAsked() {
        assertThat(languages.selectedLanguage())
                .isEqualTo(LanguageManager.firstRunLanguage(Locale.getDefault()));
    }

    // --- Every run after that -------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(Language.class)
    void whateverWasChosenIsWhatOpensNextTime(Language chosen) {
        languages.select(chosen);

        assertThat(new LanguageManager(preferences).selectedLanguage()).isEqualTo(chosen);
    }

    @Test
    void aChoiceOutlivesTheMachinesOwnLanguage() {
        // The point of remembering it: a student on a Spanish machine who wants English
        // must not have to pick it again every morning.
        languages.select(Language.ENGLISH);

        assertThat(new LanguageManager(preferences).selectedLanguage())
                .isEqualTo(Language.ENGLISH)
                .isNotEqualTo(LanguageManager.firstRunLanguage(Locale.getDefault()));
    }

    @Test
    void changingItAgainReplacesWhatWasThereRatherThanAddingToIt() {
        languages.select(Language.ENGLISH);
        languages.select(Language.CATALAN);

        assertThat(new LanguageManager(preferences).selectedLanguage())
                .isEqualTo(Language.CATALAN);
    }

    // --- A preferences file older than this version ---------------------------------

    @Test
    void aLanguageThisVersionNoLongerHasFallsBackRatherThanFailing() {
        // Preferences outlive versions, and a language dropped between two of them must
        // not stop the application opening.
        preferences.put("language", "eo");

        assertThat(languages.selectedLanguage())
                .isEqualTo(LanguageManager.firstRunLanguage(Locale.getDefault()));
    }

    @Test
    void aBlankPreferenceIsTreatedAsNoneAtAll() {
        preferences.put("language", "   ");

        assertThat(languages.selectedLanguage())
                .isEqualTo(LanguageManager.firstRunLanguage(Locale.getDefault()));
    }
}
