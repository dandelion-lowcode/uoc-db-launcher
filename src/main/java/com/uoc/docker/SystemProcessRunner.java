package com.uoc.docker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runs commands through the operating system. This is the only place in the
 * query and
 * lifecycle paths that touches a real process.
 */
public class SystemProcessRunner implements ProcessRunner {

    private static final String ERROR_PREFIX = "Error: ";
    private static final int NOT_RUN = -1;
    /** Named so that a thread dump taken while the launcher is stuck says who is who. */
    private static final String STREAM_THREAD_NAME = "process-stream";

    @Override
    public Result run(List<String> command, String stdin) {
        return run(command, stdin, line -> {
        });
    }

    @Override
    public Result run(List<String> command, String stdin, Consumer<String> onLine) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            if (stdin != null) {
                try (OutputStream out = process.getOutputStream()) {
                    out.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }

            // Read line by line rather than in one go at the end. Draining as it comes is
            // what keeps a process that fills the pipe from blocking on a reader that is
            // not there, and it is also what lets a download be watched while it happens
            // instead of only once it is over.
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    onLine.accept(line);
                }
            }
            return new Result(process.waitFor(), output.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(NOT_RUN, ERROR_PREFIX + e.getMessage());
        } catch (Exception e) {
            return new Result(NOT_RUN, ERROR_PREFIX + e.getMessage());
        }
    }

    @Override
    public LiveProcess stream(List<String> command, Consumer<String> onLine, Runnable onEnded) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (Exception e) {
            // A command that cannot even be started has ended as far as the caller is
            // concerned, and being told so is what lets it try again later.
            onEnded.run();
            return () -> {
            };
        }

        // A thread of its own, because this call must come back at once: what it starts
        // outlives it, and the caller has a window to put on the screen. It is a daemon
        // thread so that a process nobody ended cannot keep the launcher from closing.
        Thread reader = new Thread(() -> follow(process, onLine, onEnded), STREAM_THREAD_NAME);
        reader.setDaemon(true);
        reader.start();
        return process::destroy;
    }

    private static void follow(Process process, Consumer<String> onLine, Runnable onEnded) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                onLine.accept(line);
            }
        } catch (Exception e) {
            // The stream breaking is the process ending under another name, and either
            // way what the caller needs to hear is that there is no more to come.
        } finally {
            onEnded.run();
        }
    }
}
