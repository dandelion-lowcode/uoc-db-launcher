package com.uoc.platform;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

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
        return isEnabled(OperatingSystem.current(), SystemDarkMode::commandOutput);
    }

    /**
     * The same question asked of something other than this machine.
     *
     * <p>
     * Which command to run and what its answer means are the whole of what this class
     * knows, and neither can be checked on a machine that answers only one way: a test
     * running on Windows cannot tell whether the macOS branch reads its answer correctly,
     * and cannot put the machine into dark mode to find out about its own.
     *
     * @param system what to answer as
     * @param ask    runs a command and returns what it wrote, or an empty string if it
     *               could not be run at all
     */
    static boolean isEnabled(OperatingSystem system, Function<List<String>, String> ask) {
        switch (system) {
            case WINDOWS:
                // AppsUseLightTheme is 0x0 while the dark appearance is active.
                return ask.apply(List.of("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme")).contains("0x0");
            case MACOS:
                // The key only exists while the dark appearance is active.
                return ask.apply(List.of("defaults", "read", "-g", "AppleInterfaceStyle"))
                        .toLowerCase(Locale.ROOT).contains(DARK);
            case LINUX:
                String scheme = ask.apply(
                        List.of(GSETTINGS, GSETTINGS_GET, GNOME_INTERFACE, "color-scheme"))
                        .toLowerCase(Locale.ROOT);
                if (scheme.contains(DARK)) {
                    return true;
                }
                // Desktops older than color-scheme say it in the theme's name instead.
                return ask.apply(
                        List.of(GSETTINGS, GSETTINGS_GET, GNOME_INTERFACE, "gtk-theme"))
                        .toLowerCase(Locale.ROOT).contains(DARK);
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
