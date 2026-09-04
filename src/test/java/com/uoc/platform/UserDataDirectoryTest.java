package com.uoc.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every branch is exercised from any machine, because the system and the environment are
 * arguments rather than something read from the JVM. A Windows path decided correctly
 * only on Windows would be a rule nobody checks until a student on a Mac finds it.
 *
 * <p>
 * Partitions here: each system, and for those that read the environment, the variable
 * set, unset, and set to nothing at all.
 */
class UserDataDirectoryTest {

    private static final String HOME = "/home/estudiante";

    private static UnaryOperator<String> environment(Map<String, String> values) {
        return values::get;
    }

    private static final UnaryOperator<String> NOTHING_SET = name -> null;

    @Test
    void windowsKeepsTheFilesInTheLocalPartOfTheProfile() {
        Path directory = UserDataDirectory.resolve(OperatingSystem.WINDOWS,
                environment(Map.of("LOCALAPPDATA", "C:\\Users\\est\\AppData\\Local")), HOME);

        assertThat(directory).isEqualTo(
                Path.of("C:\\Users\\est\\AppData\\Local", "uoc-db-launcher"));
    }

    @Test
    void windowsFallsBackToTheRoamingProfileWhenTheLocalOneIsNotNamed() {
        Path directory = UserDataDirectory.resolve(OperatingSystem.WINDOWS,
                environment(Map.of("APPDATA", "C:\\Users\\est\\AppData\\Roaming")), HOME);

        assertThat(directory).isEqualTo(
                Path.of("C:\\Users\\est\\AppData\\Roaming", "uoc-db-launcher"));
    }

    @Test
    void windowsBuildsThePathFromTheHomeDirectoryWhenNeitherIsSet() {
        Path directory = UserDataDirectory.resolve(OperatingSystem.WINDOWS, NOTHING_SET, HOME);

        assertThat(directory).isEqualTo(Path.of(HOME, "AppData", "Local", "uoc-db-launcher"));
    }

    @Test
    void anEmptyVariableCountsAsUnset() {
        // A variable defined as nothing is not an answer, and joining a path onto it
        // would put the files at the root of the disk.
        Path directory = UserDataDirectory.resolve(OperatingSystem.WINDOWS,
                environment(Map.of("LOCALAPPDATA", "   ")), HOME);

        assertThat(directory).isEqualTo(Path.of(HOME, "AppData", "Local", "uoc-db-launcher"));
    }

    @Test
    void macosUsesTheFolderAppleSetsAsideForApplicationData() {
        Path directory = UserDataDirectory.resolve(OperatingSystem.MACOS, NOTHING_SET, HOME);

        assertThat(directory).isEqualTo(
                Path.of(HOME, "Library", "Application Support", "uoc-db-launcher"));
    }

    @Test
    void macosIgnoresTheLinuxVariableEvenWhenItIsSet() {
        Path directory = UserDataDirectory.resolve(OperatingSystem.MACOS,
                environment(Map.of("XDG_DATA_HOME", "/tmp/xdg")), HOME);

        assertThat(directory).isEqualTo(
                Path.of(HOME, "Library", "Application Support", "uoc-db-launcher"));
    }

    @Test
    void linuxHonoursTheVariableTheDesktopSpecificationDefines() {
        Path directory = UserDataDirectory.resolve(OperatingSystem.LINUX,
                environment(Map.of("XDG_DATA_HOME", "/home/est/.local/share")), HOME);

        assertThat(directory).isEqualTo(Path.of("/home/est/.local/share", "uoc-db-launcher"));
    }

    @Test
    void linuxFallsBackToTheConventionalPlaceWhenTheVariableIsUnset() {
        Path directory = UserDataDirectory.resolve(OperatingSystem.LINUX, NOTHING_SET, HOME);

        assertThat(directory).isEqualTo(Path.of(HOME, ".local", "share", "uoc-db-launcher"));
    }

    @Test
    void anUnrecognisedSystemIsTreatedLikeLinuxRatherThanLeftWithoutAnywhereToWrite() {
        Path directory = UserDataDirectory.resolve(OperatingSystem.OTHER, NOTHING_SET, HOME);

        assertThat(directory).isEqualTo(Path.of(HOME, ".local", "share", "uoc-db-launcher"));
    }

    @Test
    void theDirectoryOnThisMachineIsAbsoluteAndNamedAfterTheApplication() {
        assertThat(UserDataDirectory.current())
                .isAbsolute()
                .hasFileName("uoc-db-launcher");
    }
}
