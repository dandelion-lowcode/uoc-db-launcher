package com.uoc.docker.client;

import java.util.List;

/** CockroachDB, queried with its SQL shell in insecure single-node mode. */
final class CockroachSqlClient implements DatabaseClient {

    @Override
    public List<String> command(String query) {
        return List.of("cockroach", "sql", "--insecure", "--host=localhost:26257", "-e", query);
    }
}