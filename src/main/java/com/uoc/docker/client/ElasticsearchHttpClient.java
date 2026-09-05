package com.uoc.docker.client;

import java.util.List;

/** Elasticsearch, queried through curl commands or shorthand HTTP requests. */
final class ElasticsearchHttpClient implements DatabaseClient {

    private static final String CURL = "curl";
    private static final String QUIET_FLAGS = " -sS ";
    private static final String BASE_URL = "http://localhost:9200";

    @Override
    public List<String> command(String query) {
        return List.of("sh");
    }

    @Override
    public String stdin(String query) {
        String trimmed = query.trim();
        String command = trimmed.startsWith(CURL)
                ? CURL + QUIET_FLAGS + trimmed.substring(CURL.length()).trim()
                : shorthand(trimmed);
        return command + "\n";
    }

    @Override
    public boolean usesTerminal() {
        return false;
    }

    @Override
    public String format(String output) {
        return HttpResponseHighlighter.highlight(output);
    }

    /** The verbs a request may begin with. Anything else is the start of a path. */
    private static final java.util.Set<String> METHODS =
            java.util.Set.of("GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS");

    private static final String DEFAULT_METHOD = "GET";

    /**
     * Turns "GET /_cluster/health", or just "/_cluster/health", into a curl command.
     *
     * <p>
     * The first word is only taken as the method when it actually is one. Split blindly
     * on the first space, a student who typed a path on its own had it uppercased and
     * passed to curl as the verb: {@code -X /_CLUSTER/HEALTH}, which curl sends and
     * Elasticsearch refuses, over a request that was perfectly reasonable to type.
     */
    private String shorthand(String request) {
        int space = request.indexOf(' ');
        String head = space < 0 ? request : request.substring(0, space);
        boolean startsWithAMethod = METHODS.contains(head.toUpperCase(java.util.Locale.ROOT));

        String method = startsWithAMethod ? head.toUpperCase(java.util.Locale.ROOT) : DEFAULT_METHOD;
        String path = startsWithAMethod
                ? (space < 0 ? "/" : request.substring(space + 1).strip())
                : request;
        if (path.isEmpty()) {
            path = "/";
        }

        String url = path.startsWith("http://") || path.startsWith("https://")
                ? path
                : BASE_URL + (path.startsWith("/") ? path : "/" + path);
        return CURL + QUIET_FLAGS + "-i -X " + method + " " + url;
    }
}