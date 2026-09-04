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
