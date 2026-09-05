package com.uoc.ui;

import com.uoc.ansi.IAnsiColors;
import com.uoc.docker.Database;
import com.uoc.docker.DockerAvailability;
import com.uoc.docker.DockerManager;
import com.uoc.docker.QueryRunner;
import com.uoc.docker.ServiceStatus;
import com.uoc.i18n.Translations;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.AbstractButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What a student actually does, from beginning to end.
 *
 * <p>
 * Press start, wait for the console to become usable, then ask the database
 * several
 * things and read the answers. Nothing is stood in for: the containers are
 * real, the
 * status comes from Docker, and the queries go to the database through its own
 * client.
 *
 * <p>
 * The answers are checked for what they say, not merely for being there. Each
 * database
 * is also asked something it must refuse, because a student mistypes far more
 * often than
 * they type correctly, and an error that arrives blank or unreadable is worse
 * than no
 * error at all.
 *
 * <p>
 * Where a client prints in colour, the colour is checked as well: it is the
 * difference
 * between an error a student notices and one they scroll past.
 */
@Tag("integration")
@DisplayName("starting a database and using its console")
class ConsoleJourneyIntegrationTest {

    private static final Duration READY_LIMIT = Duration.ofMinutes(6);
    private static final Duration ANSWER_LIMIT = Duration.ofMinutes(2);

    /**
     * A query and something its answer must contain for the answer to make sense.
     */
    private record Question(String query, String expected) {
    }

    private static List<Question> questionsFor(Database database) {
        return switch (database) {
            case MONGO -> List.of(
                    new Question("db.runCommand({ping:1}).ok", "1"),
                    new Question("1 + 1", "2"),
                    new Question("db.getName()", "test"),
                    new Question("db.diario.insertOne({nota:'hola'}).acknowledged", "true"));
            case CASSANDRA -> List.of(
                    new Question("SELECT release_version FROM system.local;", "release_version"),
                    new Question("SELECT cluster_name FROM system.local;", "cluster_name"),
                    new Question("DESCRIBE KEYSPACES;", "system"),
                    new Question("SELECT key FROM system.local;", "(1 rows)"));
            case NEO4J -> List.of(
                    new Question("RETURN 1 AS uno", "uno"),
                    new Question("RETURN 'hola' AS saludo", "hola"),
                    new Question("RETURN 2 + 3 AS suma", "5"),
                    new Question("MATCH (n) RETURN count(n) AS total", "total"));
            case NEO4J_TWITTER -> List.of(
                    new Question("RETURN 1 AS uno", "uno"),
                    new Question("RETURN 'hola' AS saludo", "hola"),
                    new Question("RETURN 2 + 3 AS suma", "5"),
                    new Question("MATCH (n) RETURN count(n) AS total", "total"));
            case REDIS -> List.of(
                    new Question("PING", "PONG"),
                    new Question("SET diario hola", "OK"),
                    new Question("GET diario", "hola"),
                    // The count depends on what earlier runs left behind, so what is
                    // checked is that a count came back at all.
                    new Question("DBSIZE", "(integer)"));
            // Riak is asked over HTTP with -i, so the status line is shown. Without it a
            // successful write prints nothing, exactly as it does in a terminal.
            case RIAK -> List.of(
                    new Question("http://localhost:8098/ping", "200"),
                    new Question("curl -i -XPUT http://localhost:8098/riak/diario/uno -d hola", "204"),
                    new Question("http://localhost:8098/riak/diario/uno", "hola"),
                    new Question("http://localhost:8098/riak/diario/no-existe", "404"));
                case COCKROACHDB -> List.of(
                    new Question("SELECT 1;", "1"),
                    new Question("SELECT 'hola';", "hola"),
                    new Question("SHOW DATABASES;", "defaultdb"),
                    new Question("SELECT 2 + 3;", "5"));
                // Nothing is asked of a collection: ArangoDB starts empty and the
                // course's notebook is what fills it. The last of these used to ask for
                // the length of "posts", which is a collection that notebook creates, so
                // it could only ever have failed.
                case ARANGODB -> List.of(
                    new Question("RETURN 1", "1"),
                    new Question("RETURN 'hola'", "hola"),
                    new Question("RETURN 2 + 3", "5"),
                    new Question("FOR n IN 1..3 RETURN n * n", "[1,4,9]"));
                case VERTICA -> List.of(
                    new Question("SELECT 1;", "1"),
                    new Question("SELECT 'hola';", "hola"),
                    new Question("SELECT CURRENT_DATABASE();", "VMart"),
                    new Question("SELECT 2 + 3;", "5"));
                case ELASTICSEARCH -> List.of(
                    new Question("GET /", "200"),
                    new Question("GET /_cluster/health", "200"),
                    new Question("GET /_cat/indices", "200"),
                    new Question("GET /_nodes", "200"));
            // Filtered out before this is reached: Jupyter has no console to ask.
            case JUPYTER -> throw new IllegalArgumentException(
                    database.displayName() + " is not asked questions");
        };
    }

    /** Something each database must refuse, and what it should say about it. */
    private static Question badQueryFor(Database database) {
        return switch (database) {
            case MONGO -> new Question("db.coleccionQueNoExiste.find({", "SyntaxError");
            case CASSANDRA -> new Question("SELECT * FROM tabla_que_no_existe;", "Invalid");
            case NEO4J -> new Question("ESTO NO ES CYPHER", "Invalid input");
            case NEO4J_TWITTER -> new Question("ESTO NO ES CYPHER", "Invalid input");
            case REDIS -> new Question("COMANDO-INVENTADO", "unknown command");
            case RIAK -> new Question("http://localhost:9/no-hay-nadie", "curl:");
            case COCKROACHDB -> new Question("SELECT * FROM tabla_que_no_existe;", "does not exist");
            case ARANGODB -> new Question("RETURN ESTO NO ES AQL", "AQL: syntax error");
            case VERTICA -> new Question("SELECT * FROM tabla_que_no_existe;", "does not exist");
            case ELASTICSEARCH -> new Question("GET /_index_que_no_existe", "404");
            // Filtered out before this is reached: Jupyter has no console to ask.
            case JUPYTER -> throw new IllegalArgumentException(
                    database.displayName() + " is not asked questions");
        };
    }

    /**
     * Whether the client colours ordinary answers. Measured against the real
     * clients
     * rather than assumed: cypher-shell prints results plain and only marks
     * failures,
     * and redis-cli uses no colour at all.
     */
    private static boolean coloursItsAnswers(Database database) {
        return database == Database.MONGO
                || database == Database.CASSANDRA
                || database == Database.RIAK;
    }

    /** Whether the client marks its errors in colour, as opposed to plain text. */
    private static boolean coloursItsErrors(Database database) {
        return database != Database.REDIS;
    }

    private DockerManager manager;
    private DatabaseTab tab;
    private JTextArea input;
    private JTextComponent session;
    private AbstractButton send;

    @AfterEach
    void stopListening() {
        if (manager != null) {
            manager.close();
        }
    }

    /**
     * The services a query can be typed at. Jupyter is driven from a browser, so there is
     * no console for this journey to walk through.
     */
    static java.util.stream.Stream<Database> queryableDatabases() {
        return java.util.Arrays.stream(Database.values()).filter(Database::hasQueryConsole);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("queryableDatabases")
    void aStudentStartsTheServiceThenAsksItSeveralThings(Database database) throws Exception {
        assumeTrue(DockerAvailability.isRunning(), "Docker is not running");

        buildTheConsoleFor(database);
        manager.setListener((key, status) -> {
            if (key.equals(database.key())) {
                SwingUtilities.invokeLater(() -> tab.setSendEnabled(status == ServiceStatus.HEALTHY));
            }
        });
        manager.start();

        manager.start(database.key());
        awaitConsoleReady(database);

        List<Question> questions = questionsFor(database);
        for (Question question : questions) {
            String answer = ask(question.query());

            assertThat(answer)
                    .as("%s answered nothing to <%s>", database.displayName(), question.query())
                    .isNotBlank();
            assertThat(stripColours(answer))
                    .as("%s did not answer <%s> with anything that makes sense",
                            database.displayName(), question.query())
                    .contains(question.expected());
        }

        // Every question and its answer are still there to read back.
        String transcript = stripColours(onSwing(session::getText));
        for (Question question : questions) {
            assertThat(transcript)
                    .as("the session lost the question <%s>", question.query())
                    .contains("> " + question.query());
        }

        if (coloursItsAnswers(database)) {
            assertThat(coloursOnScreen())
                    .as("%s colours its answers but nothing on screen is coloured",
                            database.displayName())
                    .isNotEmpty();
        }

        assertThat(onSwing(send::isEnabled))
                .as("the console must be ready for the next query")
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("queryableDatabases")
    void aQueryThatTheDatabaseRefusesIsReportedAndLeavesTheConsoleUsable(Database database)
            throws Exception {
        assumeTrue(DockerAvailability.isRunning(), "Docker is not running");

        buildTheConsoleFor(database);
        manager.setListener((key, status) -> {
            if (key.equals(database.key())) {
                SwingUtilities.invokeLater(() -> tab.setSendEnabled(status == ServiceStatus.HEALTHY));
            }
        });
        manager.start();
        manager.start(database.key());
        awaitConsoleReady(database);

        Question bad = badQueryFor(database);
        String answer = ask(bad.query());

        assertThat(answer)
                .as("%s said nothing about <%s>", database.displayName(), bad.query())
                .isNotBlank();
        assertThat(stripColours(answer))
                .as("%s did not explain what was wrong with <%s>",
                        database.displayName(), bad.query())
                .contains(bad.expected());

        if (coloursItsErrors(database)) {
            assertThat(coloursOnScreen())
                    .as("%s marks its errors in colour, but the console shows none",
                            database.displayName())
                    .anyMatch(ConsoleJourneyIntegrationTest::looksLikeAWarning);
        }

        // A refused query is an everyday event, not the end of the session: the student
        // has to be able to correct it and try again.
        String recovery = ask(questionsFor(database).get(0).query());
        assertThat(stripColours(recovery))
                .as("%s could not answer a good query after a bad one", database.displayName())
                .contains(questionsFor(database).get(0).expected());
        assertThat(onSwing(send::isEnabled)).isTrue();
    }

    /** Red or yellow in the current palette, which is how a problem is marked. */
    private static boolean looksLikeAWarning(Color colour) {
        // Whatever theme is installed, read from the look and feel exactly as the console
        // itself reads it.
        IAnsiColors palette = new com.uoc.ansi.ThemeAnsiColors();
        for (com.uoc.ansi.AnsiColor warning : new com.uoc.ansi.AnsiColor[] {
                com.uoc.ansi.AnsiColor.RED, com.uoc.ansi.AnsiColor.BRIGHT_RED,
                com.uoc.ansi.AnsiColor.YELLOW, com.uoc.ansi.AnsiColor.BRIGHT_YELLOW }) {
            if (colour.equals(palette.of(warning))) {
                return true;
            }
        }
        return false;
    }

    /** Every colour actually painted in the console. */
    private Set<Color> coloursOnScreen() throws Exception {
        return onSwing(() -> {
            Set<Color> colours = new LinkedHashSet<>();
            StyledDocument document = (StyledDocument) session.getDocument();
            Element root = document.getDefaultRootElement();
            for (int paragraph = 0; paragraph < root.getElementCount(); paragraph++) {
                Element line = root.getElement(paragraph);
                for (int run = 0; run < line.getElementCount(); run++) {
                    AttributeSet attributes = line.getElement(run).getAttributes();
                    if (attributes.isDefined(StyleConstants.Foreground)) {
                        colours.add(StyleConstants.getForeground(attributes));
                    }
                }
            }
            return colours;
        });
    }

    private static String stripColours(String text) {
        return text.replaceAll((char) 27 + "\\[[0-9;]*m", "");
    }

    private void buildTheConsoleFor(Database database) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            tab = new DatabaseTab(database, new QueryRunner());
            tab.registerTranslations(new Translations(Locale.ENGLISH));
        });
        input = (JTextArea) find(DatabaseTab.INPUT);
        session = (JTextComponent) find(DatabaseTab.SESSION);
        send = (AbstractButton) find(DatabaseTab.SEND);
        manager = new DockerManager();
    }

    private void awaitConsoleReady(Database database) throws Exception {
        Instant deadline = Instant.now().plus(READY_LIMIT);
        while (Instant.now().isBefore(deadline)) {
            if (onSwing(send::isEnabled)) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError(database.displayName()
                + " never became ready to take a query within " + READY_LIMIT);
    }

    /** Types a query, sends it the way the button does, and returns the answer. */
    private String ask(String query) throws Exception {
        int before = onSwing(() -> session.getText().length());

        SwingUtilities.invokeAndWait(() -> {
            input.setText(query);
            send.doClick();
        });

        Instant deadline = Instant.now().plus(ANSWER_LIMIT);
        while (Instant.now().isBefore(deadline)) {
            // The console reopens only once the answer has been printed.
            if (onSwing(send::isEnabled)) {
                String transcript = onSwing(session::getText);
                return transcript.substring(before + ("> " + query + "\n").length());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no answer to <" + query + "> within " + ANSWER_LIMIT);
    }

    private <T> T onSwing(java.util.concurrent.Callable<T> action) throws Exception {
        List<Object> result = new ArrayList<>();
        List<Exception> failure = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.add(action.call());
            } catch (Exception e) {
                failure.add(e);
            }
        });
        if (!failure.isEmpty()) {
            throw failure.get(0);
        }
        @SuppressWarnings("unchecked")
        T value = (T) result.get(0);
        return value;
    }

    private Component find(String name) {
        Component found = find(tab.getPanel(), name);
        assertThat(found).as("no component named %s", name).isNotNull();
        return found;
    }

    private static Component find(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container inner) {
                Component found = find(inner, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
