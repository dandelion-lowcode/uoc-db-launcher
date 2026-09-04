package com.uoc.docker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the command built for each database is one its client actually
 * accepts.
 *
 * <p>
 * The unit tests prove the launcher builds the command it means to; only a real
 * container proves that command is the right one. A flag that changed name
 * between image
 * versions would pass every unit test and fail here, which is exactly the sort
 * of break
 * a student would otherwise be the first to find.
 */
@DisplayName("each database answers the command the launcher builds")
class DatabaseClientIntegrationTest extends DockerIntegrationTestBase {

    @Test
    void mongoAnswersAQuery() {
        startAndAwait(Database.MONGO);

        String output = query(Database.MONGO, "db.runCommand({ping:1}).ok");

        assertThat(output).contains("1");
    }

    @Test
    void cassandraAnswersAQuery() {
        startAndAwait(Database.CASSANDRA);

        String output = query(Database.CASSANDRA, "SELECT release_version FROM system.local;");

        assertThat(output).contains("release_version");
    }

    @Test
    void neo4jAnswersAQuery() {
        startAndAwait(Database.NEO4J);

        String output = query(Database.NEO4J, "RETURN 1 AS uno");

        assertThat(output).contains("uno").contains("1");
    }

    @Test
    void redisAnswersAQuery() {
        startAndAwait(Database.REDIS);

        assertThat(query(Database.REDIS, "PING")).contains("PONG");
    }

    @Test
    void redisKeepsAQuotedValueTogether() {
        startAndAwait(Database.REDIS);

        query(Database.REDIS, "SET saludo \"hola mundo\"");

        // Splitting on whitespace would have stored only "hola".
        assertThat(query(Database.REDIS, "GET saludo")).contains("hola mundo");
    }

    @Test
    void aClientThatFailsStillSaysSomething() {
        startAndAwait(Database.REDIS);

        // An error from the database must reach the console rather than leaving it
        // blank.
        assertThat(query(Database.REDIS, "COMANDO-QUE-NO-EXISTE")).isNotBlank();
    }
}
