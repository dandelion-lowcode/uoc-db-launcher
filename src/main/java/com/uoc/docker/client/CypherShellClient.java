package com.uoc.docker.client;

import java.util.List;

/** Neo4j, queried with cypher-shell. */
final class CypherShellClient implements DatabaseClient {

    @Override
    public List<String> command(String query) {
        return List.of("cypher-shell", "--format", "plain", query);
    }
}
