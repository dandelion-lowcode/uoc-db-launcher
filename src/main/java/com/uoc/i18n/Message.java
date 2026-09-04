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
    STATUS_STARTING("status.starting"),
    STATUS_RUNNING("status.running"),
    STATUS_HEALTHY("status.healthy"),
    STATUS_UNHEALTHY("status.unhealthy"),
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
    BUTTON_SEND("button.send"),
    BUTTON_CLEAR("button.clear"),
    BUTTON_OPEN_JUPYTER("button.open.jupyter"),
    TUTORIAL_SERVICES("tutorial.services"),
    TUTORIAL_START_MONGO("tutorial.start.mongo"),
    TUTORIAL_MENU("tutorial.menu"),
    TUTORIAL_JUPYTER("tutorial.jupyter"),
    TUTORIAL_NEXT("tutorial.next"),
    TUTORIAL_CLOSE("tutorial.close"),
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
