package com.uoc.ui.menu;

import com.uoc.ui.menu.LanguageMenu.Language;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageMenuTest {

    @Test
    void theMenuOffersTheLanguagesInTheOrderTheCourseUsesThem() {
        // Catalan and Spanish are the languages of the course; English is the fallback for
        // everyone else, so it comes last.
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
    void fallsBackToTheLanguageTheBundleWithoutASuffixProvides() {
        // A machine set to German loads messages.properties, so English must be the one
        // shown as selected rather than whichever item happens to come first.
        assertEquals(Language.ENGLISH, Language.of(Locale.GERMAN));
        assertEquals(Language.ENGLISH, Language.of(Locale.JAPANESE));
    }
}
