package com.uoc.docker.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a student may type at the Elasticsearch console, and what curl is asked to do
 * about it.
 *
 * <p>
 * The course teaches Elasticsearch through its REST API, so the console accepts the
 * shorthand the documentation itself uses -- a verb and a path -- as well as a curl command
 * written out in full.
 */
class ElasticsearchHttpClientTest {

    private final ElasticsearchHttpClient client = new ElasticsearchHttpClient();

    @Test
    void aVerbAndAPathBecomeARequestToTheLocalServer() {
        assertThat(client.stdin("GET /_cluster/health"))
                .isEqualTo("curl -sS -i -X GET http://localhost:9200/_cluster/health\n");
    }

    @Test
    void aPathOnItsOwnIsFetched() {
        // It used to be uppercased and handed to curl as the verb, so a request anybody
        // might reasonably type went out as "-X /_CLUSTER/HEALTH" and was refused.
        assertThat(client.stdin("/_cluster/health"))
                .isEqualTo("curl -sS -i -X GET http://localhost:9200/_cluster/health\n");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS" })
    void everyVerbIsRecognised(String method) {
        assertThat(client.stdin(method + " /indice"))
                .contains("-X " + method + " http://localhost:9200/indice");
    }

    @Test
    void aVerbIsRecognisedWhateverItsCase() {
        assertThat(client.stdin("get /")).contains("-X GET ");
    }

    @Test
    void aWordThatIsNotAVerbIsTreatedAsThePathItLooksLike() {
        // "_cat/indices" is a path somebody left the slash off, not a request to invent a
        // "_CAT/INDICES" method.
        assertThat(client.stdin("_cat/indices"))
                .isEqualTo("curl -sS -i -X GET http://localhost:9200/_cat/indices\n");
    }

    @Test
    void aVerbWithNothingAfterItAsksForTheRoot() {
        assertThat(client.stdin("GET")).isEqualTo("curl -sS -i -X GET http://localhost:9200/\n");
    }

    @Test
    void aWholeUrlIsUsedAsItIsRatherThanHungOffTheLocalServer() {
        assertThat(client.stdin("GET http://localhost:9200/_nodes"))
                .isEqualTo("curl -sS -i -X GET http://localhost:9200/_nodes\n");
    }

    @Test
    void aCurlCommandWrittenOutInFullIsRunAsWritten() {
        // Everything a request needs beyond a verb and a path -- a body, a header -- is
        // typed as curl, so the shorthand never has to grow to cover it.
        assertThat(client.stdin("curl -XPUT http://localhost:9200/diario -d '{}'"))
                .isEqualTo("curl -sS -XPUT http://localhost:9200/diario -d '{}'\n");
    }

    @Test
    void theRequestTravelsOnStandardInputWithNoTerminal() {
        // A shell reading its command from standard input must not be given a terminal,
        // and the command goes that way because the JDK mangles quotes when it rebuilds a
        // command line on Windows.
        assertThat(client.command("GET /")).containsExactly("sh");
        assertThat(client.usesTerminal()).isFalse();
    }

    @Test
    void theAnswerIsColouredTheWayEveryOtherHttpAnswerIs() {
        assertThat(client.format("HTTP/1.1 200 OK\n"))
                .contains(String.valueOf((char) 27))
                .contains("HTTP/1.1 200 OK");
    }
}
