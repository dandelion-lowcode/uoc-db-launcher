package com.uoc.ui;

import com.formdev.flatlaf.util.UIScale;
import com.uoc.docker.Database;
import com.uoc.docker.ProcessRunner;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Translations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Component;
import java.awt.Container;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Zoom exists so the interface can be read. The console is the part of it a student reads
 * most, and it sets its own font, so it does not follow the look and feel by itself.
 *
 * <p>What is checked here is that everything grows by the same amount, including the text
 * already printed, so a session never ends up half small and half large.
 */
@DisplayName("zooming the console")
class ConsoleZoomTest {

    @org.junit.jupiter.api.BeforeAll
    static void installTheThemeThatHoldsOurColours() {
        ThemeForTests.install();
    }

    private DatabaseTab tab;
    private JTextArea input;
    private JTextComponent session;
    private float originalZoom;

    @BeforeEach
    void buildTheTab() throws Exception {
        originalZoom = UIScale.getZoomFactor();
        ProcessRunner fakeProcess = (command, stdin) -> new ProcessRunner.Result(0, "");

        SwingUtilities.invokeAndWait(() -> {
            UIScale.setZoomFactor(1f);
            tab = new DatabaseTab(Database.MONGO, new QueryRunner(fakeProcess));
            tab.registerTranslations(new Translations(Locale.ENGLISH));
        });
        input = (JTextArea) find(DatabaseTab.INPUT);
        session = (JTextComponent) find(DatabaseTab.SESSION);
    }

    @AfterEach
    void restoreZoom() throws Exception {
        // The zoom is a setting of the whole toolkit, so leaving it changed would decide
        // what the next test sees.
        SwingUtilities.invokeAndWait(() -> UIScale.setZoomFactor(originalZoom));
    }

    private void zoomTo(float factor) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UIScale.setZoomFactor(factor);
            tab.applyZoom();
        });
    }

    private int consoleFontSize() {
        return session.getFont().getSize();
    }

    private int inputFontSize() {
        return input.getFont().getSize();
    }

    private int printedTextSize() {
        StyledDocument document = (StyledDocument) session.getDocument();
        return StyleConstants.getFontSize(
                document.getCharacterElement(0).getAttributes());
    }

    private void print(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> tab.showFailure(text));
    }

    @Test
    void zoomingInMakesTheConsoleBigger() throws Exception {
        int before = consoleFontSize();

        zoomTo(2f);

        assertThat(consoleFontSize()).isGreaterThan(before);
    }

    @Test
    void zoomingInMakesTheBoxYouTypeInBigger() throws Exception {
        int before = inputFontSize();

        zoomTo(2f);

        assertThat(inputFontSize()).isGreaterThan(before);
    }

    @Test
    void whatYouReadAndWhatYouTypeStayTheSameSize() throws Exception {
        zoomTo(1.5f);

        assertThat(consoleFontSize()).isEqualTo(inputFontSize());
    }

    @Test
    void textAlreadyPrintedGrowsWithTheRest() throws Exception {
        print("una respuesta anterior");
        int before = printedTextSize();

        zoomTo(2f);

        assertThat(printedTextSize())
                .as("the session would be half small and half large")
                .isGreaterThan(before);
        assertThat(printedTextSize()).isEqualTo(consoleFontSize());
    }

    @Test
    void textPrintedAfterZoomingComesOutAtTheNewSize() throws Exception {
        zoomTo(2f);

        print("una respuesta nueva");

        assertThat(printedTextSize()).isEqualTo(consoleFontSize());
    }

    @Test
    void zoomingOutMakesEverythingSmallerAgain() throws Exception {
        zoomTo(2f);
        int big = consoleFontSize();

        zoomTo(1f);

        assertThat(consoleFontSize()).isLessThan(big);
    }

    @Test
    void goingBackToNormalRestoresTheSizeItStartedAt() throws Exception {
        int original = consoleFontSize();
        zoomTo(2f);

        zoomTo(1f);

        assertThat(consoleFontSize()).isEqualTo(original);
    }

    @Test
    void theConsoleStaysMonospacedWhateverTheZoom() throws Exception {
        zoomTo(2f);
        print("columna");

        assertThat(session.getFont().getFamily()).isEqualTo("Monospaced");
        assertThat(StyleConstants.getFontFamily(
                ((StyledDocument) session.getDocument()).getCharacterElement(0).getAttributes()))
                .isEqualTo("Monospaced");
    }

    private Component find(String name) {
        Component found = find(tab.getPanel(), name);
        assertThat(found).as("no component named %s", name).isNotNull();
        return found;
    }

    private static Component find(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container inner) {
                Component found = find(inner, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
