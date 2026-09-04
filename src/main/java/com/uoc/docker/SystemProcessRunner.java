package com.uoc.docker;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            if (stdin != null) {
                try (OutputStream out = process.getOutputStream()) {
                    out.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }
            // The output is drained before waiting: a process that fills the pipe blocks
            // until somebody reads the other end, and Compose prints a lot while pulling.
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Result(process.waitFor(), output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(NOT_RUN, ERROR_PREFIX + e.getMessage());
        } catch (Exception e) {
            return new Result(NOT_RUN, ERROR_PREFIX + e.getMessage());
        }
    }
}
