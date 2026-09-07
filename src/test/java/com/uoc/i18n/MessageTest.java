package com.uoc.i18n;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTest {

    private static final List<Locale> LOCALES = List.of(Locale.ENGLISH, Locale.of("es"), Locale.of("ca"));

    @Test
    void everyLanguageDefinesExactlyTheDeclaredMessages() {
        Set<String> declared = new TreeSet<>();
        for (Message message : Message.values()) {
            declared.add(message.key());
        }

        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
            assertEquals(declared, new TreeSet<>(bundle.keySet()),
                    "bundle for " + locale + " does not match the declared messages");
        }
    }

    @Test
    void onlyTheTextsDrawnAsHtmlUseHtmlEscapes() {
        // An escape is decoded by the HTML renderer and by nothing else, so a plain
        // label or a button carrying one prints it exactly as written: a student saw
        // "Copia la connexi&oacute;" on a button. Accented characters go in as
        // themselves; the bundles are read as UTF-8.
        for (Locale locale : LOCALES) {
            Translations translations = new Translations(locale);
            for (Message message : Message.values()) {
                String text = translations.get(message);
                if (text.startsWith("<html>")) {
                    continue;
                }
                assertTrue(!text.matches("(?s).*&[a-zA-Z]+;.*"),
                        message.key() + " in " + locale + " is not drawn as HTML, so \""
                                + text + "\" would show its escapes to the student");
            }
        }
    }

    @Test
    void noLanguageLeavesATextEmpty() {
        for (Locale locale : LOCALES) {
            Translations translations = new Translations(locale);
            for (Message message : Message.values()) {
                assertTrue(!translations.get(message).isBlank(),
                        message.key() + " is empty in " + locale);
            }
        }
    }

    @Test
    void keysAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Message message : Message.values()) {
            assertTrue(seen.add(message.key()), "duplicate key " + message.key());
        }
    }
}
