package com.uoc.docker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The examples an empty console shows are promises: it says the query "should answer"
 * this, so it had better.
 *
 * <p>
 * They were written by running each query against the pinned images and copying what came
 * back. This runs them again, so that an image moving to a new version, or a query being
 * reworded, cannot quietly leave the console telling students something untrue.
 *
 * <p>
 * Riak is the exception and says so in the bundle: its answer carries the time of day in
 * a header, which could never match twice, so only the parts that do not change are
 * checked.
 */
class ConsoleExampleAnswersIntegrationTest extends DockerIntegrationTestBase {

    private static final ResourceBundle EXAMPLES =
            ResourceBundle.getBundle("i18n.console-examples");

    @BeforeAll
    static void requireDocker() {
        assumeDockerIsRunning();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Database.class)
    void theExampleAnswerIsWhatTheDatabaseActuallyAnswers(Database database) {
        if (!database.hasQueryConsole()) {
            return;
        }
        startAndAwait(database);

        String query = EXAMPLES.getString(database.key() + ".query");
        String promised = EXAMPLES.getString(database.key() + ".result");
        String actual = stripColours(query(database, query));

        for (String line : promised.lines().toList()) {
            String expected = line.strip();
            // The line standing in for the headers left out of Riak's answer.
            if (expected.equals("...") || expected.isEmpty()) {
                continue;
            }
            assertThat(collapseSpaces(actual))
                    .as("%s was promised to answer \"%s\" to \"%s\"", database, expected, query)
                    .contains(collapseSpaces(expected));
        }
    }

    /** Clients colour their output; the promise is about the words, not the colours. */
    private static String stripColours(String text) {
        return text.replaceAll("\u001b\\[[0-9;]*m", "");
    }

    /**
     * Column alignment is the client's business and shifts with the width of what it is
     * printing, so runs of spaces are treated as one.
     */
    private static String collapseSpaces(String text) {
        return text.replaceAll("[ \\t]+", " ");
    }
}
