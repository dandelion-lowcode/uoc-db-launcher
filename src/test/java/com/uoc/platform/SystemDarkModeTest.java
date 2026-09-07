package com.uoc.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * What each desktop is asked, and what it makes of the answer.
 *
 * <p>
 * None of this can be checked against the machine the tests run on: it answers for one
 * system only, and it cannot be put into dark mode and back to see whether the reading
 * changed. The command is therefore stood in for, which also pins the command itself --
 * a registry key or a settings name that quietly stops being the right one leaves the
 * launcher reporting light for ever, and looks exactly like a desktop set to light.
 */
@DisplayName("reading the desktop's dark mode")
class SystemDarkModeTest {

    /** Answers whatever the test says, and remembers what it was asked. */
    private static final class Desktop implements Function<List<String>, String> {

        private final Map<String, String> answers;
        private final List<List<String>> asked = new ArrayList<>();

        private Desktop(Map<String, String> answers) {
            this.answers = answers;
        }

        /** Keyed on the last word of the command, which is what each query names. */
        @Override
        public String apply(List<String> command) {
            asked.add(command);
            return answers.getOrDefault(command.get(command.size() - 1), "");
        }
    }

    private static Desktop saying(String key, String answer) {
        return new Desktop(Map.of(key, answer));
    }

    private static Desktop sayingNothing() {
        return new Desktop(Map.of());
    }

    @Test
    void windowsIsDarkWhileAppsUseLightThemeIsZero() {
        Desktop desktop = saying("AppsUseLightTheme",
                "    AppsUseLightTheme    REG_DWORD    0x0\r\n");

        assertThat(SystemDarkMode.isEnabled(OperatingSystem.WINDOWS, desktop)).isTrue();
    }

    @Test
    void windowsIsLightWhileItIsOne() {
        Desktop desktop = saying("AppsUseLightTheme",
                "    AppsUseLightTheme    REG_DWORD    0x1\r\n");

        assertThat(SystemDarkMode.isEnabled(OperatingSystem.WINDOWS, desktop)).isFalse();
    }

    @Test
    void windowsIsAskedForTheKeyThatHoldsIt() {
        Desktop desktop = sayingNothing();

        SystemDarkMode.isEnabled(OperatingSystem.WINDOWS, desktop);

        assertThat(desktop.asked).singleElement().asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .containsSequence("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme");
    }

    @Test
    void macosIsDarkWhileTheKeyExists() {
        assertThat(SystemDarkMode.isEnabled(OperatingSystem.MACOS,
                saying("AppleInterfaceStyle", "Dark\n"))).isTrue();
    }

    @Test
    void macosIsLightWhenTheKeyIsNotThereAtAll() {
        // "defaults read" fails outright rather than printing anything, and a command
        // that failed reads as an empty answer here.
        assertThat(SystemDarkMode.isEnabled(OperatingSystem.MACOS, sayingNothing())).isFalse();
    }

    @Test
    void linuxIsDarkWhenTheColourSchemeSaysSo() {
        assertThat(SystemDarkMode.isEnabled(OperatingSystem.LINUX,
                saying("color-scheme", "'prefer-dark'\n"))).isTrue();
    }

    @Test
    void linuxFallsBackToTheThemesNameOnDesktopsWithoutAColourScheme() {
        Desktop desktop = saying("gtk-theme", "'Adwaita-dark'\n");

        assertThat(SystemDarkMode.isEnabled(OperatingSystem.LINUX, desktop)).isTrue();
        assertThat(desktop.asked).hasSize(2);
    }

    @Test
    void linuxDoesNotAskTwiceWhenTheFirstAnswerIsEnough() {
        Desktop desktop = saying("color-scheme", "'prefer-dark'\n");

        SystemDarkMode.isEnabled(OperatingSystem.LINUX, desktop);

        assertThat(desktop.asked).hasSize(1);
    }

    @Test
    void linuxIsLightWhenNeitherSettingMentionsIt() {
        Desktop desktop = new Desktop(Map.of(
                "color-scheme", "'default'\n", "gtk-theme", "'Adwaita'\n"));

        assertThat(SystemDarkMode.isEnabled(OperatingSystem.LINUX, desktop)).isFalse();
    }

    @Test
    void aSystemWeKnowNothingAboutIsLightAndIsAskedNothing() {
        Desktop desktop = sayingNothing();

        assertThat(SystemDarkMode.isEnabled(OperatingSystem.OTHER, desktop)).isFalse();
        assertThat(desktop.asked).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(OperatingSystem.class)
    void aDesktopThatAnswersNothingAtAllIsLightRatherThanAFailure(OperatingSystem system) {
        // Every one of these commands is missing on some machine that runs this
        // launcher, and a missing command must read as light rather than as an error on
        // the way to the first window.
        assertThat(SystemDarkMode.isEnabled(system, sayingNothing())).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(OperatingSystem.class)
    void nothingIsAskedForMoreThanOnceAndNoQueryIsEmpty(OperatingSystem system) {
        Desktop desktop = sayingNothing();

        SystemDarkMode.isEnabled(system, desktop);

        assertThat(desktop.asked).doesNotHaveDuplicates().allSatisfy(
                command -> assertThat(command).isNotEmpty());
    }

    @Test
    void theRealReadingAnswersWithoutThrowingWhateverThisMachineIs() {
        // The one thing the stand-in cannot cover: that the wiring from the public
        // question to the command is there at all.
        assertThat(SystemDarkMode.isEnabled()).isIn(true, false);
    }
}
