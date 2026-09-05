package com.uoc.ui;

import com.uoc.docker.Database;
import com.uoc.docker.ServiceStatus;
import com.uoc.i18n.Translations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The panel beside the tabs, driven through its own API rather than through Docker.
 *
 * <p>
 * What is checked here is what a student reads while waiting: that a download says so and
 * keeps moving, and that nothing else picked up the trailing dots.
 */
class ServicesPanelTest {

    @org.junit.jupiter.api.BeforeAll
    static void installTheThemeThatHoldsOurColours() {
        ThemeForTests.install();
    }

    private Translations translations;
    private ServicesPanel panel;

    @BeforeEach
    void buildThePanel() {
        translations = new Translations(Locale.of("es"));
        panel = new ServicesPanel(List.of(Database.values()), key -> {
        }, key -> {
        }, translations);
    }

    private String statusTextOf(Database database) {
        JLabel label = labelShowingStatusOf(database);
        return label == null ? null : label.getText();
    }

    /**
     * The status label sits immediately after the one holding the service's name, which
     * is what identifies it without the layout having to be reproduced here.
     */
    private JLabel labelShowingStatusOf(Database database) {
        List<JLabel> labels = new java.util.ArrayList<>();
        collectLabels(panel.getComponent(), labels);
        for (int i = 0; i < labels.size() - 1; i++) {
            if (database.displayName().equals(labels.get(i).getText())) {
                return labels.get(i + 1);
            }
        }
        return null;
    }

    private static void collectLabels(Container root, List<JLabel> found) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) {
                found.add(label);
            }
            if (child instanceof Container container) {
                collectLabels(container, found);
            }
        }
    }

    @Test
    void aServiceBeingInstalledSaysSo() {
        panel.updateStatus(Database.MONGO.key(), ServiceStatus.INSTALLING);

        assertThat(statusTextOf(Database.MONGO)).startsWith("Instalando");
    }

    @Test
    void theDotsKeepMovingWhileSomethingInstalls() {
        // A download can run for minutes. A label that never changed would be
        // indistinguishable from a launcher that had stopped responding, which is the
        // whole reason this animates.
        panel.updateStatus(Database.NEO4J_TWITTER.key(), ServiceStatus.INSTALLING);
        String first = statusTextOf(Database.NEO4J_TWITTER);

        boolean changed = false;
        for (int i = 0; i < 200 && !changed; i++) {
            sleepBriefly();
            changed = !first.equals(statusTextOf(Database.NEO4J_TWITTER));
        }

        assertThat(changed).as("the label never moved").isTrue();
        assertThat(statusTextOf(Database.NEO4J_TWITTER)).startsWith("Instalando");
    }

    @Test
    void theDotsNeverGrowBeyondThree() {
        panel.updateStatus(Database.MONGO.key(), ServiceStatus.INSTALLING);

        for (int i = 0; i < 40; i++) {
            String text = statusTextOf(Database.MONGO);
            assertThat(text).matches("Instalando\\.{0,3}");
            sleepBriefly();
        }
    }

    @Test
    void aServiceThatFinishesInstallingStopsSayingItIs() {
        panel.updateStatus(Database.MONGO.key(), ServiceStatus.INSTALLING);

        panel.updateStatus(Database.MONGO.key(), ServiceStatus.HEALTHY);

        assertThat(statusTextOf(Database.MONGO)).doesNotStartWith("Instalando").doesNotContain("..");
    }

    @Test
    void everyOtherStatusIsWrittenPlainlyWithoutDots() {
        for (ServiceStatus status : ServiceStatus.values()) {
            if (status == ServiceStatus.INSTALLING) {
                continue;
            }
            panel.updateStatus(Database.REDIS.key(), status);

            assertThat(statusTextOf(Database.REDIS))
                    .as("%s should not trail dots", status)
                    .doesNotEndWith(".");
        }
    }

    @Test
    void aSecondServiceCanBeInstallingAtTheSameTime() {
        panel.updateStatus(Database.MONGO.key(), ServiceStatus.INSTALLING);
        panel.updateStatus(Database.REDIS.key(), ServiceStatus.INSTALLING);

        assertThat(statusTextOf(Database.MONGO)).startsWith("Instalando");
        assertThat(statusTextOf(Database.REDIS)).startsWith("Instalando");
    }

    @Test
    void changingLanguageWhileInstallingKeepsSayingItIsInstalling() {
        panel.updateStatus(Database.MONGO.key(), ServiceStatus.INSTALLING);

        translations.setLocale(Locale.ENGLISH);

        assertThat(statusTextOf(Database.MONGO)).startsWith("Installing");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
