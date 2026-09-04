package com.uoc.ui;

import java.awt.Desktop;
import java.net.URI;

/**
 * Opens an address in whatever browser the student uses.
 *
 * <p>
 * Jupyter is the one service here that is not driven from a console, so the launcher's
 * part in it ends at putting the right page in front of them.
 */
public final class Browser {

    /**
     * Where the notebook server answers. It is the host side of the port the compose file
     * publishes for the jupyter service, and the two have to agree.
     */
    static final String JUPYTER_URL = "http://localhost:8888";

    private Browser() {
    }

    /** Opens the notebooks. Does nothing if this system offers no way to. */
    public static void openJupyter() {
        open(JUPYTER_URL);
    }

    static void open(String url) {
        // Headless systems and some Linux desktops support none of this, and failing to
        // open a browser is not worth interrupting anyone over: the address is on screen
        // beside the button, and they can paste it themselves.
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            return;
        }
        try {
            desktop.browse(URI.create(url));
        } catch (Exception e) {
            // Nothing better to offer than leaving them to open it themselves.
        }
    }
}
