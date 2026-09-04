package com.uoc.docker.client;

import com.uoc.docker.Database;

import java.util.List;

/**
 * How one database is queried: the client program to run inside its container,
 * what it
 * needs on standard input, and how its output is presented.
 *
 * <p>
 * Everything specific to a database lives in a single implementation, so
 * reading one
 * of them tells the whole story and adding another does not mean revisiting a
 * chain of
 * conditionals spread over the runner.
 *
 * <p>
 * The interface is sealed because the set of clients is closed and mirrors the
 * fixed
 * set of databases the course uses: there is exactly one implementation per
 * constant of
 * {@link Database}, and nothing outside this package may add another.
 */
public sealed interface DatabaseClient
        permits MongoshClient, CqlshClient, CypherShellClient, RedisCliClient, RiakHttpClient {

    /** The client program and its arguments, appended after the container name. */
    List<String> command(String query);

    /**
     * What to write to the process input, or {@code null} when the query travels in
     * the
     * argument list.
     */
    default String stdin(String query) {
        return null;
    }

    /**
     * Whether to allocate a pseudo-terminal, which is what makes the interactive
     * clients
     * emit their usual colours. Clients fed through standard input must not have
     * one.
     */
    default boolean usesTerminal() {
        return true;
    }

    /** A chance to decorate the output before it reaches the console. */
    default String format(String output) {
        return output;
    }

    static DatabaseClient of(Database database) {
        return switch (database) {
            case MONGO -> new MongoshClient();
            case CASSANDRA -> new CqlshClient();
            case NEO4J, NEO4J_TWITTER -> new CypherShellClient();
            case REDIS -> new RedisCliClient();
            case RIAK -> new RiakHttpClient();
            // Jupyter is worked on in a browser; nothing here can drive it, and asking
            // for a client is a mistake in the caller rather than something to guess at.
            case JUPYTER -> throw new IllegalArgumentException(
                    database.displayName() + " is not queried from a console");
        };
    }
}
