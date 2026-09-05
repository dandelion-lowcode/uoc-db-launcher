package com.uoc.ui;

import com.uoc.ansi.AnsiEscCode;
import com.uoc.docker.Database;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * What an empty trace shows instead of nothing.
 *
 * <p>
 * The six databases here are each driven in a different language, and an empty box says
 * nothing about which one this tab expects. A worked example -- a query and the answer it
 * gives -- says it in two lines.
 *
 * <p>
 * The examples themselves are not translated. A Cypher query is the same sentence in
 * every language, and so is what the database answers back, so they are kept as data
 * beside the wording rather than repeated in each bundle.
 */
public final class ConsoleExamples {

    private static final String BUNDLE = "i18n.console-examples";
    private static final String QUERY_SUFFIX = ".query";
    private static final String RESULT_SUFFIX = ".result";

    /** One tab in, which is how the example is set off from the sentences around it. */
    private static final String INDENT = "    ";

    private ConsoleExamples() {
    }

    /**
     * The whole of what an empty trace shows for one database.
     *
     * @return text with ANSI colours in it, ready for the console to render
     */
    public static String placeholderFor(Database database, Translations translations) {
        StringBuilder text = new StringBuilder();
        text.append(grey(translations.get(Message.CONSOLE_EMPTY))).append("\n\n");
        text.append(translations.get(Message.CONSOLE_EMPTY_INTRO)).append("\n\n");
        text.append(blue(indented(exampleOf(database, QUERY_SUFFIX)))).append("\n\n");
        text.append(translations.get(Message.CONSOLE_EMPTY_ANSWER)).append("\n\n");
        text.append(blue(indented(exampleOf(database, RESULT_SUFFIX)))).append("\n");
        return text.toString();
    }

    /**
     * The example for a database, or an empty string when it has none. A database with
     * nothing to show falls back to the sentences alone rather than failing: an example
     * missing is a gap in the writing, not something worth stopping the console for.
     */
    private static String exampleOf(Database database, String suffix) {
        try {
            return ResourceBundle.getBundle(BUNDLE).getString(database.key() + suffix);
        } catch (MissingResourceException e) {
            return "";
        }
    }

    private static String indented(String text) {
        return text.lines().map(line -> INDENT + line)
                .reduce((one, other) -> one + "\n" + other).orElse("");
    }

    private static String grey(String text) {
        return AnsiEscCode.BRIGHT_BLACK.escCode + text + AnsiEscCode.RESET.escCode;
    }

    private static String blue(String text) {
        return AnsiEscCode.BLUE.escCode + text + AnsiEscCode.RESET.escCode;
    }
}
