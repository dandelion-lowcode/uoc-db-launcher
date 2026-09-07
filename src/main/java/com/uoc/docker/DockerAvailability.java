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

    private DockerAvailability() {
    }

    public static boolean isRunning() {
        return isRunning(new SystemProcessRunner());
    }

    /**
     * Whether the daemon answers, asked of something other than this machine's Docker.
     *
     * <p>
     * "docker info" is the question because it is the one command that needs the daemon
     * rather than only the executable: a machine with Docker installed and not started
     * answers every other question happily, and that is the state a student is most often
     * in when they open the launcher.
     *
     * @param runner what runs the command
     */
    static boolean isRunning(ProcessRunner runner) {
        try {
            return !runner.run(List.of(DockerCommand.EXECUTABLE, "info"), null).failed();
        } catch (Exception e) {
            // Docker not being installed at all arrives here rather than as an exit code.
            return false;
        }
    }

    public static void showMissingDialog(Translations translations) {
        JEditorPane messagePane = new JEditorPane("text/html",
                translations.format(Message.DIALOG_DOCKER_MISSING_MESSAGE, INSTALL_URL));
        messagePane.setEditable(false);
        messagePane.setOpaque(false);
        messagePane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openInBrowser(e.getURL().toString());
            }
        });
        JOptionPane.showMessageDialog(null, messagePane,
                translations.get(Message.DIALOG_DOCKER_MISSING_TITLE), JOptionPane.ERROR_MESSAGE);
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
