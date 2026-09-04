package com.uoc.docker;

import java.util.List;

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
}
