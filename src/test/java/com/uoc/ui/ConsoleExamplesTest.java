package com.uoc.ui;

import com.uoc.docker.Database;
import com.uoc.i18n.Translations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The worked example an empty trace shows.
 *
 * <p>
 * What cannot be checked here is whether an answer is the one the database really gives:
 * that was established by running each query through the launcher against the pinned
 * images, and {@code ConsoleQueryExamplesIntegrationTest} runs them again against real
 * containers. What is checked here is that every database has one, and that the text is
 * put together the way it is meant to read.
 */
class ConsoleExamplesTest {

    private static final Translations SPANISH = new Translations(Locale.of("es"));

    @ParameterizedTest(name = "{0}")
    @EnumSource(Database.class)
    void everyDatabaseWithAConsoleHasAQueryAndAnAnswerToShow(Database database) {
        if (!database.hasQueryConsole()) {
            return;
        }
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.console-examples");

        assertThat(bundle.getString(database.key() + ".query")).isNotBlank();
        assertThat(bundle.getString(database.key() + ".result")).isNotBlank();
    }

    @Test
    void aNotebookNeedsNoExampleBecauseItHasNoConsole() {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.console-examples");

        assertThat(bundle.containsKey(Database.JUPYTER.key() + ".query")).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Database.class)
    void theExampleShownIsTheOneForThisDatabase(Database database) {
        if (!database.hasQueryConsole()) {
            return;
        }
        String query = ResourceBundle.getBundle("i18n.console-examples")
                .getString(database.key() + ".query");

        assertThat(ConsoleExamples.placeholderFor(database, SPANISH)).contains(query);
    }

    @Test
    void theWholeThingReadsAsEmptyThenAQuestionThenAnAnswer() {
        String text = ConsoleExamples.placeholderFor(Database.NEO4J, SPANISH);
        String plain = text.replaceAll("\u001b\\[[0-9;]*m", "");

        assertThat(plain).startsWith("(Vac\u00edo)");
        assertThat(plain.indexOf("Por ejemplo"))
                .isLessThan(plain.indexOf("MATCH (n) RETURN COUNT(n);"));
        assertThat(plain.indexOf("MATCH (n) RETURN COUNT(n);"))
                .isLessThan(plain.indexOf("Debe responder"));
        assertThat(plain.indexOf("Debe responder")).isLessThan(plain.indexOf("COUNT(n)\n"));
    }

    @Test
    void theWordEmptyIsGreyAndBothExamplesAreBlue() {
        String text = ConsoleExamples.placeholderFor(Database.REDIS, SPANISH);

        assertThat(text).contains("\u001b[90m(Vac\u00edo)");
        assertThat(text).contains("\u001b[34m    SET a \"42\"");
        assertThat(text).contains("\u001b[34m    OK");
    }

    @Test
    void everyLineOfALongAnswerIsIndented() {
        // Cassandra's answer runs to two lines, and half an indent would read as though
        // the second line were something else.
        String text = ConsoleExamples.placeholderFor(Database.CASSANDRA, SPANISH);
        String plain = text.replaceAll("\u001b\\[[0-9;]*m", "");

        assertThat(plain.lines().filter(line -> line.contains("system_schema")))
                .allSatisfy(line -> assertThat(line).startsWith("    "));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Database.class)
    void theWordingFollowsTheLanguageWhileTheQueryDoesNot(Database database) {
        if (!database.hasQueryConsole()) {
            return;
        }
        String query = ResourceBundle.getBundle("i18n.console-examples")
                .getString(database.key() + ".query");

        String catalan = ConsoleExamples.placeholderFor(database, new Translations(Locale.of("ca")));
        String english = ConsoleExamples.placeholderFor(database, new Translations(Locale.ENGLISH));

        assertThat(catalan).contains("(Buit)").contains(query);
        assertThat(english).contains("(Empty)").contains(query);
    }

    @Test
    void everyExampleInTheBundleBelongsToADatabaseThatExists() {
        // A key left behind after a database is renamed would never be shown and nobody
        // would notice.
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.console-examples");

        for (String key : java.util.Collections.list(bundle.getKeys())) {
            String database = key.substring(0, key.lastIndexOf('.'));
            assertThat(Database.fromKey(database)).as("%s names no database", key).isNotNull();
        }
    }

    @Test
    void adatabaseWithNoExampleFallsBackToTheWordingAlone() {
        // Missing writing is a gap worth fixing, not something worth stopping a console
        // from opening over.
        try {
            ResourceBundle.getBundle("i18n.console-examples").getString("no-existe.query");
        } catch (MissingResourceException expected) {
            assertThat(ConsoleExamples.placeholderFor(Database.JUPYTER, SPANISH))
                    .contains("(Vac\u00edo)");
        }
    }
}
