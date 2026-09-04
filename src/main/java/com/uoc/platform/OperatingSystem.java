package com.uoc.platform;

import java.util.Locale;

/**
 * The system the launcher is running on, as far as it needs to care.
 *
 * <p>Only the parts that have no portable Java equivalent ask for this, which today is
 * reading the system-wide dark mode setting.
 */
public enum OperatingSystem {
    WINDOWS, MACOS, LINUX, OTHER;

    public static OperatingSystem current() {
        return of(System.getProperty("os.name", ""));
    }

    /**
     * The system a JVM's {@code os.name} describes. Taking the name as an argument keeps
     * the choice verifiable for systems other than the one the tests happen to run on.
     *
     * @param osName the value of the {@code os.name} property; null is treated as unknown
     * @return never null; {@link #OTHER} when the name is not recognised
     */
    public static OperatingSystem of(String osName) {
        String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        // Darwin is asked about first because it contains "win", which would otherwise
        // send a Mac down the Windows path and have it run a registry query.
        if (name.contains("mac") || name.contains("darwin")) {
            return MACOS;
        }
        if (name.contains("win")) {
            return WINDOWS;
        }
        if (name.contains("nux") || name.contains("nix") || name.contains("aix")) {
            return LINUX;
        }
        return OTHER;
    }
}
