package com.uoc.ui;

import com.uoc.docker.Database;
import com.uoc.docker.ProcessRunner;
import com.uoc.docker.QueryRunner;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The query console, driven through Swing itself rather than through the mouse and
 * keyboard.
 *
 * <p>Pressing real keys was tried and rejected: it needs a screen, it takes thirty times
 * longer, and it types whatever the machine's keyboard layout produces, so a query with
 * brackets in it arrived mangled on a Spanish keyboard. Invoking the same button and the
 * same key binding directly checks the behaviour that belongs to this class and leaves
 * the delivery of keystrokes to the toolkit, which is not ours to test.
 *
 * <p>Docker is not involved: the process is stood in for, so what is exercised is the
 * console and not a database.
 */
@DisplayName("the query console")
class DatabaseTabTest {

    @org.junit.jupiter.api.BeforeAll
    static void installTheThemeThatHoldsOurColours() {
        ThemeForTests.install();
    }

    private final List<String> commandsRun = new CopyOnWriteArrayList<>();
    private String reply = "";

    // Lets a test hold a query in flight for as long as it needs to look at the console
    // while the answer has not arrived. Without it the answer can land first and the test
    // passes or fails depending on which thread wins.
    private final CountDownLatch answerHeld = new CountDownLatch(1);
    private volatile boolean holdTheAnswer;

    private DatabaseTab tab;
    private JTextArea input;
    private JTextComponent session;
    private AbstractButton send;
    private AbstractButton clear;

    @BeforeEach
    void buildTheTab() throws Exception {
        ProcessRunner fakeProcess = (command, stdin) -> {
            commandsRun.add(String.join(" ", command));
            if (holdTheAnswer) {
                try {
                    answerHeld.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new ProcessRunner.Result(0, reply);
        };

        SwingUtilities.invokeAndWait(() -> {
            tab = new DatabaseTab(Database.MONGO, new QueryRunner(fakeProcess));
            tab.registerTranslations(new Translations(Locale.ENGLISH));
        });

        input = (JTextArea) find(DatabaseTab.INPUT);
        session = (JTextComponent) find(DatabaseTab.SESSION);
        send = (AbstractButton) find(DatabaseTab.SEND);
        clear = (AbstractButton) find(DatabaseTab.CLEAR);
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

    private void onSwing(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    /** Waits for the answer, which arrives from another thread and lands on this one. */
    private void awaitReply() throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            SwingUtilities.invokeAndWait(() -> {
            });
            if (send.isEnabled()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the answer never arrived; the console shows <"
                + session.getText() + ">");
    }

    /** Waits until the query has actually reached the process and is being answered. */
    private void awaitQueryToBeInFlight() throws Exception {
        for (int attempt = 0; attempt < 200 && commandsRun.isEmpty(); attempt++) {
            Thread.sleep(10);
        }
        assertThat(commandsRun).as("the query never reached the process").isNotEmpty();
    }

    private void type(String query) throws Exception {
        onSwing(() -> {
            tab.setSendEnabled(true);
            input.setText(query);
        });
    }

    /**
     * The shortcut for sending, whichever key this system uses for its menu shortcuts.
     * Asked of the toolkit rather than written down, so this reads as Command on a Mac
     * and as Control everywhere else, exactly as the application does.
     */
    private static KeyStroke sendShortcut() {
        return KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
    }

    private void pressCtrlEnter() throws Exception {
        InputMap inputMap = input.getInputMap();
        Object binding = inputMap.get(sendShortcut());
        assertThat(binding).as("the send shortcut is not bound to anything").isNotNull();

        ActionMap actionMap = input.getActionMap();
        assertThat(actionMap.get(binding))
                .as("the send shortcut is bound to a missing action").isNotNull();

        onSwing(() -> actionMap.get(binding).actionPerformed(
                new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "send")));
    }

    @Test
    void theSendButtonIsOffUntilTheServiceIsReady() throws Exception {
        // Sending to a database that is not running could only produce an error, so the
        // button says so before the student finds out the hard way.
        onSwing(() -> tab.setSendEnabled(false));
        assertThat(send.isEnabled()).isFalse();

        onSwing(() -> tab.setSendEnabled(true));
        assertThat(send.isEnabled()).isTrue();
    }

    @Test
    void aQuerySentWithTheButtonComesBackInTheConsole() throws Exception {
        reply = "resultado de la consulta";
        type("db.libros.find({autor:\"Borges\"})");

        onSwing(send::doClick);
        awaitReply();

        assertThat(session.getText()).contains("resultado de la consulta");
        assertThat(commandsRun).hasSize(1);
        assertThat(commandsRun.get(0))
                .contains("uocdb-mongo")
                .contains("db.libros.find({autor:\"Borges\"})");
    }

    @Test
    void aQuerySentWithControlEnterComesBackTheSameWay() throws Exception {
        reply = "enviado con el teclado";
        type("show dbs");

        pressCtrlEnter();
        awaitReply();

        assertThat(session.getText()).contains("enviado con el teclado");
        assertThat(commandsRun).hasSize(1);
    }

    @Test
    void theQueryIsEchoedSoTheSessionReadsAsAConversation() throws Exception {
        reply = "respuesta";
        type("show dbs");

        onSwing(send::doClick);
        awaitReply();

        assertThat(session.getText()).contains("> show dbs").contains("respuesta");
        assertThat(session.getText().indexOf("> show dbs"))
                .isLessThan(session.getText().indexOf("respuesta"));
    }

    @Test
    void sendingEmptiesTheBoxReadyForTheNextQuery() throws Exception {
        reply = "algo";
        type("show dbs");

        onSwing(send::doClick);
        awaitReply();

        assertThat(input.getText()).isEmpty();
    }

    @Test
    void theButtonIsOffWhileTheAnswerIsOnItsWay() throws Exception {
        // Otherwise a student can queue several queries by clicking again, and the answers
        // arrive interleaved with no way to tell which belongs to which.
        reply = "respuesta";
        type("show dbs");

        onSwing(send::doClick);

        awaitReply();
        assertThat(commandsRun).hasSize(1);
    }

    @Test
    void theServiceBeingReportedHealthyDoesNotReopenTheConsoleMidQuery() throws Exception {
        // The status is polled every few seconds and reports the service healthy, which is
        // true and beside the point: a query is still on its way and the console must not
        // accept another one until its answer is in.
        reply = "respuesta";
        holdTheAnswer = true;
        type("show dbs");
        onSwing(send::doClick);
        awaitQueryToBeInFlight();

        onSwing(() -> tab.setSendEnabled(true));

        assertThat(send.isEnabled())
                .as("the console reopened while a query was still running")
                .isFalse();

        answerHeld.countDown();
        awaitReply();
        assertThat(commandsRun).hasSize(1);
    }

    @Test
    void theConsoleOpensAgainOnceTheAnswerIsIn() throws Exception {
        reply = "respuesta";
        type("show dbs");
        onSwing(send::doClick);
        awaitReply();

        assertThat(send.isEnabled()).isTrue();
    }

    @Test
    void aServiceThatStopsClosesTheConsoleEvenBetweenQueries() throws Exception {
        onSwing(() -> tab.setSendEnabled(true));

        onSwing(() -> tab.setSendEnabled(false));

        assertThat(send.isEnabled()).isFalse();
    }

    @Test
    void anEmptyQueryIsNotSent() throws Exception {
        type("   ");

        onSwing(send::doClick);

        assertThat(commandsRun).isEmpty();
        // Nothing was echoed either: a sent query is written into the trace behind a
        // "> ", and whitespace never gets that far.
        assertThat(session.getText()).doesNotContain("> ");
    }

    @Test
    void aQueryIsNotSentWhileTheServiceIsNotReady() throws Exception {
        onSwing(() -> {
            tab.setSendEnabled(false);
            input.setText("show dbs");
        });

        pressCtrlEnter();

        // The button being off has to stop the keyboard too, or the shortcut is a way
        // around it.
        assertThat(commandsRun).isEmpty();
    }

    @Test
    void clearingTakesTheSessionAwayAndPutsTheExampleBack() throws Exception {
        reply = "algo que borrar";
        type("show dbs");
        onSwing(send::doClick);
        awaitReply();
        assertThat(session.getText()).contains("algo que borrar");

        onSwing(clear::doClick);

        // An empty box says nothing about what to type into it, so clearing leaves the
        // console the way it opened rather than blank.
        assertThat(session.getText()).doesNotContain("algo que borrar");
        assertThat(session.getText()).contains("show databases");
    }

    @Test
    void theConsoleOpensShowingAQueryForThisDatabaseAndTheAnswerItGives() {
        // Six databases, six query languages. An empty box says nothing about which one
        // this tab expects; a worked example says it in two lines.
        assertThat(session.getText())
                .contains("show databases")
                .contains("admin")
                .contains("config");
    }

    @Test
    void theFirstRealAnswerReplacesTheExampleRatherThanFollowingIt() throws Exception {
        reply = "la respuesta de verdad";

        type("show dbs");
        onSwing(send::doClick);
        awaitReply();

        assertThat(session.getText()).contains("la respuesta de verdad");
        assertThat(session.getText())
                .as("the example's answer must not still be sitting above it")
                .doesNotContain("'admin'");
    }

    @Test
    void aFailureFromDockerIsShownInTheConsole() throws Exception {
        onSwing(() -> tab.showFailure("no such image: mongo:latest"));

        assertThat(session.getText()).contains("no such image: mongo:latest");
    }

    @Test
    void theButtonsAreLabelledInTheChosenLanguage() throws Exception {
        Translations spanish = new Translations(Locale.of("es"));

        onSwing(() -> tab.registerTranslations(spanish));

        assertThat(send.getText()).isEqualTo(spanish.get(Message.BUTTON_SEND));
        assertThat(clear.getText()).isEqualTo(spanish.get(Message.BUTTON_CLEAR));
    }

    @Test
    void changingLanguageRelabelsTheButtonsThatAreAlreadyOnScreen() throws Exception {
        Translations translations = new Translations(Locale.ENGLISH);
        onSwing(() -> tab.registerTranslations(translations));
        String english = send.getText();

        onSwing(() -> translations.setLocale(Locale.of("ca")));

        assertThat(send.getText()).isNotEqualTo(english);
    }
}
