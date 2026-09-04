package com.uoc.docker.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis, queried with redis-cli. Unlike the other clients it takes the query already
 * split into arguments rather than as a single string.
 */
final class RedisCliClient implements DatabaseClient {

    @Override
    public List<String> command(String query) {
        List<String> command = new ArrayList<>();
        command.add("redis-cli");
        command.addAll(tokenize(query));
        return command;
    }

    /**
     * redis-cli prints in no colour at all, so a refused command looks the same as a
     * result. The mark is added here, the way the other consoles already show one.
     */
    @Override
    public String format(String output) {
        return RedisOutputHighlighter.highlight(output);
    }

    // Splits on whitespace but keeps quoted values together, so a command such as
    // SET greeting "hello world" reaches the client as a single argument.
    static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inToken = false;
        char quote = 0;

        for (char c : query.trim().toCharArray()) {
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }

        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
