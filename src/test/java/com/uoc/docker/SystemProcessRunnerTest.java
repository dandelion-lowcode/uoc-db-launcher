package com.uoc.docker;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one place that starts a real process, exercised with a real one.
 *
 * <p>The process is the JVM the tests are already running under, so these need nothing
 * installed and behave the same on every system. What they check is the handling around
 * the process, which is where the mistakes were: an exit code that was ignored, an output
 * stream that was never drained, and a failure that reached the console as silence.
 */
class SystemProcessRunnerTest {

    private final SystemProcessRunner runner = new SystemProcessRunner();

    /** The java command of the JVM running the tests, so no assumption about the PATH. */
    private static String java() {
        String extension = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? ".exe" : "";
        return Path.of(System.getProperty("java.home"), "bin", "java" + extension).toString();
    }

    @Test
    void aProcessThatSucceedsReportsZeroAndWhatItWrote() {
        ProcessRunner.Result result = runner.run(List.of(java(), "-version"), null);

        assertThat(result.exitCode()).isZero();
        assertThat(result.failed()).isFalse();
        assertThat(result.output()).isNotBlank();
    }

    @Test
    void whatAProcessWritesToItsErrorStreamIsKeptToo() {
        // java -version writes to the error stream. Losing it would have hidden every
        // message a database client sends the same way.
        ProcessRunner.Result result = runner.run(List.of(java(), "-version"), null);

        assertThat(result.output()).containsIgnoringCase("version");
    }

    @Test
    void aProcessThatFailsReportsItRatherThanLookingLikeSuccess() {
        ProcessRunner.Result result = runner.run(List.of(java(), "-opcion-que-no-existe"), null);

        assertThat(result.failed()).isTrue();
        assertThat(result.output()).isNotBlank();
    }

    @Test
    void aCommandThatCannotBeRunSaysSoInsteadOfThrowing() {
        // A missing docker binary must show up as a failure the interface can report.
        ProcessRunner.Result result = runner.run(List.of("programa-que-no-existe-en-ningun-sitio"), null);

        assertThat(result.failed()).isTrue();
        assertThat(result.output()).isNotBlank();
    }

    @Test
    void aLotOfOutputIsReadRatherThanBlockingForever() {
        // The bug this covers: waiting for a process without draining its output leaves
        // it blocked once the pipe fills, which froze the panel on the first image pull.
        String program = "public class Ruidoso {"
                + " public static void main(String[] a) {"
                + "  StringBuilder line = new StringBuilder();"
                + "  for (int i = 0; i < 100; i++) { line.append(\"ABCDEFGHIJ\"); }"
                + "  for (int i = 0; i < 20000; i++) { System.out.println(line); }"
                + " } }";

        ProcessRunner.Result result = runInSourceMode(program, "Ruidoso");

        assertThat(result.exitCode()).isZero();
        assertThat(result.output().length()).isGreaterThan(2_000_000);
    }

    @Test
    void whatIsWrittenToAProcessReachesIt() {
        String program = "import java.util.Scanner;"
                + " public class Eco {"
                + "  public static void main(String[] a) {"
                + "   Scanner s = new Scanner(System.in);"
                + "   while (s.hasNextLine()) { System.out.println(\"eco: \" + s.nextLine()); }"
                + "  } }";

        ProcessRunner.Result result = runInSourceMode(program, "Eco", "hola\nmundo\n");

        assertThat(result.output()).contains("eco: hola").contains("eco: mundo");
    }

    @Test
    void aProcessGivenNothingToReadStillFinishes() {
        ProcessRunner.Result result = runner.run(List.of(java(), "-version"), null);

        assertThat(result.exitCode()).isZero();
    }

    private ProcessRunner.Result runInSourceMode(String program, String name) {
        return runInSourceMode(program, name, null);
    }

    /** Runs a small program straight from source, which every modern JVM can do. */
    private ProcessRunner.Result runInSourceMode(String program, String name, String stdin) {
        try {
            Path file = java.nio.file.Files.createTempDirectory("uocdb-test").resolve(name + ".java");
            java.nio.file.Files.writeString(file, program);
            return runner.run(List.of(java(), file.toString()), stdin);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
