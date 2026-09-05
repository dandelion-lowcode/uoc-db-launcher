package com.uoc.docker.client;

/**
 * Riak, which has no query shell of its own: the course teaches it through its REST API
 * with curl, so the typed command runs in a shell inside the container.
 */
final class RiakHttpClient extends CurlClient {

    /** A bare URL is accepted as a shorthand for a plain GET. */
    @Override
    protected String shorthand(String request) {
        return curl("-i " + request);
    }
}
