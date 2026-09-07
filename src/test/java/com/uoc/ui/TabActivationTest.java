package com.uoc.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.uoc.docker.Database;
import com.uoc.i18n.Translations;

/**
 * Which tab the student is looking at, and when that counts as asking for a service.
 *
 * <p>
 * Bringing a tab to the front is how a student says they want to work with that database
 * now, so it starts the service. What must not count is the selection moving on its own:
 * opening a tab shifts the ones after it, and closing the front one hands the front to
 * whichever tab is next along. Taken at face value, putting Redis away would start
 * whatever happened to sit beside it -- which on a first run is a gigabyte nobody asked
 * for.
 */
@DisplayName("bringing a tab to the front")
class TabActivationTest {

    @BeforeAll
    static void installTheThemeThatHoldsOurColours() {
        ThemeForTests.install();
    }

    private final List<Database> activated = new ArrayList<>();
    private DatabaseTabs tabs;
    private JTabbedPane pane;

    @BeforeEach
    void buildTheTabs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            tabs = new DatabaseTabs(List.of(Database.values()),
                    new Translations(Locale.ENGLISH), () -> {
                    });
            tabs.addActivationListener(activated::add);
        });
        pane = tabs.getComponent();
    }

    private void onSwing(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    @Test
    void aStudentClickingATabSaysTheyWantThatService() throws Exception {
        // What the tab strip does when a tab is clicked, which is all a click amounts to
        // by the time it reaches us.
        onSwing(() -> pane.setSelectedIndex(indexOf(Database.CASSANDRA)));

        assertThat(activated).containsExactly(Database.CASSANDRA);
    }

    @Test
    void openingATabIsNotClickingOne() throws Exception {
        // Ticking a service in the menu opens its tab and starts it, once. If opening
        // also counted, the same service would be asked for twice.
        onSwing(() -> tabs.show(Database.REDIS));

        assertThat(activated).isEmpty();
    }

    @Test
    void bringingATabToTheFrontFromTheMenuIsNotClickingOneEither() throws Exception {
        onSwing(() -> tabs.reveal(Database.REDIS));

        assertThat(activated).isEmpty();
    }

    @Test
    void closingTheFrontTabDoesNotStartWhateverTakesItsPlace() throws Exception {
        // The one that made this worth a flag. Removing the selected tab moves the
        // selection to its neighbour, and Swing reports that exactly as it reports a
        // click.
        onSwing(() -> tabs.select(Database.CASSANDRA));
        activated.clear();

        onSwing(() -> tabs.hide(Database.CASSANDRA));

        assertThat(activated).isEmpty();
    }

    @Test
    void closingATabThatIsNotAtTheFrontStartsNothingEither() throws Exception {
        onSwing(() -> tabs.select(Database.NEO4J));
        activated.clear();

        onSwing(() -> tabs.hide(Database.MONGO));

        assertThat(activated).isEmpty();
    }

    @Test
    void aStudentCanStillClickAfterTheTabsHaveBeenRearranged() throws Exception {
        // The flag has to be put back down again, whatever happened while it was up.
        onSwing(() -> tabs.show(Database.REDIS));
        onSwing(() -> tabs.hide(Database.MONGO));
        activated.clear();

        onSwing(() -> pane.setSelectedIndex(indexOf(Database.REDIS)));

        assertThat(activated).containsExactly(Database.REDIS);
    }

    @Test
    void clickingTheTabAlreadyAtTheFrontChangesNothing() throws Exception {
        // Swing reports no change, so there is nothing to act on: a student who clicks
        // the tab they are already reading has not asked for anything.
        onSwing(() -> pane.setSelectedIndex(indexOf(Database.CASSANDRA)));
        activated.clear();

        onSwing(() -> pane.setSelectedIndex(indexOf(Database.CASSANDRA)));

        assertThat(activated).isEmpty();
    }

    @Test
    void everyTabAStudentClicksIsReportedAsItsOwnService() throws Exception {
        for (Database database : List.of(Database.MONGO, Database.CASSANDRA,
                Database.NEO4J, Database.JUPYTER)) {
            onSwing(() -> pane.setSelectedIndex(indexOf(database)));
        }

        assertThat(activated).containsExactly(Database.CASSANDRA, Database.NEO4J,
                Database.JUPYTER);
    }

    private int indexOf(Database database) {
        for (int i = 0; i < pane.getTabCount(); i++) {
            if (pane.getTitleAt(i).equals(database.displayName())) {
                return i;
            }
        }
        return -1;
    }
}
