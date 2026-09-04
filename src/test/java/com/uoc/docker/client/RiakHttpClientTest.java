package com.uoc.docker.client;

import com.uoc.docker.Database;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiakHttpClientTest {

    private final RiakHttpClient client = new RiakHttpClient();

    private String stdin(String query) {
        return client.stdin(query).trim();
    }

    @Test
    void turnsABareUrlIntoAGet() {
        assertEquals("curl -sS -i http://localhost:8098/riak/prueba/k1",
                stdin("http://localhost:8098/riak/prueba/k1"));
    }

    @Test
    void ignoresSurroundingWhitespace() {
        assertEquals("curl -sS -i http://localhost:8098/riak/prueba/k1",
                stdin("   http://localhost:8098/riak/prueba/k1   "));
    }

    @Test
    void keepsAnExplicitCommandVerbatimApartFromTheQuietFlags() {
        String written = "-XPUT http://localhost:8098/riak/p/k -H \"Content-Type: application/json\" -d \"{\\\"a\\\":1}\"";
        assertEquals("curl -sS " + written, stdin("curl " + written));
    }

    @Test
    void alwaysAsksCurlToReportFailures() {
        // -s alone hides the reason a request could not be made, leaving the console blank.
        assertTrue(stdin("curl -i http://x").contains("-sS"));
        assertTrue(stdin("http://x").contains("-sS"));
    }

    @Test
    void endsTheCommandWithANewlineSoTheShellRunsIt() {
        assertTrue(client.stdin("http://x").endsWith("\n"));
    }

    @Test
    void runsAShellWithoutATerminal() {
        // The command arrives on standard input, which a pseudo-terminal would interfere with.
        assertEquals(List.of("sh"), client.command("anything"));
        assertFalse(client.usesTerminal());
    }

    @Test
    void colouringIsAppliedToTheResponse() {
        assertTrue(client.format("HTTP/1.1 200 OK\n\nhi").contains("HTTP/1.1 200 OK"));
        assertFalse(client.format("HTTP/1.1 200 OK\n\nhi").equals("HTTP/1.1 200 OK\n\nhi"));
    }

    @Test
    void onlyRiakUsesStandardInput() {
        for (Database database : Database.values()) {
            // Jupyter has no client at all: it is opened in a browser.
            if (database == Database.RIAK || !database.hasQueryConsole()) {
                continue;
            }
            DatabaseClient other = DatabaseClient.of(database);
            assertNull(other.stdin("query"), database + " should pass its query as arguments");
            assertTrue(other.usesTerminal(), database + " should keep its colours");
        }
    }
}
