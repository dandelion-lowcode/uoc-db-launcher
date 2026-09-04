package com.uoc.docker.client;

import java.util.List;

/** MongoDB, queried with mongosh. */
final class MongoshClient implements DatabaseClient {

    @Override
    public List<String> command(String query) {
        return List.of("mongosh", "--quiet", "--eval", query);
    }
}
