package com.uoc.ui.menu;

import java.util.Locale;
import java.util.prefs.Preferences;

import com.uoc.ui.menu.LanguageMenu.Language;

/**
 * Which language the launcher opens in, and where that is written down.
 *
 * <p>
 * The machine is asked once, on the very first run on it, and never again: from the
 * moment a student picks a language from the menu, that is the answer. A choice made and
 * then forgotten is worse than no choice at all -- a student who switches to Catalan and
 * finds Spanish again the next morning has no way to tell whether they clicked it.
 *
 * <p>
 * That first answer is Catalan for a machine set to Catalan, and Spanish for every other
 * machine, English included. The course is taught in Spanish and its notes are written in
 * it, so it is the wording a student is likeliest to want beside them; English is in the
 * menu for anyone who would rather have it, and is remembered like any other choice.
 */
public class LanguageManager {

    private static final String PREF_KEY = "language";

    private final Preferences prefs;

    public LanguageManager(Preferences prefs) {
        this.prefs = prefs;
    }

    /** The language to open in: the one chosen last time, or the first-run answer. */
    public Language selectedLanguage() {
        return remembered(prefs.get(PREF_KEY, null));
    }

    /** Remembers a choice, so the next session opens in it. */
    public void select(Language language) {
        prefs.put(PREF_KEY, language.locale().getLanguage());
    }

    /**
     * A saved language, or what to show when there is none to read.
     *
     * <p>
     * A code that names no language the launcher has is treated as no code at all rather
     * than as a failure: preferences outlive versions, and a language dropped between two
     * of them must not stop the application opening.
     */
    private static Language remembered(String saved) {
        if (saved == null || saved.isBlank()) {
            return firstRunLanguage(Locale.getDefault());
        }
        for (Language language : Language.values()) {
            if (language.locale().getLanguage().equals(saved.strip())) {
                return language;
            }
        }
        return firstRunLanguage(Locale.getDefault());
    }

    /**
     * What a machine nobody has chosen on yet opens in.
     *
     * @param machine the language the operating system is set to, asked only this once
     */
    static Language firstRunLanguage(Locale machine) {
        return Language.CATALAN.locale().getLanguage().equals(machine.getLanguage())
                ? Language.CATALAN
                : Language.SPANISH;
    }
}
