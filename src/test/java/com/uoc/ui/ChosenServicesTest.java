package com.uoc.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JLabel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.uoc.docker.Database;
import com.uoc.docker.ServiceStatus;
import com.uoc.i18n.Translations;

/**
 * A service is listed beside the tabs if and only if the student has chosen it.
 *
 * <p>
 * With one exception, which is the part worth testing: a service unchosen while something
 * is still happening to it keeps its row until that has finished. A stop takes time, and
 * an image already downloading cannot be called back -- a row that vanished on the click
 * would take with it the only account of a download that is still running.
 */
@DisplayName("which services the panel lists")
class ChosenServicesTest {

    @BeforeAll
    static void installTheThemeThatHoldsOurColours() {
        ThemeForTests.install();
    }

    private ServicesPanel panel;

    @BeforeEach
    void aPanelNobodyHasChosenAnythingIn() {
        panel = new ServicesPanel(List.of(Database.values()), key -> {
        }, key -> {
        }, new Translations(Locale.ENGLISH));
    }

    private void choose(Database database) {
        panel.setChosen(database.key(), true);
    }

    private void unchoose(Database database) {
        panel.setChosen(database.key(), false);
    }

    private void reportedAs(Database database, ServiceStatus status) {
        panel.updateStatus(database.key(), status);
    }

    @Test
    void aPanelStartsListingNothingUntilItIsToldWhatWasChosen() {
        assertThat(listed()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Database.class)
    void whatIsChosenIsListed(Database database) {
        choose(database);

        assertThat(listed()).containsExactly(database.displayName());
    }

    @Test
    void whatIsUnchosenAndIdleGoesAtOnce() {
        // Nothing was ever started, so there is nothing to wait for.
        choose(Database.REDIS);

        unchoose(Database.REDIS);

        assertThat(listed()).isEmpty();
    }

    @Test
    void unchoosingARunningServiceKeepsItUntilItHasActuallyStopped() {
        // The one a student sees. Unchoosing a healthy service is what asks it to stop,
        // and at that moment the stop has not even been sent: it is not "stopping" yet.
        // Read as settled, the row vanished on the click and the container went down
        // with nothing on screen to say so, which looked like a menu that did nothing.
        choose(Database.REDIS);
        reportedAs(Database.REDIS, ServiceStatus.HEALTHY);

        unchoose(Database.REDIS);

        assertThat(listed()).containsExactly("Redis");
    }

    @Test
    void andGoesWhenThatStopArrives() {
        choose(Database.REDIS);
        reportedAs(Database.REDIS, ServiceStatus.HEALTHY);
        unchoose(Database.REDIS);

        reportedAs(Database.REDIS, ServiceStatus.STOPPING);
        assertThat(listed()).as("still on its way down").containsExactly("Redis");

        reportedAs(Database.REDIS, ServiceStatus.STOPPED);
        assertThat(listed()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ServiceStatus.class, names = { "RUNNING", "HEALTHY", "UNHEALTHY",
            "PAUSED", "RESTARTING", "STARTING", "INSTALLING", "STOPPING" })
    void anythingStillUpOrStillBusyKeepsItsRowWhenUnchosen(ServiceStatus busy) {
        choose(Database.REDIS);
        reportedAs(Database.REDIS, busy);

        unchoose(Database.REDIS);

        assertThat(listed()).as("%s is not somewhere a service can be left", busy)
                .containsExactly("Redis");
    }

    @Test
    void aServiceThatIsStoppingIsStillListedUntilItHasStopped() {
        choose(Database.REDIS);
        reportedAs(Database.REDIS, ServiceStatus.STOPPING);

        unchoose(Database.REDIS);

        assertThat(listed()).as("the stop is still happening").containsExactly("Redis");
    }

    @Test
    void andGoesOnceItHas() {
        choose(Database.REDIS);
        reportedAs(Database.REDIS, ServiceStatus.STOPPING);
        unchoose(Database.REDIS);

        reportedAs(Database.REDIS, ServiceStatus.STOPPED);

        assertThat(listed()).isEmpty();
    }

    @Test
    void aDownloadKeepsItsRowEvenAfterBeingUnchosen() {
        // The pull cannot be called back: Docker will finish it whether or not anybody is
        // watching, and a student who cannot see it will think the launcher has hung.
        choose(Database.VERTICA);
        reportedAs(Database.VERTICA, ServiceStatus.INSTALLING);

        unchoose(Database.VERTICA);

        assertThat(listed()).containsExactly("Vertica");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ServiceStatus.class, names = { "STOPPED", "CRASHED", "ERROR",
            "OUT_OF_MEMORY" })
    void anythingThatIsNoLongerHappeningLetsTheRowGo(ServiceStatus settled) {
        // Not STOPPED alone. A stop that fails, or a container that was already dead,
        // would otherwise leave an unchosen row that nothing could ever clear.
        choose(Database.REDIS);
        reportedAs(Database.REDIS, ServiceStatus.STOPPING);
        unchoose(Database.REDIS);

        reportedAs(Database.REDIS, settled);

        assertThat(listed()).isEmpty();
    }

    @Test
    void choosingItAgainWhileItIsLeavingKeepsIt() {
        choose(Database.REDIS);
        reportedAs(Database.REDIS, ServiceStatus.STOPPING);
        unchoose(Database.REDIS);

        choose(Database.REDIS);
        reportedAs(Database.REDIS, ServiceStatus.STOPPED);

        assertThat(listed()).as("it was asked for again before it finished leaving")
                .containsExactly("Redis");
    }

    @Test
    void aServiceThatComesBackReturnsToItsOwnPlaceInTheOrder() {
        choose(Database.MONGO);
        choose(Database.CASSANDRA);
        choose(Database.NEO4J);

        unchoose(Database.CASSANDRA);
        choose(Database.CASSANDRA);

        assertThat(listed()).containsExactly("MongoDB", "Cassandra", "Neo4j");
    }

    @Test
    void aServiceStoppingOnItsOwnKeepsItsRowBecauseItIsStillChosen() {
        // Stopping something from its own button is not unchoosing it: the student keeps
        // their place, and the row and the tab stay where they were.
        choose(Database.REDIS);

        reportedAs(Database.REDIS, ServiceStatus.STOPPING);
        reportedAs(Database.REDIS, ServiceStatus.STOPPED);

        assertThat(listed()).containsExactly("Redis");
    }

    @Test
    void anEmptyPanelSaysWhereToGetAServiceBack() {
        // An empty rectangle under a heading reads as a fault rather than as a choice.
        Translations english = new Translations(Locale.ENGLISH);
        ServicesPanel empty = new ServicesPanel(List.of(Database.values()), key -> {
        }, key -> {
        }, english);

        assertThat(textOf(empty)).anySatisfy(
                text -> assertThat(text).contains("Services").contains("menu"));
    }

    /**
     * The names of the services with a row, in the order the rows are laid out in rather
     * than in the order the enum declares -- which is what makes the ordering test mean
     * something.
     */
    private List<String> listed() {
        List<String> names = java.util.Arrays.stream(Database.values())
                .map(Database::displayName).toList();
        return textOf(panel).stream().filter(names::contains).toList();
    }

    private static List<String> textOf(ServicesPanel panel) {
        List<String> text = new ArrayList<>();
        collect(panel.getComponent(), text);
        return text;
    }

    private static void collect(Container container, List<String> text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                text.add(label.getText());
            }
            if (child instanceof Container nested) {
                collect(nested, text);
            }
        }
    }
}
