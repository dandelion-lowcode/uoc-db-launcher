package com.uoc.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicArrowButton;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatArrowButton;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.model.CharBuffer;
import com.uoc.ansi.AnsiColor;
import com.uoc.ansi.ThemeAnsiColors;
import com.uoc.docker.Database;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

@DisplayName("the terminal each service is driven from")
class TerminalTabTest {

    @BeforeAll
    static void installTheThemeThatHoldsOurColours() {
        ThemeForTests.install();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Database.class)
    void everyServiceWithAConsoleKnowsWhatToOpenInIt(Database database) {
        if (!database.hasQueryConsole()) {
            return;
        }

        assertThat(TerminalTab.clientFor(database))
                .as("%s has a tab to type in and nothing to run in it", database)
                .isNotEmpty();
    }

    @Test
    void aServiceWithNoConsoleIsRefusedRatherThanGivenAShell() {
        // Jupyter is opened in a browser. Answering this with something plausible would
        // put a tab in front of a student that cannot do anything.
        assertThatThrownBy(() -> TerminalTab.clientFor(Database.JUPYTER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verticaOpensOnTheDatabaseTheCourseWorksIn() {
        assertThat(TerminalTab.clientFor(Database.VERTICA))
                .contains("/opt/vertica/bin/vsql", "-U", "dbadmin", "-d", "VMart");
    }

    @Test
    void arangoshOpensOnTheImdbGraph() {
        // Its exercises begin with a query over imdb_vertices, and arangosh otherwise
        // starts in _system, where that collection does not exist.
        assertThat(TerminalTab.clientFor(Database.ARANGODB))
                .containsSequence("--server.database", "IMDB");
    }

    @Test
    void cqlshIsAskedForColourOutright() {
        // It decides for itself by asking whether it is writing to a file, and through a
        // pseudo terminal it guesses wrong.
        assertThat(TerminalTab.clientFor(Database.CASSANDRA)).contains("--color");
    }

    @Test
    void theRightClickMenuIsInTheLanguageTheStudentPicked() {
        TerminalTab.ConsoleLikeSettings settings =
                new TerminalTab.ConsoleLikeSettings(new Translations(Locale.of("es")));

        assertThat(settings.getCopyActionPresentation().getName()).isEqualTo("Copiar");
        assertThat(settings.getClearBufferActionPresentation().getName())
                .isEqualTo("Limpiar la consola");
    }

    @Test
    void renamingTheMenuLeavesTheShortcutsJediTermChose() {
        // Only the wording is ours. An item renamed without its keys would drop the
        // shortcut it is listed with.
        var settings = new TerminalTab.ConsoleLikeSettings(new Translations(Locale.ENGLISH));
        var jediTerm = new com.jediterm.terminal.ui.settings.DefaultSettingsProvider();

        assertThat(settings.getFindActionPresentation().getKeyStrokes())
                .isEqualTo(jediTerm.getFindActionPresentation().getKeyStrokes());
    }

    @Test
    void aMatchIsPaintedInTheThemesOwnYellowRatherThanJediTermsFixedOne() {
        // JediTerm paints one in black on #FFFF00, the same under either theme.
        var settings = new TerminalTab.ConsoleLikeSettings(new Translations(Locale.ENGLISH));
        var style = settings.getFoundPatternColor();

        assertThat(asAwt(style.getBackground())).isEqualTo(new ThemeAnsiColors().of(AnsiColor.YELLOW));
        assertThat(asAwt(style.getForeground())).isEqualTo(UIManager.getColor("Console.background"));
    }

    @Test
    void theFindBarsArrowsAreDrawnByTheLookAndFeel() throws Exception {
        // This is the whole point of having our own: JediTerm builds its two out of
        // BasicArrowButton, which paints its triangle and its border in the Basic style
        // whatever look and feel is installed.
        TerminalTab.FindBar bar = findBar(Locale.ENGLISH);

        List<BasicArrowButton> arrows = childrenOf(bar.getComponent(), BasicArrowButton.class);

        assertThat(arrows).hasSize(2).allMatch(FlatArrowButton.class::isInstance);
    }

    @Test
    void theFindBarSpeaksTheStudentsLanguage() throws Exception {
        Translations spanish = new Translations(Locale.of("es"));
        TerminalTab.FindBar bar = onSwing(() -> new TerminalTab.FindBar(spanish));

        JCheckBox ignoreCase = only(bar.getComponent(), JCheckBox.class);

        assertThat(ignoreCase.getText()).isEqualTo(spanish.get(Message.TERMINAL_IGNORE_CASE));
    }

    @Test
    void changingTheLanguageReachesAFindBarThatIsAlreadyOpen() throws Exception {
        Translations translations = new Translations(Locale.ENGLISH);
        TerminalTab.FindBar bar = onSwing(() -> new TerminalTab.FindBar(translations));
        String english = only(bar.getComponent(), JCheckBox.class).getText();

        onSwing(() -> {
            translations.setLocale(Locale.of("ca"));
            return null;
        });

        assertThat(only(bar.getComponent(), JCheckBox.class).getText())
                .isEqualTo(new Translations(Locale.of("ca")).get(Message.TERMINAL_IGNORE_CASE))
                .isNotEqualTo(english);
    }

    @Test
    void howManyMatchesThereAreAndWhichOneIsShowing() throws Exception {
        TerminalTab.FindBar bar = findBar(Locale.ENGLISH);

        onSwing(() -> {
            only(bar.getComponent(), JTextField.class).setText("ab");
            bar.onResultUpdated(matchesOf("ab", "xabxab"));
            return null;
        });

        assertThat(only(bar.getComponent(), JLabel.class).getText()).isEqualTo("1/2");
    }

    @Test
    void aQueryThatMatchesNothingOutlinesTheBoxTheWayFlatLafDoesElsewhere() throws Exception {
        TerminalTab.FindBar bar = findBar(Locale.ENGLISH);
        JTextField typed = only(bar.getComponent(), JTextField.class);

        onSwing(() -> {
            typed.setText("nowhere");
            bar.onResultUpdated(new SubstringFinder.FindResult());
            return null;
        });

        assertThat(typed.getClientProperty(FlatClientProperties.OUTLINE))
                .isEqualTo(FlatClientProperties.OUTLINE_ERROR);
        assertThat(only(bar.getComponent(), JLabel.class).getText()).isEmpty();
    }

    @Test
    void theOutlineGoesAgainAsSoonAsSomethingMatches() throws Exception {
        TerminalTab.FindBar bar = findBar(Locale.ENGLISH);
        JTextField typed = only(bar.getComponent(), JTextField.class);

        onSwing(() -> {
            typed.setText("nowhere");
            bar.onResultUpdated(new SubstringFinder.FindResult());
            typed.setText("ab");
            bar.onResultUpdated(matchesOf("ab", "xabxab"));
            return null;
        });

        assertThat(typed.getClientProperty(FlatClientProperties.OUTLINE)).isNull();
    }

    /** What JediTerm would hand the bar after searching a screen holding this text. */
    private static SubstringFinder.FindResult matchesOf(String pattern, String screen) {
        SubstringFinder finder = new SubstringFinder(pattern, true);
        CharBuffer line = new CharBuffer(screen);
        for (int i = 0; i < screen.length(); i++) {
            finder.nextChar(i, 0, line, i);
        }
        return finder.getResult();
    }

    private static java.awt.Color asAwt(com.jediterm.terminal.TerminalColor colour) {
        com.jediterm.core.Color drawn = colour.toColor();
        return new java.awt.Color(drawn.getRed(), drawn.getGreen(), drawn.getBlue());
    }

    private static TerminalTab.FindBar findBar(Locale locale) throws Exception {
        return onSwing(() -> new TerminalTab.FindBar(new Translations(locale)));
    }

    private static <T> T onSwing(java.util.function.Supplier<T> built) throws Exception {
        List<T> holder = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> holder.add(built.get()));
        return holder.get(0);
    }

    private static <T> T only(Container container, Class<T> kind) {
        List<T> found = childrenOf(container, kind);
        assertThat(found).as("one %s in the bar", kind.getSimpleName()).hasSize(1);
        return found.get(0);
    }

    private static <T> List<T> childrenOf(Container container, Class<T> kind) {
        List<T> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (kind.isInstance(child)) {
                found.add(kind.cast(child));
            }
            if (child instanceof Container nested) {
                found.addAll(childrenOf(nested, kind));
            }
        }
        return found;
    }
}
