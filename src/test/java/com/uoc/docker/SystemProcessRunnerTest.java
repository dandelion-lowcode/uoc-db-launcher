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

    /** Writes one line, waits, then writes another, so the two cannot arrive together. */
    private static final String SLOW_EMITTER = """
            public class SlowEmitter {
                public static void main(String[] args) throws Exception {
                    System.out.println("una");
                    System.out.flush();
                    Thread.sleep(600);
                    System.out.println("dos");
                }
            }
            """;

    @Test
    void eachLineArrivesWhileTheProcessIsStillRunning() {
        // The point of following an install: waiting for the process to finish before
        // showing anything throws away the only part that is useful while it happens.
        // The pause between the two lines is what proves the first was handed over
        // before the second had even been written.
        List<Long> arrivals = new java.util.concurrent.CopyOnWriteArrayList<>();
        long start = System.nanoTime();

        ProcessRunner.Result result = runInSourceMode(SLOW_EMITTER, "SlowEmitter",
                line -> arrivals.add(System.nanoTime() - start));

        assertThat(result.exitCode()).isZero();
        assertThat(arrivals).hasSize(2);
        assertThat(java.time.Duration.ofNanos(arrivals.get(1) - arrivals.get(0)))
                .as("the second line should arrive after the pause, not with the first")
                .isGreaterThan(java.time.Duration.ofMillis(300));
    }

    @Test
    void theWholeOutputIsStillReturnedWhenTheLinesAreFollowed() {
        ProcessRunner.Result result = runInSourceMode(SLOW_EMITTER, "SlowEmitter", line -> {
        });

        assertThat(result.output().lines()).containsExactly("una", "dos");
    }

    @Test
    void aRunnerThatFollowsNothingStillBehavesAsItAlwaysDid() {
        ProcessRunner.Result result = runner.run(List.of(java(), "-version"), null);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).isNotEmpty();
    }

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
        return runInSourceMode(program, name, (String) null);
    }

    /** Runs a small program straight from source, which every modern JVM can do. */
    private ProcessRunner.Result runInSourceMode(String program, String name, String stdin) {
        try {
            return runner.run(List.of(java(), sourceFile(program, name)), stdin);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The same, following the output line by line as it is written. */
    private ProcessRunner.Result runInSourceMode(String program, String name,
            java.util.function.Consumer<String> onLine) {
        try {
            return runner.run(List.of(java(), sourceFile(program, name)), null, onLine);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sourceFile(String program, String name) throws java.io.IOException {
        Path file = java.nio.file.Files.createTempDirectory("uocdb-test").resolve(name + ".java");
        java.nio.file.Files.writeString(file, program);
        return file.toString();
    }
}
