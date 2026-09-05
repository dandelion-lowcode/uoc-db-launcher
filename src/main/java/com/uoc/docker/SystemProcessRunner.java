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
}
