package com.uoc.i18n;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class Translations {

    private static final String BUNDLE = "i18n.messages";

    private static final ResourceBundle.Control NO_LOCALE_FALLBACK = ResourceBundle.Control
            .getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

    private final String bundleName;
    private Locale locale;
    private ResourceBundle bundle;
    private final List<Runnable> listeners = new ArrayList<>();

    public Translations(Locale locale) {
        this(BUNDLE, locale);
    }

    /**
     * Lets a test point at a bundle of its own to check what happens to a broken
     * one.
     */
    Translations(String bundleName, Locale locale) {
        this.bundleName = bundleName;
        this.locale = locale;
        this.bundle = load(bundleName, locale);
    }

    public String get(Message message) {
        return bundle.getString(message.key());
    }

    public String format(Message message, Object... arguments) {
        return MessageFormat.format(bundle.getString(message.key()), arguments);
    }

    public void register(Runnable applier) {
        listeners.add(applier);
        applier.run();
    }

    public void setLocale(Locale locale) {
        Locale.setDefault(locale);
        this.locale = locale;
        bundle = load(bundleName, locale);
        listeners.forEach(Runnable::run);
    }

    public Locale locale() {
        return locale;
    }

    /**
     * Loads a language and refuses one that cannot supply every text the interface
     * asks for, rather than failing later with a half-translated window.
     */
    private static ResourceBundle load(String bundleName, Locale locale) {
        // Without this, asking for a language with no bundle of its own falls back to
        // whatever the machine is set to before falling back to the texts without a
        // suffix, so choosing English on a Spanish machine would have shown Spanish.
        ResourceBundle loaded = ResourceBundle.getBundle(bundleName, locale, NO_LOCALE_FALLBACK);
        List<String> missing = new ArrayList<>();
        for (Message message : Message.values()) {
            if (!loaded.containsKey(message.key())) {
                missing.add(message.key());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing translations for " + locale + ": " + String.join(", ", missing));
        }
        return loaded;
    }
}
