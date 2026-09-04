package com.uoc.docker;

import com.uoc.docker.client.DatabaseClient;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs a query with the database's own client, inside its container. The
 * clients live in
 * the images, so the behaviour is identical whatever the host runs. What each
 * database
 * needs is decided by its {@link DatabaseClient}; this class assembles the
 * command and
 * hands the result back to the interface.
 */
public class QueryRunner {

    private static final String EXEC = "exec";
    private static final String WITH_TERMINAL = "-t";
    private static final String WITH_INPUT = "-i";

    private final ProcessRunner processRunner;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public QueryRunner() {
        this(new SystemProcessRunner());
    }

    /**
     * Takes the way commands are run as a dependency, so the query path can be exercised
     * without Docker, a container or a database.
     */
    public QueryRunner(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Runs the query away from the interface thread and delivers the result back on
     * it,
     * so the console stays responsive while a slow query runs.
     */
    public void run(String key, String query, Consumer<String> onResult) {
        executor.submit(() -> {
            String output = execute(key, query);
            SwingUtilities.invokeLater(() -> onResult.accept(output));
        });
    }

    /**
     * The whole query path with no threading: pick the client, run its command, and
     * give
     * the output the shape the console expects.
     */
    String execute(String key, String query) {
        Database database = Database.fromKey(key);
        DatabaseClient client = DatabaseClient.of(database);

        ProcessRunner.Result result = processRunner.run(command(database, client, query), client.stdin(query));
        return client.format(ConsoleOutput.normalize(result.output()));
    }

    private List<String> command(Database database, DatabaseClient client, String query) {
        List<String> command = new ArrayList<>();
        command.add(DockerCommand.EXECUTABLE);
        command.add(EXEC);
        command.add(client.usesTerminal() ? WITH_TERMINAL : WITH_INPUT);
        command.add(database.containerName());
        command.addAll(client.command(query));
        return command;
    }
}
