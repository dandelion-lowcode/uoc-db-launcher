package com.uoc.platform;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Where the launcher keeps the files it needs on disk.
 *
 * <p>
 * An installed application cannot write next to itself: on Windows it sits under
 * Program Files, on macOS inside a bundle, and on Linux under a system directory, all of
 * which are read-only to the person running it. Anything that has to survive between
 * runs, or simply exist as a real file, therefore belongs in the place each system sets
 * aside for a program's own data.
 *
 * <p>
 * Each system names that place differently, which is the only reason this class exists.
 */
public final class UserDataDirectory {

    private static final String APPLICATION = "uoc-db-launcher";

    private UserDataDirectory() {
    }

    /** The directory for this user on this system, which may not exist yet. */
    public static Path current() {
        return resolve(OperatingSystem.current(), System::getenv,
                System.getProperty("user.home", "."));
    }

    /**
     * The directory a given system would use. The system and the environment are
     * arguments so that every branch can be exercised from a test, whatever machine it
     * happens to run on.
     *
     * @param os          the system to resolve for
     * @param environment reads an environment variable, returning null when it is unset
     * @param userHome    the value of the {@code user.home} property
     * @return never null
     */
    static Path resolve(OperatingSystem os, UnaryOperator<String> environment, String userHome) {
        Path home = Path.of(userHome);
        return switch (os) {
            // Local rather than roaming: these files are a cache of what the application
            // ships plus a student's notebooks, and copying them onto every machine of a
            // domain account would be a surprise nobody asked for.
            case WINDOWS -> firstSet(environment, "LOCALAPPDATA", "APPDATA")
                    .orElse(home.resolve("AppData").resolve("Local"))
                    .resolve(APPLICATION);
            case MACOS -> home.resolve("Library").resolve("Application Support")
                    .resolve(APPLICATION);
            // The convention the freedesktop specification sets out, which is what a
            // Linux desktop expects and what the rest of the world falls back to.
            default -> firstSet(environment, "XDG_DATA_HOME")
                    .orElse(home.resolve(".local").resolve("share"))
                    .resolve(APPLICATION);
        };
    }

    private static Optional<Path> firstSet(
            UnaryOperator<String> environment, String... names) {
        for (String name : names) {
            String value = environment.apply(name);
            if (value != null && !value.isBlank()) {
                return Optional.of(Path.of(value));
            }
        }
        return Optional.empty();
    }
}
