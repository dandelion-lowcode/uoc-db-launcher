package com.uoc.docker.client;

import java.util.List;

/** Vertica, queried with vsql against the bundled VMart database. */
final class VerticaSqlClient implements DatabaseClient {

    /**
     * The full path, because {@code vsql} is not on the PATH a {@code docker exec} gets.
     * Written as a bare name it failed with "vsql: command not found" for every query,
     * and the healthcheck in the compose file had the same fault, which left the service
     * shown as loading for as long as it ran.
     */
    private static final String VSQL = "/opt/vertica/bin/vsql";

    /**
     * Ignore the startup file. The image ships one that turns on timings and prints two
     * lines about them before every answer, which is noise in a teaching console.
     */
    private static final String NO_STARTUP_FILE = "-X";

    @Override
    public List<String> command(String query) {
        return List.of(VSQL, NO_STARTUP_FILE,
                "-h", "localhost", "-U", "dbadmin", "-d", "VMart", "-c", query);
    }
}
