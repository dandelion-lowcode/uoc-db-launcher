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

    /** A process that is still running, and the only thing left to do with it. */
    @FunctionalInterface
    interface LiveProcess {

        /** Ends it. Ending one twice, or one that is already over, does nothing. */
        void stop();
    }

    /**
     * Starts a command that is not expected to end, hands over each line as it is
     * written, and says so if it stops anyway.
     *
     * <p>
     * Docker's event stream is such a command: it runs for as long as the launcher does,
     * and the only thing that ends it is the daemon going away, which is news the
     * launcher has to act on. {@link #run} can say none of that, since it answers once
     * the process is over and this one is not meant to be.
     *
     * <p>
     * The default runs the command through {@link #run} on a thread of its own and
     * replays what it wrote, which is enough for the stand-ins the tests use; the real
     * runner overrides it, and only the real one can end a process that has stopped
     * listening.
     *
     * @param command the program and its arguments; never empty
     * @param onLine  called with each line, on a thread of the runner's own rather than
     *                the caller's
     * @param onEnded called once, whenever the process stops, for whatever reason
     * @return the handle that ends it; never {@code null}
     */
    default LiveProcess stream(List<String> command, Consumer<String> onLine, Runnable onEnded) {
        Thread thread = new Thread(() -> {
            run(command, null, onLine);
            onEnded.run();
        });
        thread.setDaemon(true);
        thread.start();
        return thread::interrupt;
    }
}
