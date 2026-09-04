package com.uoc.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the query path without Docker, by standing in for the process with
 * a fake
 * that records the command it was given and replies with whatever the test
 * wants.
 *
 * <p>
 * Partitions exercised here: every database (each has its own client), a query
 * that
 * needs standard input (Riak) against ones that do not, output that needs
 * cleaning up
 * against output that does not, and a database key that does not exist.
 */
class QueryRunnerTest {

    /**
     * A fake rather than a mock: it has a working implementation, it just does not
     * start
     * a process. Recording the command is what lets the test assert what would have
     * run.
     */
    private static final class RecordingProcessRunner implements ProcessRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private final List<String> inputs = new ArrayList<>();
        private String reply = "";
        private int exitCode = 0;

        @Override
        public Result run(List<String> command, String stdin) {
            commands.add(List.copyOf(command));
            inputs.add(stdin);
            return new Result(exitCode, reply);
        }

        List<String> lastCommand() {
            return commands.get(commands.size() - 1);
        }

        String lastInput() {
            return inputs.get(inputs.size() - 1);
        }
    }

    private final RecordingProcessRunner process = new RecordingProcessRunner();
    private final QueryRunner runner = new QueryRunner(process);

    @ParameterizedTest(name = "{0} runs {1} in its own container")
    @CsvSource({
            "mongo,     mongosh,       uocdb-mongo",
            "cassandra, cqlsh,         uocdb-cassandra",
            "neo4j,     cypher-shell,  uocdb-neo4j",
            "neo4j-twitter, cypher-shell, uocdb-neo4j-twitter",
            "redis,     redis-cli,     uocdb-redis",
            "riak,      sh,            uocdb-riak"
    })
    void eachDatabaseIsQueriedWithItsOwnClientInsideItsOwnContainer(
            String key, String client, String container) {
        runner.execute(key, "consulta");

        assertThat(process.lastCommand())
                .startsWith("docker", "exec")
                .contains(container)
                .contains(client);
    }

    @ParameterizedTest(name = "{0} gets a terminal")
    @ValueSource(strings = { "mongo", "cassandra", "neo4j", "neo4j-twitter", "redis" })
    void clientsThatPrintColoursGetAPseudoTerminal(String key) {
        runner.execute(key, "consulta");

        assertThat(process.lastCommand()).containsSequence("exec", "-t");
    }

    @Test
    void theShellThatReadsTheQueryFromInputGetsNoTerminal() {
        // Riak's command arrives on standard input, which a pseudo-terminal would
        // disturb.
        runner.execute("riak", "http://localhost:8098/riak/b/k");

        assertThat(process.lastCommand()).containsSequence("exec", "-i");
        assertThat(process.lastInput()).startsWith("curl -sS ");
    }

    @ParameterizedTest(name = "{0} passes its query as arguments")
    @ValueSource(strings = { "mongo", "cassandra", "neo4j", "neo4j-twitter", "redis" })
    void everyOtherDatabasePassesItsQueryAsArgumentsRatherThanOnInput(String key) {
        runner.execute(key, "consulta");

        assertThat(process.lastInput()).isNull();
    }

    @Test
    void theQueryReachesTheClientUnchanged() {
        String query = "db.libros.find({autor:\"Borges\"})";
        runner.execute("mongo", query);

        assertThat(process.lastCommand()).endsWith(query);
    }

    @Test
    void theOutputIsCleanedBeforeItReachesTheConsole() {
        process.reply = "linea uno\r\nlinea dos\r\n";

        assertThat(runner.execute("mongo", "consulta")).isEqualTo("linea uno\nlinea dos\n");
    }

    @Test
    void riakResponsesAreColoured() {
        process.reply = "HTTP/1.1 200 OK\r\n\r\n{\"a\":1}";

        String output = runner.execute("riak", "http://localhost:8098/riak/b/k");

        assertThat(output)
                .contains(String.valueOf((char) 27))
                .contains("HTTP/1.1 200 OK")
                .contains("{\"a\":1}");
    }

    @Test
    void everyOtherDatabaseKeepsTheColoursItsOwnClientPrinted() {
        String coloured = (char) 27 + "[32mok" + (char) 27 + "[0m";
        process.reply = coloured;

        assertThat(runner.execute("cassandra", "SELECT 1;")).isEqualTo(coloured);
    }

    @Test
    void anOutputThatSaysNothingStaysEmpty() {
        process.reply = "";

        assertThat(runner.execute("redis", "GET k")).isEmpty();
    }

    @ParameterizedTest(name = "{0} is not a database")
    @ValueSource(strings = { "postgres", "", " ", "mongo2", "mong" })
    void anUnknownDatabaseIsRejected(String key) {
        // The keys come from the interface, which only offers the five it built;
        // anything
        // else is a programming mistake and should stop rather than run a wrong
        // command.
        assertThatThrownBy(() -> runner.execute(key, "consulta"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(key);
    }

    @ParameterizedTest(name = "{0} still finds the database")
    @ValueSource(strings = { "MONGO", "Mongo", "mOnGo" })
    void aKeyIsMatchedWhateverItsCase(String key) {
        runner.execute(key, "consulta");

        assertThat(process.lastCommand()).contains("uocdb-mongo");
    }

    @Test
    void aMissingDatabaseKeyIsRejectedWithAnExplanation() {
        assertThatThrownBy(() -> runner.execute(null, "consulta"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
