package com.uoc.docker.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * How a student's AQL reaches arangosh.
 *
 * <p>
 * arangosh speaks JavaScript, so the query has to arrive inside a script, and the script
 * arrives on standard input because the JDK mangles double quotes when it rebuilds a
 * command line on Windows. What is checked here is that a student's own quotes and
 * backslashes survive that journey: an unbalanced one turns their typo into a syntax
 * error from a language they are not writing.
 */
class ArangoshClientTest {

    private final ArangoshClient client = new ArangoshClient();

    @Test
    void runsArangoshDirectlyWithNoShellAndNoTemporaryFile() {
        // It used to run "sh -c 'cat > /tmp/... && arangosh ...'", writing the query to a
        // file inside the container so JavaScript could read it back. That needed a
        // shell, a file and two languages to say what one line of standard input says.
        assertThat(client.command("RETURN 1"))
                .startsWith("arangosh")
                .doesNotContain("sh", "-c")
                .noneMatch(argument -> argument.contains("/tmp"))
                .noneMatch(argument -> argument.contains("readFileSync"));
    }

    @Test
    void theQueryTravelsOnStandardInputInsideAScriptThatPrintsItsRows() {
        assertThat(client.stdin("RETURN 1"))
                .isEqualTo("print(JSON.stringify(db._query(\"RETURN 1\").toArray()));\n");
    }

    @Test
    void aQueryWithQuotesInItDoesNotEndTheScriptEarly() {
        // The commonest thing to type after FILTER, and the one that would break a script
        // built by pasting the query in.
        assertThat(client.stdin("FOR v IN c FILTER v.type == \"Movie\" RETURN v"))
                .contains("FILTER v.type == \\\"Movie\\\"")
                .startsWith("print(JSON.stringify(db._query(\"");
    }

    @Test
    void aBackslashIsPassedOnRatherThanEatenByTheScript() {
        assertThat(client.stdin("RETURN \"a\\b\"")).contains("\\\\b");
    }

    @Test
    void aQueryOverSeveralLinesStaysOneScript() {
        // Newlines inside a JavaScript string literal have to be written as an escape, or
        // the literal ends at the end of the first line.
        String script = client.stdin("FOR value IN [1, 2]\nRETURN value");

        assertThat(script.lines()).hasSize(1);
        assertThat(script).contains("[1, 2]\\nRETURN value");
    }

    @Test
    void doesNotAllocateATerminalForTheAqlScript() {
        assertThat(client.usesTerminal()).isFalse();
    }

    @Test
    void theCredentialsAreTheOnesTheComposeFileSets() {
        // Both have to agree, and there is nothing else to check them against.
        assertThat(client.command("RETURN 1"))
                .containsSequence("--server.username", "root")
                .containsSequence("--server.password", "rootpassword");
    }

    @Test
    void theConsoleOpensOnTheDatabaseTheDatasetIsIn() {
        // Left out, arangosh opens _system, where imdb_vertices does not exist: the
        // tutorial's first query would report a missing collection on a server that has
        // the whole graph in it.
        assertThat(client.command("RETURN 1")).containsSequence("--server.database", "IMDB");
    }
}
