package com.uoc.docker;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import java.awt.Desktop;

public class DockerAvailability {

    private static final String INSTALL_URL = "https://docs.docker.com/get-started/get-docker/";

    private DockerAvailability() {
    }

    public static boolean isRunning() {
        try {
            Process process = new ProcessBuilder(DockerCommand.EXECUTABLE, "info").start();
            return process.waitFor() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
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
