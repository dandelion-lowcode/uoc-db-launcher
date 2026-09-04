package com.uoc.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The launcher has to behave on systems the tests are not running on, so the
 * names here
 * are the real {@code os.name} values those systems report, taken from the
 * values a JVM
 * publishes rather than invented.
 */
class OperatingSystemTest {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
            "Windows 11,          WINDOWS",
            "Windows 10,          WINDOWS",
            "Windows Server 2022, WINDOWS",
            "Mac OS X,            MACOS",
            "Darwin,              MACOS",
            "Linux,               LINUX",
            "LINUX,               LINUX",
            "SunOS,               OTHER",
            "FreeBSD,             OTHER",
            "AIX,                 LINUX",
            "HP-UX,               OTHER",
            "z/OS,                OTHER"
    })
    void everySystemAJvmReportsIsPlacedSomewhere(String osName, OperatingSystem expected) {
        assertThat(OperatingSystem.of(osName)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} is still recognised")
    @ValueSource(strings = { "WINDOWS 11", "windows 11", "MAC OS X", "linux" })
    void theNameIsReadWhateverItsCase(String osName) {
        assertThat(OperatingSystem.of(osName)).isNotEqualTo(OperatingSystem.OTHER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void anUnknownSystemIsNotGuessedAt(String osName) {
        // Anything unrecognised must fall through rather than be mistaken for a system
        // whose commands would then be run on it.
        assertThat(OperatingSystem.of(osName)).isEqualTo(OperatingSystem.OTHER);
    }

    @Test
    void theSystemTheseTestsRunOnIsRecognised() {
        assertThat(OperatingSystem.current())
                .as("os.name was <%s>", System.getProperty("os.name"))
                .isNotEqualTo(OperatingSystem.OTHER);
    }

    @Test
    void theCurrentSystemAgreesWithItsOwnName() {
        assertThat(OperatingSystem.current())
                .isEqualTo(OperatingSystem.of(System.getProperty("os.name")));
    }
}
