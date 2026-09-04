package com.uoc.docker.client;

import java.util.List;

/**
 * Riak, which has no query shell of its own: the course teaches it through its REST API
 * with curl, so the typed command runs in a shell inside the container.
 */
final class RiakHttpClient implements DatabaseClient {

    private static final String SHELL = "sh";
    private static final String CURL = "curl";
    // Quiet about progress, but never about failures: -S keeps the error message that -s
    // would swallow, and without -s the progress meter is printed over the same stream as
    // the errors, leaving them buried at the end of a line of counters.
    private static final String QUIET_FLAGS = " -sS ";
    private static final String DEFAULT_FLAGS = CURL + QUIET_FLAGS + "-i ";

    @Override
    public List<String> command(String query) {
        return List.of(SHELL);
    }

    /**
     * The command travels through standard input rather than the argument list because
     * the JDK rebuilds a single command line on Windows and mangles the double quotes
     * that JSON payloads need.
     */
    @Override
    public String stdin(String query) {
        String trimmed = query.trim();
        // A bare URL is accepted as a shorthand for a plain GET.
        String command = trimmed.startsWith(CURL)
                ? silenceProgress(trimmed)
                : DEFAULT_FLAGS + trimmed;
        return command + "\n";
    }

    /** A shell reading a command from standard input must not be given a terminal. */
    @Override
    public boolean usesTerminal() {
        return false;
    }

    /** Riak answers over HTTP, so the colours the other clients print are added here. */
    @Override
    public String format(String output) {
        return HttpResponseHighlighter.highlight(output);
    }

    private String silenceProgress(String curlCommand) {
        String arguments = curlCommand.substring(CURL.length()).trim();
        return CURL + QUIET_FLAGS + arguments;
    }
}
