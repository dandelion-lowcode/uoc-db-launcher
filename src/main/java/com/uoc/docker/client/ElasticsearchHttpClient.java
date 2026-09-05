package com.uoc.docker.client;

import java.util.Locale;
import java.util.Set;

/** Elasticsearch, queried through curl commands or shorthand HTTP requests. */
final class ElasticsearchHttpClient extends CurlClient {

    private static final String BASE_URL = "http://localhost:9200";

    /** The verbs a request may begin with. Anything else is the start of a path. */
    private static final Set<String> METHODS =
            Set.of("GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS");

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
    @Override
    protected String shorthand(String request) {
        int space = request.indexOf(' ');
        String head = space < 0 ? request : request.substring(0, space);
        boolean startsWithAMethod = METHODS.contains(head.toUpperCase(Locale.ROOT));

        String method = startsWithAMethod ? head.toUpperCase(Locale.ROOT) : DEFAULT_METHOD;
        String path = startsWithAMethod
                ? (space < 0 ? "/" : request.substring(space + 1).strip())
                : request;
        if (path.isEmpty()) {
            path = "/";
        }

        String url = path.startsWith("http://") || path.startsWith("https://")
                ? path
                : BASE_URL + (path.startsWith("/") ? path : "/" + path);
        return curl("-i -X " + method + " " + url);
    }
}
