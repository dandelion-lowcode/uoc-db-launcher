package com.uoc.docker.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisCliClientTest {

    @Test
    void splitsOnWhitespace() {
        assertEquals(List.of("GET", "saludo"), RedisCliClient.tokenize("GET saludo"));
    }

    @Test
    void keepsDoubleQuotedValuesTogether() {
        assertEquals(List.of("SET", "saludo", "hola mundo"),
                RedisCliClient.tokenize("SET saludo \"hola mundo\""));
    }

    @Test
    void keepsSingleQuotedValuesTogether() {
        assertEquals(List.of("SET", "saludo", "hola mundo"),
                RedisCliClient.tokenize("SET saludo 'hola mundo'"));
    }

    @Test
    void collapsesRepeatedAndSurroundingWhitespace() {
        assertEquals(List.of("GET", "saludo"), RedisCliClient.tokenize("   GET    saludo   "));
    }

    @Test
    void keepsAnEmptyQuotedValueAsAnArgument() {
        assertEquals(List.of("SET", "k", ""), RedisCliClient.tokenize("SET k \"\""));
    }

    @Test
    void returnsNothingForAnEmptyQuery() {
        assertEquals(List.of(), RedisCliClient.tokenize("   "));
    }

    @Test
    void buildsTheClientCommand() {
        assertEquals(List.of("redis-cli", "GET", "saludo"), new RedisCliClient().command("GET saludo"));
    }
}
