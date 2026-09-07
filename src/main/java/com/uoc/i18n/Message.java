package com.uoc.i18n;

/**
 * Every text the interface can show, as a constant instead of a loose string.
 *
 * <p>
 * A mistyped key can no longer reach the running application: it stops the
 * build, and a key that no longer exists in the bundles is reported at startup
 * by {@link Translations}, in whichever language is being loaded.
 */
public enum Message {

    APP_TITLE("app.title"),
    MENU_LANGUAGE("menu.language"),
    MENU_ENGLISH("menu.english"),
    MENU_SPANISH("menu.spanish"),
    MENU_CATALAN("menu.catalan"),
    MENU_DATABASES("menu.databases"),
    MENU_OPTIONS("menu.options"),
    THEME_LIGHT("theme.light"),
    THEME_DARK("theme.dark"),
    THEME_SYSTEM("theme.system"),
    FONT_SYSTEM("font.system"),
    MENU_HELP("menu.help"),
    MENU_TUTORIAL("menu.tutorial"),
    MENU_TUTORIAL_START("menu.tutorial.start"),
    MENU_ABOUT("menu.about"),
    MENU_FILE("menu.file"),
    MENU_CLOSE("menu.close"),
    STATUS_STOPPED("status.stopped"),
    STATUS_INSTALLING("status.installing"),
    STATUS_STARTING("status.starting"),
    STATUS_RUNNING("status.running"),
    STATUS_HEALTHY("status.healthy"),
    STATUS_UNHEALTHY("status.unhealthy"),
    STATUS_PAUSED("status.paused"),
    STATUS_RESTARTING("status.restarting"),
    STATUS_CRASHED("status.crashed"),
    STATUS_OUT_OF_MEMORY("status.out_of_memory"),
    STATUS_STOPPING("status.stopping"),
    STATUS_ERROR("status.error"),
    MENU_ZOOM("menu.zoom"),
    ZOOM_RESET("zoom.reset"),
    ZOOM_IN("zoom.in"),
    ZOOM_OUT("zoom.out"),
    LABEL_SERVICES("label.services"),
    LABEL_JUPYTER("label.jupyter"),
    LABEL_CONSOLE_MONGO("label.console.mongo"),
    LABEL_CONSOLE_CASSANDRA("label.console.cassandra"),
    LABEL_CONSOLE_NEO4J("label.console.neo4j"),
    LABEL_CONSOLE_NEO4J_TWITTER("label.console.neo4j.twitter"),
    LABEL_CONSOLE_REDIS("label.console.redis"),
    LABEL_CONSOLE_RIAK("label.console.riak"),
    LABEL_CONSOLE_COCKROACHDB("label.console.cockroachdb"),
    LABEL_CONSOLE_ARANGODB("label.console.arangodb"),
    LABEL_CONSOLE_VERTICA("label.console.vertica"),
    LABEL_CONSOLE_ELASTICSEARCH("label.console.elasticsearch"),
    BUTTON_OPEN_JUPYTER("button.open.jupyter"),
    TUTORIAL_SERVICES("tutorial.services"),
    TUTORIAL_START_MONGO("tutorial.start.mongo"),
    TUTORIAL_MENU("tutorial.menu"),
    TUTORIAL_NEXT("tutorial.next"),
    TUTORIAL_CLOSE("tutorial.close"),
    TERMINAL_COPY("terminal.copy"),
    TERMINAL_PASTE("terminal.paste"),
    TERMINAL_FIND("terminal.find"),
    TERMINAL_SELECT_ALL("terminal.selectAll"),
    TERMINAL_CLEAR_BUFFER("terminal.clearBuffer"),
    TERMINAL_OPEN_URL("terminal.openUrl"),
    TERMINAL_PAGE_UP("terminal.pageUp"),
    TERMINAL_PAGE_DOWN("terminal.pageDown"),
    TERMINAL_LINE_UP("terminal.lineUp"),
    TERMINAL_LINE_DOWN("terminal.lineDown"),
    TERMINAL_IGNORE_CASE("terminal.ignoreCase"),
    TERMINAL_FIND_PREVIOUS("terminal.findPrevious"),
    TERMINAL_FIND_NEXT("terminal.findNext"),
    LABEL_SERVICES_NONE("label.services.none"),
    LABEL_UTILITIES("label.utilities"),
    CONSOLE_LOADING("console.loading"),
    CONSOLE_READY("console.ready"),
    BUTTON_OPEN_NEO4J_BROWSER("button.open.neo4jBrowser"),
    BUTTON_OPEN_STUDIO3T("button.open.studio3t"),
    LABEL_STUDIO3T_DOWNLOAD("label.studio3t.download"),
    DIALOG_UNINSTALL_TITLE("dialog.uninstall.title"),
    DIALOG_UNINSTALL_MESSAGE("dialog.uninstall.message"),
    TOOLTIP_START("tooltip.start"),
    TOOLTIP_STOP("tooltip.stop"),
    DIALOG_DOCKER_MISSING_TITLE("dialog.dockerMissing.title"),
    DIALOG_DOCKER_MISSING_MESSAGE("dialog.dockerMissing.message"),
    ABOUT_TEXT("about.text");

    private final String key;

    Message(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
