package com.uoc.docker;

import java.util.Locale;

import com.uoc.i18n.Message;

/**
 * The databases the launcher manages. The key is both the Docker Compose
 * service name and the suffix of the container name, so it is defined once here
 * instead of being repeated as a literal wherever a database is referenced.
 */
public enum Database {

    MONGO("mongo", "MongoDB", Group.GENERAL, Kind.DATABASE, Message.LABEL_CONSOLE_MONGO),
    CASSANDRA("cassandra", "Cassandra", Group.GENERAL, Kind.DATABASE,
            Message.LABEL_CONSOLE_CASSANDRA),
    NEO4J("neo4j", "Neo4j", Group.GENERAL, Kind.DATABASE, Message.LABEL_CONSOLE_NEO4J),

    NEO4J_TWITTER("neo4j-twitter", "Neo4j (Twitter)", Group.BACHELOR_COURSE, Kind.DATABASE,
            Message.LABEL_CONSOLE_NEO4J_TWITTER),
    REDIS("redis", "Redis", Group.BACHELOR_COURSE, Kind.DATABASE, Message.LABEL_CONSOLE_REDIS),
    RIAK("riak", "Riak", Group.BACHELOR_COURSE, Kind.DATABASE, Message.LABEL_CONSOLE_RIAK),

    COCKROACHDB("cockroachdb", "CockroachDB", Group.OPTIMIZATION_COURSE, Kind.DATABASE,
            Message.LABEL_CONSOLE_COCKROACHDB),
    VERTICA("vertica", "Vertica", Group.OPTIMIZATION_COURSE, Kind.DATABASE, Message.LABEL_CONSOLE_VERTICA),
    ARANGODB("arangodb", "ArangoDB", Group.OPTIMIZATION_COURSE, Kind.DATABASE,
            Message.LABEL_CONSOLE_ARANGODB),
    ELASTICSEARCH("elasticsearch", "Elasticsearch", Group.OPTIMIZATION_COURSE, Kind.DATABASE,
            Message.LABEL_CONSOLE_ELASTICSEARCH),

    JUPYTER("jupyter", "Jupyter", Group.JUPYTER, Kind.NOTEBOOK, Message.LABEL_JUPYTER);

    /**
     * Which block of the services menu a service belongs to.
     *
     * <p>
     * The menu draws a line wherever this changes, so the order the constants are
     * declared in is the order they appear in and the grouping needs saying only
     * once.
     *
     * <p>
     * It used to be worked out from whether a service was shown by default, which
     * could
     * only ever produce two blocks: everything not in the course's own three ended
     * up
     * lumped together, whatever it was for.
     */
    public enum Group {

        /**
         * Wanted whichever course a student is on, and the tabs open on a first run.
         */
        GENERAL,

        /** The rest of what the bachelor's course covers. */
        BACHELOR_COURSE,

        /** Added for the optimisation course, whose notebooks are what drive them. */
        OPTIMIZATION_COURSE,

        /** Not a database at all. */
        JUPYTER
    }

    /**
     * What a service is, which is the one distinction the rest of the launcher
     * needs.
     *
     * <p>
     * Everything that separates Jupyter from the databases follows from this rather
     * than
     * from a list of exceptions: it is driven from a browser instead of a query
     * console,
     * and it publishes no healthcheck, so it counts as ready as soon as it is up.
     */
    public enum Kind {
        DATABASE, NOTEBOOK
    }

    private final String key;
    private final String displayName;
    private final Group group;
    private final Kind kind;
    private final Message consoleLabel;

    Database(String key, String displayName, Group group, Kind kind, Message consoleLabel) {
        this.key = key;
        this.displayName = displayName;
        this.group = group;
        this.kind = kind;
        this.consoleLabel = consoleLabel;
    }

    /** Which block of the services menu this belongs to. */
    public Group group() {
        return group;
    }

    public Kind kind() {
        return kind;
    }

    /**
     * Whether queries can be typed at this service. Only a database answers a
     * client on
     * standard input; a notebook is worked on in a browser.
     */
    public boolean hasQueryConsole() {
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

    /**
     * Whether a tab opens on this the first time the launcher is run.
     *
     * <p>
     * Which is the three the course works through, and follows from the group
     * rather than
     * being said twice. After that first run it is the student's own choice that
     * decides,
     * which is remembered.
     */
    public boolean isShownByDefault() {
        return group == Group.GENERAL;
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
     *                                  database, which in either case means a
     *                                  mistake in the caller
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
