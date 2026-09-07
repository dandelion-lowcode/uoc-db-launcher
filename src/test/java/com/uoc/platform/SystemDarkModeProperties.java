package com.uoc.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;

/**
 * What any answer at all means, rather than the handful a desktop is known to give.
 *
 * <p>
 * These commands are read by whatever is installed on a student's machine, across several
 * desktops and several versions of each, and the wording is nobody's promise: gsettings
 * has answered {@code 'prefer-dark'}, {@code prefer-dark} and {@code 'Adwaita-dark'} at
 * different times. What must hold is the shape of the reading -- that a mention of dark is
 * what turns it on and nothing else does -- which is a property rather than a list of
 * examples.
 */
class SystemDarkModeProperties {

    /** Answers every command the same way, whatever it is. */
    private static Function<List<String>, String> alwaysSaying(String answer) {
        return command -> answer;
    }

    @Property
    void nothingButAMentionOfDarkTurnsItOnOnAppleAndLinux(
            @ForAll @StringLength(max = 60) String answer) {
        Assume.that(!answer.toLowerCase(Locale.ROOT).contains("dark"));

        assertThat(SystemDarkMode.isEnabled(OperatingSystem.MACOS, alwaysSaying(answer)))
                .as("macOS read \"%s\" as dark", answer).isFalse();
        assertThat(SystemDarkMode.isEnabled(OperatingSystem.LINUX, alwaysSaying(answer)))
                .as("Linux read \"%s\" as dark", answer).isFalse();
    }

    @Property
    void aMentionOfDarkAnywhereInTheAnswerIsEnough(
            @ForAll @StringLength(max = 30) String before,
            @ForAll @StringLength(max = 30) String after) {
        String answer = before + "Dark" + after;

        assertThat(SystemDarkMode.isEnabled(OperatingSystem.MACOS, alwaysSaying(answer)))
                .as("macOS missed the dark in \"%s\"", answer).isTrue();
    }

    @Property
    void howItIsCapitalisedDoesNotMatter(@ForAll @StringLength(min = 1, max = 20) String noise) {
        for (String spelling : List.of("dark", "DARK", "Dark", "prefer-dark", "Adwaita-Dark")) {
            String answer = noise + spelling;

            assertThat(SystemDarkMode.isEnabled(OperatingSystem.LINUX, alwaysSaying(answer)))
                    .as("\"%s\"", answer).isTrue();
        }
    }

    @Property
    void windowsReadsTheNumberAndNotTheWord(@ForAll @StringLength(max = 60) String answer) {
        // The registry answers with a name, a type and a value on one line, and the word
        // in that name is "Light". Only the value decides.
        assertThat(SystemDarkMode.isEnabled(OperatingSystem.WINDOWS, alwaysSaying(answer)))
                .isEqualTo(answer.contains("0x0"));
    }

    @Property
    void aSystemWeKnowNothingAboutIsLightWhateverItSays(
            @ForAll @StringLength(max = 60) String answer) {
        assertThat(SystemDarkMode.isEnabled(OperatingSystem.OTHER, alwaysSaying(answer)))
                .isFalse();
    }

    @Property
    void aDesktopThatAnswersNothingIsLightOnEverySystem(
            @ForAll OperatingSystem system) {
        assertThat(SystemDarkMode.isEnabled(system, alwaysSaying(""))).isFalse();
    }
}
