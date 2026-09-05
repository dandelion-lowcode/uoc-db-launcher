package com.uoc.docker.client;

import java.util.List;

/**
 * ArangoDB, queried with arangosh against the local root account.
 *
 * <p>
 * arangosh speaks JavaScript, not AQL, so the query is handed to {@code db._query} and
 * the rows are printed as JSON. The whole thing goes in on standard input.
 *
 * <p>
 * Standard input rather than an argument, for the same reason Riak's client uses it: the
 * JDK rebuilds a single command line on Windows and mangles the double quotes this script
 * is full of. It was written to work around that by having the shell write the query to a
 * file inside the container and then read it back from JavaScript, which needed a shell,
 * a temporary file and two languages to say what one line says here.
 */
final class ArangoshClient implements DatabaseClient {

    private static final String ENDPOINT = "tcp://127.0.0.1:8529";
    private static final String USERNAME = "root";

    /**
     * The database the image restores the IMDB graph into, and the one the module tells
     * the student to pick after logging in. Without this arangosh opens {@code _system},
     * where imdb_vertices does not exist and the tutorial's first query answers nothing.
     */
    private static final String DATABASE = "IMDB";

    /**
     * The password the compose file sets for this container. Both have to agree, and
     * neither is a secret: the service listens on one student's own machine and holds a
     * copy of a public dataset.
     */
    private static final String PASSWORD = "rootpassword";

    @Override
    public List<String> command(String query) {
        return List.of("arangosh",
                "--console.history", "false",
                "--server.endpoint", ENDPOINT,
                "--server.username", USERNAME,
                "--server.password", PASSWORD,
                "--server.database", DATABASE,
                // Without this, every answer arrives under a banner of version numbers.
                "--quiet");
    }

    @Override
    public String stdin(String query) {
        return "print(JSON.stringify(db._query(" + asJavaScriptString(query) + ").toArray()));\n";
    }

    /**
     * The query as a JavaScript string literal.
     *
     * <p>
     * A student's AQL is full of the quotes and backslashes that would end the literal
     * early, and an unbalanced one turns a typo into a syntax error from a language they
     * are not writing.
     */
    private static String asJavaScriptString(String query) {
        StringBuilder literal = new StringBuilder("\"");
        for (char character : query.toCharArray()) {
            switch (character) {
                case '"' -> literal.append("\\\"");
                case '\\' -> literal.append("\\\\");
                case '\n' -> literal.append("\\n");
                case '\r' -> literal.append("\\r");
                case '\t' -> literal.append("\\t");
                default -> literal.append(character);
            }
        }
        return literal.append('"').toString();
    }

    /** A client reading its script from standard input must not be given a terminal. */
    @Override
    public boolean usesTerminal() {
        return false;
    }
}
