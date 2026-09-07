package com.uoc.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.uoc.docker.Database;
import com.uoc.docker.InstallProgress;
import com.uoc.i18n.Translations;

/**
 * What a student watches while an image is being fetched.
 *
 * <p>
 * Driven by what Compose actually wrote during a real pull, captured down a pipe -- which
 * is how the launcher reads it -- and kept in {@code install/compose-pull-redis.txt}.
 * Compose sends no carriage returns and no escape sequences that way: eighty plain lines,
 * each naming one layer, which between them describe eleven things whose state keeps
 * changing.
 *
 * <p>
 * So the whole of the display is ours to draw, and the first attempt drew it wrongly in
 * two ways at once. It appended the block instead of redrawing it, once per update, and
 * it separated the lines with a bare line feed, which moves the cursor down without
 * bringing it back to the first column. Eighty updates of eleven lines produced eight
 * hundred and eighty lines of staircase running off the right-hand edge.
 */
@DisplayName("watching an image being fetched")
class InstallDisplayTest {

    @BeforeAll
    static void installTheThemeThatHoldsOurColours() {
        ThemeForTests.install();
    }

    private static final String CAPTURE = "install/compose-pull-redis.txt";

    @Test
    void aRealPullDescribesElevenThingsHoweverManyLinesItTakesToSayIt() throws Exception {
        InstallProgress progress = new InstallProgress();

        for (String line : capturedPull()) {
            progress.accept(line);
        }

        // One for the image, seven layers it is made of, two more the compose file pulls
        // alongside, and the container itself.
        assertThat(progress.text().lines()).hasSize(11);
        assertThat(progress.text().lines().toList())
                .as("the block keeps the order the work was announced in")
                .satisfies(block -> {
                    assertThat(block.get(0)).startsWith("Image redis");
                    assertThat(block.get(block.size() - 1)).startsWith("Container uocdb-redis");
                });
    }

    @Test
    void everyLineIsShortEnoughToReadWithoutWrapping() throws Exception {
        // The staircase made these look enormous. They are not: the widest is a third of
        // a standard terminal, so nothing has to be truncated to fit.
        InstallProgress progress = new InstallProgress();
        capturedPull().forEach(progress::accept);

        assertThat(progress.text().lines().mapToInt(String::length).max().orElse(0))
                .isLessThan(80);
    }

    @Test
    void theWholePullLeavesOneBlockOnScreenRatherThanEightHundredLines() throws Exception {
        TerminalTab tab = terminalTab();
        InstallProgress progress = new InstallProgress();

        for (String line : capturedPull()) {
            progress.accept(line);
            String text = progress.text();
            onSwing(() -> tab.showInstallProgress(text));
        }

        String screen = tab.screenText();
        assertThat(screen.lines().filter(line -> line.contains("Container uocdb-redis")))
                .as("the container was reported once, not once per update")
                .hasSize(1);
        assertThat(screen.lines().filter(line -> line.contains("redis:8.10.1"))).hasSize(1);
    }

    @Test
    void everyLineStartsAtTheLeftEdgeRatherThanWhereTheOneAboveItEnded() throws Exception {
        // The line feed on its own is what drew the staircase. Each line of the block
        // has to begin in the first column.
        TerminalTab tab = terminalTab();
        InstallProgress progress = new InstallProgress();
        capturedPull().forEach(progress::accept);

        String text = progress.text();
        onSwing(() -> tab.showInstallProgress(text));

        assertThat(tab.screenText().lines().filter(line -> !line.isBlank()))
                .allSatisfy(line -> assertThat(line).doesNotStartWith(" "));
    }

    @Test
    void whatIsOnScreenIsWhatTheProgressNowSaysAndNotWhatItSaidBefore() throws Exception {
        TerminalTab tab = terminalTab();

        onSwing(() -> tab.showInstallProgress("b1de9b9d5a81 Downloading 1.2MB/45MB"));
        onSwing(() -> tab.showInstallProgress("b1de9b9d5a81 Downloading 44MB/45MB"));

        assertThat(tab.screenText()).contains("44MB/45MB").doesNotContain("1.2MB");
    }

    @Test
    void aFinishedDownloadLeavesAClearScreenForTheClientToOpenOn() throws Exception {
        TerminalTab tab = terminalTab();
        InstallProgress progress = new InstallProgress();
        capturedPull().forEach(progress::accept);
        String text = progress.text();

        onSwing(() -> tab.showInstallProgress(text));
        onSwing(() -> tab.endInstallProgress(true));

        assertThat(tab.screenText()).isBlank();
    }

    @Test
    void andTheNextThingWrittenStartsAtTheTopRatherThanBelowTheGap() throws Exception {
        // Clearing without bringing the cursor back leaves the client's prompt eleven
        // lines down, under a screenful of nothing.
        TerminalTab tab = terminalTab();
        InstallProgress progress = new InstallProgress();
        capturedPull().forEach(progress::accept);
        String text = progress.text();

        onSwing(() -> tab.showInstallProgress(text));
        onSwing(() -> tab.endInstallProgress(true));
        onSwing(() -> tab.showFailure("redis> "));

        List<String> lines = tab.screenText().lines().toList();
        int printedAt = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("redis>")) {
                printedAt = i;
                break;
            }
        }

        assertThat(printedAt).as("nothing was printed at all").isNotEqualTo(-1);
        assertThat(printedAt).as("printed at line %d, so the screen was cleared from"
                + " underneath rather than from the top", printedAt)
                .isLessThanOrEqualTo(2);
    }

    @Test
    void aDownloadThatFailedKeepsWhatItPrinted() throws Exception {
        // The only account of why nothing started.
        TerminalTab tab = terminalTab();

        onSwing(() -> tab.showInstallProgress("Image redis:8.10.1 Error"));
        onSwing(() -> tab.endInstallProgress(false));

        assertThat(tab.screenText()).contains("Error");
    }

    private static TerminalTab terminalTab() throws Exception {
        TerminalTab[] built = new TerminalTab[1];
        SwingUtilities.invokeAndWait(() ->
                built[0] = new TerminalTab(Database.REDIS, new Translations(Locale.ENGLISH)));
        return built[0];
    }

    private static void onSwing(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    /** What Compose wrote, line by line, during a real fetch of the Redis image. */
    private static List<String> capturedPull() throws IOException {
        try (InputStream stream = InstallDisplayTest.class.getClassLoader()
                .getResourceAsStream(CAPTURE)) {
            assertThat(stream).as("the captured pull is missing from the test resources")
                    .isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
