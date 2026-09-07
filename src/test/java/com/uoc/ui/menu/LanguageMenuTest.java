package com.uoc.ui.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.uoc.i18n.Translations;
import com.uoc.ui.menu.LanguageMenu.Language;

@DisplayName("the language menu")
class LanguageMenuTest {

    private Preferences preferences;
    private LanguageManager languages;
    private Translations translations;

    /**
     * Changing the language sets the whole runtime's default, which is how a dialog the
     * launcher did not build comes out in the right language. It has to be put back, or
     * the tests that run after this class see a machine that has changed language.
     */
    private Locale machineLanguage;

    @BeforeEach
    void aMenuOnAMachineNobodyHasChosenOn() throws Exception {
        machineLanguage = Locale.getDefault();
        preferences = Preferences.userRoot().node("com/uoc/test/languageMenu");
        preferences.clear();
        languages = new LanguageManager(preferences);
        translations = new Translations(Locale.ENGLISH);
    }

    @AfterEach
    void leaveNothingBehind() throws Exception {
        Locale.setDefault(machineLanguage);
        preferences.removeNode();
    }

    @Test
    void itOffersTheLanguagesInTheOrderTheCourseUsesThem() {
        // Catalan and Spanish are the languages of the course; English is there for
        // anyone else who asks for it, so it comes last.
        assertThat(Language.values())
                .containsExactly(Language.CATALAN, Language.SPANISH, Language.ENGLISH);
    }

    @Test
    void theOneTickedIsTheOneTheSessionOpenedIn() {
        languages.select(Language.ENGLISH);

        JMenu menu = LanguageMenu.build(translations, new LanguageManager(preferences));

        assertThat(ticked(menu)).isEqualTo(translations.get(Language.ENGLISH.message()));
    }

    @Test
    void pickingOneRedrawsTheWindowInIt() {
        JMenu menu = LanguageMenu.build(translations, languages);

        itemFor(menu, Language.CATALAN).doClick();

        assertThat(translations.locale().getLanguage()).isEqualTo("ca");
    }

    @Test
    void pickingOneRemembersItForNextTime() {
        // What a student picks is theirs from then on: the machine's own language is
        // asked once, on the first run, and never again.
        JMenu menu = LanguageMenu.build(translations, languages);

        itemFor(menu, Language.CATALAN).doClick();

        assertThat(new LanguageManager(preferences).selectedLanguage())
                .isEqualTo(Language.CATALAN);
    }

    @Test
    void everyLanguageInTheMenuCanBePickedAndIsRemembered() {
        JMenu menu = LanguageMenu.build(translations, languages);

        for (Language language : Language.values()) {
            itemFor(menu, language).doClick();

            assertThat(new LanguageManager(preferences).selectedLanguage())
                    .as("%s", language).isEqualTo(language);
        }
    }

    @Test
    void onlyOneIsEverTicked() {
        JMenu menu = LanguageMenu.build(translations, languages);

        itemFor(menu, Language.ENGLISH).doClick();

        assertThat(items(menu)).filteredOn(JRadioButtonMenuItem::isSelected).hasSize(1);
    }

    private static List<JRadioButtonMenuItem> items(JMenu menu) {
        List<JRadioButtonMenuItem> found = new ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            found.add((JRadioButtonMenuItem) menu.getItem(i));
        }
        return found;
    }

    /** Found by its wording, which is the only thing that names an item from outside. */
    private JRadioButtonMenuItem itemFor(JMenu menu, Language language) {
        String name = translations.get(language.message());
        return items(menu).stream().filter(item -> name.equals(item.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("no item for " + language));
    }

    private static String ticked(JMenu menu) {
        return items(menu).stream().filter(JRadioButtonMenuItem::isSelected)
                .map(JRadioButtonMenuItem::getText).findFirst().orElse(null);
    }
}
