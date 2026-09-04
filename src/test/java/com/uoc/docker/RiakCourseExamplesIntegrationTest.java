package com.uoc.docker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Riak examples exactly as the course notes write them.
 *
 * <p>
 * The notes are not going to be changed, so this is the test that matters most
 * for
 * Riak: a student who copies a line out of them must get the documented answer.
 * It is the
 * only way to find out that a change to the client, to the image, or to the
 * console broke
 * the exercises, short of a person retyping them.
 */
@DisplayName("the Riak examples from the course notes")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiakCourseExamplesIntegrationTest extends DockerIntegrationTestBase {

    private static final String BUCKET = "http://localhost:8098/riak/prueba";
    private static final String KEY = BUCKET + "/curso";

    @BeforeAll
    static void startRiak() {
        startAndAwait(Database.RIAK);
    }

    @Test
    @Order(1)
    void storingAValueReportsNoProblem() {
        // Written as the notes write it, without -i, so curl prints no headers and a
        // 204
        // carries no body: an empty console is the same thing a real terminal shows.
        String output = query(Database.RIAK,
                "curl -XPUT " + KEY + " -H \"Content-Type: application/json\""
                        + " -d \"{\\\"titulo\\\":\\\"Rayuela\\\"}\"");

        assertThat(output).doesNotContain("curl:").doesNotContain("sh:");
    }

    @Test
    @Order(1)
    void storingAValueWithHeadersShownReportsTheStatus() {
        // The same request with -i, which is how a student sees that it worked.
        String output = query(Database.RIAK,
                "curl -i -XPUT " + KEY + "-con-cabeceras"
                        + " -H \"Content-Type: application/json\" -d \"{\\\"a\\\":1}\"");

        assertThat(output).contains("204");
    }

    @Test
    @Order(2)
    void readingItBackGivesTheStoredValue() {
        String output = query(Database.RIAK, KEY);

        assertThat(output).contains("200").contains("Rayuela");
    }

    @Test
    @Order(3)
    void theJsonSurvivesItsQuotes() {
        // The quotes in a JSON payload are what the Windows command line used to
        // mangle,
        // turning {"titulo":"Rayuela"} into {titulo:Rayuela} before it reached Riak.
        String output = query(Database.RIAK, KEY);

        assertThat(output).contains("{\"titulo\":\"Rayuela\"}");
    }

    @Test
    @Order(4)
    void aKeyThatDoesNotExistAnswersNotFound() {
        String output = query(Database.RIAK, BUCKET + "/no-existe");

        assertThat(output).contains("404");
    }

    @Test
    @Order(5)
    void deletingItReportsNoProblem() {
        assertThat(query(Database.RIAK, "curl -XDELETE " + KEY))
                .doesNotContain("curl:").doesNotContain("sh:");
    }

    @Test
    @Order(6)
    void afterDeletingItIsGone() {
        assertThat(query(Database.RIAK, KEY)).contains("404");
    }

    @Test
    @Order(7)
    void aBareUrlWorksAsAShortcutForAGet() {
        assertThat(query(Database.RIAK, BUCKET + "?keys=true")).contains("200");
    }

    @Test
    @Order(8)
    void aRequestThatCannotBeMadeReportsWhyRatherThanSayingNothing() {
        String output = query(Database.RIAK, "http://localhost:9/no-hay-nadie");

        assertThat(output).contains("curl:").isNotBlank();
    }
}
