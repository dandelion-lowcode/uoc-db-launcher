package com.uoc.ui.menu;

import com.uoc.ui.menu.LanguageMenu.Language;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageMenuTest {

    @Test
    void theMenuOffersTheLanguagesInTheOrderTheCourseUsesThem() {
        // Catalan and Spanish are the languages of the course; English is there for
        // anyone else who asks for it, so it comes last.
        assertThat(Language.values())
                .containsExactly(Language.CATALAN, Language.SPANISH, Language.ENGLISH);
    }

    @Test
    void recognisesEachSupportedLanguage() {
        assertEquals(Language.SPANISH, Language.of(Locale.of("es")));
        assertEquals(Language.CATALAN, Language.of(Locale.of("ca")));
        assertEquals(Language.ENGLISH, Language.of(Locale.ENGLISH));
    }

    @Test
    void ignoresTheCountry() {
        assertEquals(Language.SPANISH, Language.of(Locale.of("es", "AR")));
        assertEquals(Language.ENGLISH, Language.of(Locale.US));
    }

    @Test
    void anyOtherLanguageStartsInTheOneTheCourseIsTaughtIn() {
        // Not English, which is only the bundle without a suffix. A student whose machine
        // is set to something else is likelier to want the wording of their notes.
        assertEquals(Language.SPANISH, Language.of(Locale.GERMAN));
        assertEquals(Language.SPANISH, Language.of(Locale.JAPANESE));
        assertEquals(Language.SPANISH, Language.of(Locale.of("gl")));
    }

    @Test
    void whatTheMachineAsksForStillWinsWhenWeHaveIt() {
        // The fallback must not have quietly become the answer for everyone.
        assertEquals(Language.CATALAN, Language.of(Locale.of("ca", "ES")));
        assertEquals(Language.ENGLISH, Language.of(Locale.UK));
    }
}
