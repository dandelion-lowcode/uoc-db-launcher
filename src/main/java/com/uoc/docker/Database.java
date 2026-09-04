package com.uoc.docker;

import com.uoc.i18n.Message;

import java.util.Locale;

/**
 * The databases the launcher manages. The key is both the Docker Compose
 * service name and the suffix of the container name, so it is defined once here
 * instead of being repeated as a literal wherever a database is referenced.
 */
public enum Database {

    MONGO("mongo", "MongoDB", true, Kind.DATABASE, Message.LABEL_CONSOLE_MONGO),
    CASSANDRA("cassandra", "Cassandra", true, Kind.DATABASE, Message.LABEL_CONSOLE_CASSANDRA),
    NEO4J("neo4j", "Neo4j", true, Kind.DATABASE, Message.LABEL_CONSOLE_NEO4J),
    NEO4J_TWITTER("neo4j-twitter", "Neo4j (Twitter)", false, Kind.DATABASE,
            Message.LABEL_CONSOLE_NEO4J_TWITTER),
    REDIS("redis", "Redis", false, Kind.DATABASE, Message.LABEL_CONSOLE_REDIS),
    RIAK("riak", "Riak", false, Kind.DATABASE, Message.LABEL_CONSOLE_RIAK),
    JUPYTER("jupyter", "Jupyter", false, Kind.NOTEBOOK, Message.LABEL_JUPYTER);

    /**
     * What a service is, which is the one distinction the rest of the launcher needs.
     *
     * <p>
     * Everything that separates Jupyter from the databases follows from this rather than
     * from a list of exceptions: it is driven from a browser instead of a query console,
     * and it publishes no healthcheck, so it counts as ready as soon as it is up.
     */
    public enum Kind {
        DATABASE, NOTEBOOK
    }

    private final String key;
    private final String displayName;
    private final boolean shownByDefault;
    private final Kind kind;
    private final Message consoleLabel;

    Database(String key, String displayName, boolean shownByDefault, Kind kind,
            Message consoleLabel) {
        this.key = key;
        this.displayName = displayName;
        this.shownByDefault = shownByDefault;
        this.kind = kind;
        this.consoleLabel = consoleLabel;
    }

    public Kind kind() {
        return kind;
    }

    /**
     * Whether queries can be typed at this service. Only a database answers a client on
     * standard input; a notebook is worked on in a browser.
     */
    public boolean hasQueryConsole() {
        return kind == Kind.DATABASE;
    }

    /**
     * Whether Docker is asked to judge when this service is ready.
     *
     * <p>
     * A notebook publishes no healthcheck, so waiting for one would leave it shown as
     * starting for as long as it ran. Being up is the whole of what readiness means for
     * it.
     */
    public boolean reportsHealth() {
        return kind == Kind.DATABASE;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * What the console above this database is called. Each one is driven with a
     * different
     * client and a different language, and the heading is where that is said.
     */
    public Message consoleLabel() {
        return consoleLabel;
    }

    public boolean isShownByDefault() {
        return shownByDefault;
    }

    public String iconResource() {
        return "icons/" + key + ".svg";
    }

    public String containerName() {
        return DockerCommand.containerName(key);
    }

    /**
     * The database a key refers to. Matching ignores case, so a key that came back
     * from
     * Docker rather than from {@link #key()} is still recognised.
     *
     * @param key a service key; must not be null
     * @return the matching database; never null
     * @throws IllegalArgumentException if the key is null or names no known
     *                                  database,
     *                                  which in either case means a mistake in the
     *                                  caller
     */
    public static Database fromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("A database key is required");
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (Database database : values()) {
            if (database.key.equals(normalized)) {
                return database;
            }
        }
        throw new IllegalArgumentException("Unknown database: " + key);
    }
}
