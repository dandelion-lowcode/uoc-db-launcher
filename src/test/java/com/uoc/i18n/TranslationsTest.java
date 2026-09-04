package com.uoc.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationsTest {

    // setLocale changes the default locale of the whole JVM, so it is put back
    // after each
    // test. Without this a test would decide what the next one sees.
    private Locale originalDefault;

    @BeforeEach
    void rememberTheDefaultLocale() {
        originalDefault = Locale.getDefault();
    }

    @AfterEach
    void restoreTheDefaultLocale() {
        Locale.setDefault(originalDefault);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "en", "es", "ca" })
    void everyLanguageCanBeLoaded(String language) {
        Translations translations = new Translations(Locale.of(language));

        assertThat(translations.get(Message.APP_TITLE)).isNotBlank();
    }

    @Test
    void aLanguageWithNoBundleOfItsOwnFallsBackToTheOneWithout() {
        // A student whose machine is set to German gets the English texts rather than a
        // half-translated window or a crash.
        Translations translations = new Translations(Locale.GERMAN);

        assertThat(translations.get(Message.MENU_HELP))
                .isEqualTo(new Translations(Locale.ENGLISH).get(Message.MENU_HELP));
    }

    @Test
    void eachLanguageSaysSomethingDifferent() {
        assertThat(new Translations(Locale.of("es")).get(Message.MENU_HELP))
                .isNotEqualTo(new Translations(Locale.of("ca")).get(Message.MENU_HELP));
    }

    @Test
    void aTextWithAPlaceholderIsFilledIn() {
        String message = new Translations(Locale.ENGLISH)
                .format(Message.DIALOG_DOCKER_MISSING_MESSAGE, "https://example.org");

        assertThat(message).contains("https://example.org").doesNotContain("{0}");
    }

    @Test
    void changingLanguageChangesEveryTextAtOnce() {
        Translations translations = new Translations(Locale.ENGLISH);
        String english = translations.get(Message.MENU_HELP);

        translations.setLocale(Locale.of("es"));

        assertThat(translations.get(Message.MENU_HELP)).isNotEqualTo(english);
    }

    @Test
    void changingLanguageAlsoChangesTheOneTheMachineReports() {
        // The language menu ticks its radio button from the default locale, and
        // anything
        // else that formats a date or a number follows it too.
        new Translations(Locale.ENGLISH).setLocale(Locale.of("ca"));

        assertThat(Locale.getDefault().getLanguage()).isEqualTo("ca");
    }

    @Test
    void everythingRegisteredIsAppliedAsSoonAsItIsRegistered() {
        // A menu registers itself and expects its text straight away, without waiting
        // for
        // the student to change language first.
        Translations translations = new Translations(Locale.ENGLISH);
        List<String> applied = new ArrayList<>();

        translations.register(() -> applied.add(translations.get(Message.MENU_HELP)));

        assertThat(applied).hasSize(1);
    }

    @Test
    void everythingRegisteredIsAppliedAgainOnEveryChange() {
        Translations translations = new Translations(Locale.ENGLISH);
        List<String> applied = new ArrayList<>();
        translations.register(() -> applied.add(translations.get(Message.MENU_HELP)));

        translations.setLocale(Locale.of("es"));
        translations.setLocale(Locale.of("ca"));

        assertThat(applied).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    void everythingRegisteredIsAppliedInTheOrderItWasRegistered() {
        Translations translations = new Translations(Locale.ENGLISH);
        List<String> order = new ArrayList<>();
        translations.register(() -> order.add("primero"));
        translations.register(() -> order.add("segundo"));
        order.clear();

        translations.setLocale(Locale.of("es"));

        assertThat(order).containsExactly("primero", "segundo");
    }

    @Test
    void aLanguageMissingATextIsRefusedRatherThanShownHalfTranslated() {
        // The guard exists so a bundle that falls behind the interface fails at once
        // and
        // says which text is missing, instead of throwing when a menu is first opened.
        assertThatThrownBy(() -> new Translations("i18n.incomplete", Locale.ENGLISH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("menu.help");
    }

    @Test
    void theRefusalNamesTheLanguageThatIsShort() {
        assertThatThrownBy(() -> new Translations("i18n.incomplete", Locale.ENGLISH))
                .hasMessageContaining("en");
    }

    @Test
    void aLanguageThatHasEverythingIsAccepted() {
        // The other side of the guard: a complete bundle must not be refused.
        assertThat(new Translations("i18n.messages", Locale.ENGLISH).get(Message.MENU_HELP))
                .isNotBlank();
    }
}
