package com.uoc.docker;

import com.uoc.i18n.Message;

import java.util.Locale;

/**
 * The databases the launcher manages. The key is both the Docker Compose
 * service name and the suffix of the container name, so it is defined once here
 * instead of being repeated as a literal wherever a database is referenced.
 */
public enum Database {

    MONGO("mongo", "MongoDB", true, Message.LABEL_CONSOLE_MONGO),
    CASSANDRA("cassandra", "Cassandra", true, Message.LABEL_CONSOLE_CASSANDRA),
    NEO4J("neo4j", "Neo4j", true, Message.LABEL_CONSOLE_NEO4J),
    NEO4J_TWITTER("neo4j-twitter", "Neo4j (Twitter)", false, Message.LABEL_CONSOLE_NEO4J_TWITTER),
    REDIS("redis", "Redis", false, Message.LABEL_CONSOLE_REDIS),
    RIAK("riak", "Riak", false, Message.LABEL_CONSOLE_RIAK);

    private final String key;
    private final String displayName;
    private final boolean shownByDefault;
    private final Message consoleLabel;

    Database(String key, String displayName, boolean shownByDefault, Message consoleLabel) {
        this.key = key;
        this.displayName = displayName;
        this.shownByDefault = shownByDefault;
        this.consoleLabel = consoleLabel;
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
