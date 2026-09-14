package com.uoc.docker;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import java.awt.Desktop;
import java.util.List;

public class DockerAvailability {

    private static final String INSTALL_URL = "https://docs.docker.com/get-started/get-docker/";

    /**
     * How wide the dialog's text is allowed to be, before the screen scales it. Wide
     * enough for a sentence to sit on one line and narrow enough to read the next one.
     */
    private static final int MESSAGE_WIDTH = 420;

    private DockerAvailability() {
    }

    /** The three answers there are to "is there a Docker to talk to". */
    public enum Status {
        /** The daemon answered. */
        RUNNING,
        /** The command ran and the daemon did not answer: installed, not started. */
        NOT_STARTED,
        /** There is no "docker" command on the PATH to run at all. */
        NOT_FOUND
    }

    public static boolean isRunning() {
        return isRunning(new SystemProcessRunner());
    }

    public static Status status() {
        return status(new SystemProcessRunner());
    }

    /**
     * Whether the daemon answers, asked of something other than this machine's Docker.
     *
     * @param runner what runs the command
     */
    static boolean isRunning(ProcessRunner runner) {
        return status(runner) == Status.RUNNING;
    }

    /**
     * The same question, answered in three ways rather than two.
     *
     * <p>
     * "docker info" is the question because it is the one command that needs the daemon
     * rather than only the executable: a machine with Docker installed and not started
     * answers every other question happily, and that is the state a student is most often
     * in when they open the launcher.
     *
     * <p>
     * A machine with Docker Desktop plainly running on screen and no "docker" on the PATH
     * fails here exactly as a machine with Docker closed does, and the two ask opposite
     * things of the student: one has to start Docker, the other has to end the session
     * that was open before Docker was installed, since that session never learnt the new
     * PATH. Telling the second that Docker is not running is what sends them looking in
     * the wrong place -- it cost a colleague an afternoon and an email.
     *
     * @param runner what runs the command
     */
    static Status status(ProcessRunner runner) {
        try {
            ProcessRunner.Result result = runner.run(List.of(DockerCommand.EXECUTABLE, "info"), null);
            if (!result.failed()) {
                return Status.RUNNING;
            }
            // A command that could not be run at all answers with a negative exit code;
            // see ProcessRunner.Result. The runner this is given in the application
            // catches what starting a process throws and reports it that way rather than
            // throwing it on, so reading only the exception below would have made
            // NOT_FOUND unreachable on a student's machine -- and it is the one state
            // this was split in two to recognise.
            return result.exitCode() < 0 ? Status.NOT_FOUND : Status.NOT_STARTED;
        } catch (Exception e) {
            // A runner that throws instead is answering the same thing.
            return Status.NOT_FOUND;
        }
    }

    /** The dialog that fits what was actually wrong. */
    public static void showMissingDialog(Translations translations, Status status) {
        if (status == Status.NOT_FOUND) {
            showDialog(translations, Message.DIALOG_DOCKER_NOT_FOUND_TITLE,
                    translations.format(Message.DIALOG_DOCKER_NOT_FOUND_MESSAGE, INSTALL_URL));
        } else {
            showMissingDialog(translations);
        }
    }

    public static void showMissingDialog(Translations translations) {
        showDialog(translations, Message.DIALOG_DOCKER_MISSING_TITLE,
                translations.format(Message.DIALOG_DOCKER_MISSING_MESSAGE, INSTALL_URL));
    }

    private static void showDialog(Translations translations, Message title, String html) {
        JOptionPane.showMessageDialog(null, messagePane(html),
                translations.get(title), JOptionPane.ERROR_MESSAGE);
    }

    /**
     * The message, wrapped to a readable width.
     *
     * <p>
     * HTML in a JEditorPane is laid out at whatever width it is given, and a pane nobody
     * has given one to asks for the width of its longest line unbroken. The dialog that
     * followed was a strip across the whole screen with one sentence in it.
     *
     * <p>
     * So it is sized twice: once as wide as it should be and as tall as it likes, which
     * makes the text wrap, and then to the height that wrapping came to. Scaled, because
     * on a screen that draws text at twice the size the same paragraph needs twice the
     * room.
     */
    static JEditorPane messagePane(String html) {
        JEditorPane messagePane = new JEditorPane("text/html", html);
        messagePane.setEditable(false);
        messagePane.setOpaque(false);
        messagePane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openInBrowser(e.getURL().toString());
            }
        });

        int width = com.formdev.flatlaf.util.UIScale.scale(MESSAGE_WIDTH);
        messagePane.setSize(width, Short.MAX_VALUE);
        messagePane.setPreferredSize(new java.awt.Dimension(width,
                messagePane.getPreferredSize().height));
        return messagePane;
    }

    private static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception e) {
            // Opening a browser is a convenience; the URL is still shown in the dialog.
        }
    }
}
