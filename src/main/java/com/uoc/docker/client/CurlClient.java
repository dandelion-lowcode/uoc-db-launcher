package com.uoc.docker.client;

import java.util.List;

/**
 * The two databases the course teaches through their REST API, which have no query shell
 * of their own: what a student types is turned into a curl command and run by a shell
 * inside the container.
 *
 * <p>
 * Everything about that is the same for both. All either one decides is what to make of a
 * query that is not already a curl command, which is what {@link #shorthand} is for.
 */
sealed abstract class CurlClient implements DatabaseClient
        permits RiakHttpClient, ElasticsearchHttpClient {

    private static final String SHELL = "sh";
    private static final String CURL = "curl";

    // Quiet about progress, but never about failures: -S keeps the error message that -s
    // would swallow, and without -s the progress meter is printed over the same stream as
    // the errors, leaving them buried at the end of a line of counters.
    private static final String QUIET_FLAGS = " -sS ";

    @Override
    public final List<String> command(String query) {
        return List.of(SHELL);
    }

    /**
     * The command travels through standard input rather than the argument list because
     * the JDK rebuilds a single command line on Windows and mangles the double quotes
     * that JSON payloads need.
     */
    @Override
    public final String stdin(String query) {
        String trimmed = query.trim();
        // A command written out as curl is run as written, so anything the shorthand does
        // not cover -- a body, a header -- can always be typed in full.
        String command = trimmed.startsWith(CURL)
                ? curl(trimmed.substring(CURL.length()).trim())
                : shorthand(trimmed);
        return command + "\n";
    }

    /** A shell reading a command from standard input must not be given a terminal. */
    @Override
    public final boolean usesTerminal() {
        return false;
    }

    /**
     * These answers arrive as bare HTTP over a shell, with no interactive client of their
     * own to colour them, so the colours the other consoles print are added here.
     */
    @Override
    public final String format(String output) {
        return HttpResponseHighlighter.highlight(output);
    }

    /** What this database makes of a query that is not already a curl command. */
    protected abstract String shorthand(String request);

    /** A curl command carrying the quiet flags, whatever else is asked of it. */
    protected static String curl(String arguments) {
        return CURL + QUIET_FLAGS + arguments;
    }
}
