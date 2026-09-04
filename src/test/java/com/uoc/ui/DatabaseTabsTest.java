package com.uoc.ui;

import com.uoc.docker.Database;
import com.uoc.docker.ProcessRunner;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Translations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("the tabs and which of them are showing")
class DatabaseTabsTest {

    private record Change(Database database, boolean shown) {
    }

    private final List<Change> changes = new ArrayList<>();
    private DatabaseTabs tabs;
    private JTabbedPane pane;

    @BeforeEach
    void buildTheTabs() throws Exception {
        ProcessRunner fakeProcess = (command, stdin) -> new ProcessRunner.Result(0, "");

        SwingUtilities.invokeAndWait(() -> {
            tabs = new DatabaseTabs(List.of(Database.values()), new QueryRunner(fakeProcess),
                    new Translations(Locale.ENGLISH));
            tabs.addVisibilityListener((database, shown) -> changes.add(new Change(database, shown)));
        });
        pane = tabs.getComponent();
    }

    private void onSwing(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private Database selected() {
        for (Database database : Database.values()) {
            if (tabs.isShown(database)
                    && pane.getSelectedComponent() == pane.getComponentAt(indexOf(database))) {
                return database;
            }
        }
        return null;
    }

    private int indexOf(Database database) {
        for (int i = 0; i < pane.getTabCount(); i++) {
            if (pane.getTitleAt(i).equals(database.displayName())) {
                return i;
            }
        }
        return -1;
    }

    @ParameterizedTest
    @EnumSource(Database.class)
    void theCourseDatabasesStartShowingAndTheOthersDoNot(Database database) {
        assertThat(tabs.isShown(database)).isEqualTo(database.isShownByDefault());
    }

    @Test
    void startingADatabaseThatIsHiddenOpensItsTabAndGoesToIt() throws Exception {
        assertThat(tabs.isShown(Database.RIAK)).isFalse();

        onSwing(() -> tabs.reveal(Database.RIAK));

        assertThat(tabs.isShown(Database.RIAK)).isTrue();
        assertThat(selected()).isEqualTo(Database.RIAK);
    }

    @Test
    void startingADatabaseThatIsAlreadyShowingLeavesTheStudentWhereTheyWere() throws Exception {
        // They asked for the service to run, not to be taken away from the tab they were
        // reading. Mongo is showing already, and Cassandra is where they are looking.
        onSwing(() -> tabs.select(Database.CASSANDRA));
        assertThat(selected()).isEqualTo(Database.CASSANDRA);

        onSwing(() -> tabs.reveal(Database.MONGO));

        assertThat(selected()).isEqualTo(Database.CASSANDRA);
    }

    @Test
    void revealingADatabaseThatIsAlreadyShowingAnnouncesNothing() throws Exception {
        changes.clear();

        onSwing(() -> tabs.reveal(Database.MONGO));

        assertThat(changes).isEmpty();
    }

    @Test
    void openingATabIsAnnouncedOnce() throws Exception {
        changes.clear();

        onSwing(() -> tabs.show(Database.REDIS));
        onSwing(() -> tabs.show(Database.REDIS));

        assertThat(changes).containsExactly(new Change(Database.REDIS, true));
    }

    @Test
    void closingATabIsAnnouncedOnce() throws Exception {
        changes.clear();

        onSwing(() -> tabs.hide(Database.MONGO));
        onSwing(() -> tabs.hide(Database.MONGO));

        assertThat(changes).containsExactly(new Change(Database.MONGO, false));
        assertThat(tabs.isShown(Database.MONGO)).isFalse();
    }

    @Test
    void aTabThatComesBackReturnsToItsPlaceInTheOrder() throws Exception {
        onSwing(() -> tabs.hide(Database.CASSANDRA));

        onSwing(() -> tabs.show(Database.CASSANDRA));

        // Cassandra sits between MongoDB and Neo4j in the declared order and must not end
        // up appended after them.
        assertThat(indexOf(Database.CASSANDRA)).isEqualTo(1);
    }

    @Test
    void aHiddenTabCannotBeSelected() throws Exception {
        Database before = selected();

        onSwing(() -> tabs.select(Database.RIAK));

        assertThat(selected()).isEqualTo(before);
    }

    @Test
    void everyDatabaseHasAConsoleWhetherOrNotItIsShowing() {
        for (Database database : Database.values()) {
            assertThat(tabs.tabFor(database.key()))
                    .as("%s has no console", database)
                    .isNotNull();
        }
    }
}
