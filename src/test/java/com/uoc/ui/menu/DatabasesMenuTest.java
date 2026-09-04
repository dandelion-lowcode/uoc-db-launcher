package com.uoc.ui.menu;

import com.uoc.docker.Database;
import com.uoc.docker.ProcessRunner;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Translations;
import com.uoc.ui.DatabaseTabs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("the menu of available databases")
class DatabasesMenuTest {

    private final List<String> started = new ArrayList<>();
    private final List<String> stopped = new ArrayList<>();

    private DatabaseTabs tabs;
    private JMenu menu;

    @BeforeEach
    void buildTheMenu() throws Exception {
        ProcessRunner fakeProcess = (command, stdin) -> new ProcessRunner.Result(0, "");

        SwingUtilities.invokeAndWait(() -> {
            Translations translations = new Translations(Locale.ENGLISH);
            tabs = new DatabaseTabs(List.of(Database.values()),
                    new QueryRunner(fakeProcess), translations);
            menu = DatabasesMenu.build(tabs, started::add, stopped::add, translations);
        });
    }

    private JCheckBoxMenuItem itemFor(Database database) {
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JCheckBoxMenuItem item
                    && database.key().equals(item.getName())) {
                return item;
            }
        }
        throw new AssertionError("no menu item for " + database);
    }

    private void onSwing(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    @ParameterizedTest
    @EnumSource(Database.class)
    void everyDatabaseHasAnEntryThatStartsOutAgreeingWithItsTab(Database database) {
        assertThat(itemFor(database).isSelected()).isEqualTo(tabs.isShown(database));
    }

    @Test
    void theOnesTheCourseDoesNotUseAreGroupedApart() {
        List<Component> components = List.of(menu.getMenuComponents());

        assertThat(components).anyMatch(JSeparator.class::isInstance);
    }

    @Test
    void tickingAnEntryOpensItsTabAndStartsTheService() throws Exception {
        JCheckBoxMenuItem riak = itemFor(Database.RIAK);

        // doClick already flips the tick, which is what the student's click does.
        onSwing(riak::doClick);

        assertThat(tabs.isShown(Database.RIAK)).isTrue();
        assertThat(started).contains(Database.RIAK.key());
    }

    @Test
    void untickingAnEntryClosesItsTabAndStopsTheService() throws Exception {
        JCheckBoxMenuItem mongo = itemFor(Database.MONGO);

        onSwing(mongo::doClick);

        assertThat(tabs.isShown(Database.MONGO)).isFalse();
        assertThat(stopped).contains(Database.MONGO.key());
    }

    @Test
    void startingAServiceFromElsewhereTicksItsEntry() throws Exception {
        // The play button beside the services opens the tab. The menu is a report of what
        // is showing, so it has to agree without the student touching it.
        assertThat(itemFor(Database.RIAK).isSelected()).isFalse();

        onSwing(() -> tabs.reveal(Database.RIAK));

        assertThat(itemFor(Database.RIAK).isSelected()).isTrue();
    }

    @Test
    void closingATabFromElsewhereUnticksItsEntry() throws Exception {
        onSwing(() -> tabs.hide(Database.MONGO));

        assertThat(itemFor(Database.MONGO).isSelected()).isFalse();
    }

    @Test
    void tickingTheEntryOfADatabaseAlreadyShowingDoesNotStartItTwice() throws Exception {
        onSwing(() -> tabs.reveal(Database.RIAK));
        started.clear();

        JCheckBoxMenuItem riak = itemFor(Database.RIAK);
        assertThat(riak.isSelected()).isTrue();

        assertThat(started).isEmpty();
    }
}
