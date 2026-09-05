package com.uoc.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.uoc.docker.Database;
import com.uoc.docker.ProcessRunner;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Translations;

/**
 * What the launcher remembers about a student between sessions.
 *
 * <p>
 * A scratch node of the preference store is used rather than the real one, so a
 * test run
 * cannot reach into whatever the person at this machine had chosen. It is
 * emptied
 * afterwards.
 */
class RememberedSettingsTest {

    @org.junit.jupiter.api.BeforeAll
    static void installTheThemeThatHoldsOurColours() {
        ThemeForTests.install();
    }

    private Preferences prefs;

    @BeforeEach
    void useAScratchStore() {
        prefs = Preferences.userRoot().node("uocdb-test-" + System.nanoTime());
    }

    @AfterEach
    void throwItAway() throws Exception {
        prefs.removeNode();
    }

    private DatabaseTabs tabsUsing(Preferences preferences) throws Exception {
        ProcessRunner fake = (command, stdin) -> new ProcessRunner.Result(0, "");
        DatabaseTabs[] built = new DatabaseTabs[1];
        SwingUtilities.invokeAndWait(() -> built[0] = new DatabaseTabs(
                List.of(Database.values()), new QueryRunner(fake),
                new Translations(Locale.ENGLISH), () -> {
                }, preferences));
        return built[0];
    }

    @Test
    void theFirstRunOpensTheServicesTheCourseStartsWith() throws Exception {
        DatabaseTabs tabs = tabsUsing(prefs);

        for (Database database : Database.values()) {
            assertThat(tabs.isShown(database))
                    .as("%s", database)
                    .isEqualTo(database.isShownByDefault());
        }
    }

    @Test
    void theServicesLeftOpenAreTheOnesOpenNextTime() throws Exception {
        // A student working through the Cassandra exercises should not have to tick it
        // again every time they open the launcher.
        DatabaseTabs first = tabsUsing(prefs);
        SwingUtilities.invokeAndWait(() -> {
            first.show(Database.RIAK);
            first.hide(Database.MONGO);
        });

        DatabaseTabs second = tabsUsing(prefs);

        assertThat(second.isShown(Database.RIAK)).isTrue();
        assertThat(second.isShown(Database.MONGO)).isFalse();
    }

    @Test
    void aStudentWhoClosesEverythingGetsNothingBack() throws Exception {
        // Not the three defaults: they asked for none, and answering with three would
        // read as the setting being ignored.
        DatabaseTabs first = tabsUsing(prefs);
        SwingUtilities.invokeAndWait(() -> {
            for (Database database : Database.values()) {
                first.hide(database);
            }
        });

        DatabaseTabs second = tabsUsing(prefs);

        for (Database database : Database.values()) {
            assertThat(second.isShown(database)).as("%s", database).isFalse();
        }
    }

    @Test
    void theNotebooksAreRememberedLikeAnyOtherService() throws Exception {
        DatabaseTabs first = tabsUsing(prefs);
        SwingUtilities.invokeAndWait(() -> first.show(Database.JUPYTER));

        assertThat(tabsUsing(prefs).isShown(Database.JUPYTER)).isTrue();
    }

    @Test
    void aServiceThisVersionNoLongerHasIsSkippedRatherThanFailing() throws Exception {
        // The list is a convenience. A database renamed between versions must not stop
        // the application from opening.
        prefs.put("shownServices", "mongo,una-que-ya-no-existe,redis");

        DatabaseTabs tabs = tabsUsing(prefs);

        assertThat(tabs.isShown(Database.MONGO)).isTrue();
        assertThat(tabs.isShown(Database.REDIS)).isTrue();
    }

    @Test
    void nothingIsRememberedWhenThereIsNowhereToRememberIt() throws Exception {
        // Which is what the tests that do not care about this pass, and what keeps them
        // from writing into the store of whoever is at this machine.
        DatabaseTabs tabs = tabsUsing(null);

        SwingUtilities.invokeAndWait(() -> tabs.show(Database.RIAK));

        assertThat(tabs.isShown(Database.RIAK)).isTrue();
    }
}
