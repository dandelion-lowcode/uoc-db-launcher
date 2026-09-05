package com.uoc.docker;

import java.util.List;
import java.util.function.Consumer;

/**
 * Runs an external command and reports what it wrote and how it ended.
 *
 * <p>
 * This is the boundary between the code that decides what to run and the
 * operating
 * system that runs it. Keeping it behind an interface lets the deciding half be
 * tested
 * without Docker, a container, or a database.
 */
public interface ProcessRunner {

    /**
     * The outcome of a finished process.
     *
     * @param exitCode the process exit code, or a negative number when it could not
     *                 be run
     * @param output   everything the process wrote, with its error stream merged in
     */
    record Result(int exitCode, String output) {

        public boolean failed() {
            return exitCode != 0;
        }
    }

    /**
     * @param command the program and its arguments; never empty
     * @param stdin   what to write to the process input, or {@code null} to write
     *                nothing
     * @return the outcome; never {@code null}
     */
    Result run(List<String> command, String stdin);

    /**
     * The same, but handing over each line as it is written rather than all of them at
     * the end.
     *
     * <p>
     * Fetching an image takes minutes and says so the whole time, and a student watching
     * a still panel has no way to tell a download from a launcher that has stopped
     * responding. Waiting for the process to finish before showing anything throws away
     * the only part of that which is useful while it happens.
     *
     * <p>
     * The default reads nothing early and simply replays the finished output, which is
     * enough for the stand-ins the tests use; the real runner overrides it.
     *
     * @param onLine called with each line, on the thread running the command
     */
    default Result run(List<String> command, String stdin, Consumer<String> onLine) {
        Result result = run(command, stdin);
        result.output().lines().forEach(onLine);
        return result;
    }
}
