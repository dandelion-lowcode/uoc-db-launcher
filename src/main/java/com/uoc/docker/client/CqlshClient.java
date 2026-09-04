package com.uoc.docker.client;

import java.util.List;

/** Cassandra, queried with cqlsh. */
final class CqlshClient implements DatabaseClient {

    @Override
    public List<String> command(String query) {
        return List.of("cqlsh", "-e", query);
    }
}
