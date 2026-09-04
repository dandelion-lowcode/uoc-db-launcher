package com.uoc.platform;

import java.util.List;
import java.util.Locale;

/**
 * Reports whether the desktop is currently using a dark appearance.
 * Each supported platform exposes this through a different command, so the query
 * is chosen from the running operating system; unknown platforms report light.
 */
public final class SystemDarkMode {

    private static final String DARK = "dark";
    private static final String GSETTINGS = "gsettings";
    private static final String GSETTINGS_GET = "get";
    private static final String GNOME_INTERFACE = "org.gnome.desktop.interface";

    private SystemDarkMode() {
    }

    public static boolean isEnabled() {
        switch (OperatingSystem.current()) {
            case WINDOWS:
                // AppsUseLightTheme is 0x0 while the dark appearance is active.
                return commandOutput(List.of("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme")).contains("0x0");
            case MACOS:
                // The key only exists while the dark appearance is active.
                return commandOutput(List.of("defaults", "read", "-g", "AppleInterfaceStyle"))
                        .toLowerCase(Locale.ROOT).contains(DARK);
            case LINUX:
                String scheme = commandOutput(List.of(GSETTINGS, GSETTINGS_GET, GNOME_INTERFACE, "color-scheme")).toLowerCase(Locale.ROOT);
                if (scheme.contains(DARK)) {
                    return true;
                }
                return commandOutput(List.of(GSETTINGS, GSETTINGS_GET, GNOME_INTERFACE, "gtk-theme")).toLowerCase(Locale.ROOT).contains(DARK);
            default:
                return false;
        }
    }

    private static String commandOutput(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            return process.waitFor() == 0 ? output : "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
