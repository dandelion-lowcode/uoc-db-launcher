package com.uoc.docker;

import com.uoc.platform.UserDataDirectory;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DockerManager {

    private static final String COMPOSE_UP = "up";
    private static final String COMPOSE_DETACHED = "-d";
    private static final String COMPOSE_STOP = "stop";
    private static final int POLL_SECONDS = 5;

    /**
     * The container events worth looking again for. Docker also reports every exec,
     * attach
     * and resize, and inspecting on those would be constant work for nothing:
     * running a
     * query is an exec, so a student typing would re-inspect on every line.
     */
    private static final java.util.Set<String> SIGNIFICANT_ACTIONS = java.util.Set.of(
            "create", "start", "restart", "die", "kill", "stop", "pause", "unpause",
            "destroy", "rename", "update", "oom", "health_status");
    private static final int RECONNECT_SECONDS = 5;

    private static final String EVENTS = "events";
    private static final String FILTER = "--filter";
    private static final String FORMAT = "--format";
    private static final String CONTAINER_EVENTS_ONLY = "type=container";

    /**
     * One line per event: the container's name, a tab, and what happened to it.
     *
     * <p>
     * Docker can also print the whole event as JSON, which would mean carrying a JSON
     * parser to read two fields out of it. The tab is what separates them, because a
     * container name cannot hold one and an action can hold a space: health events arrive
     * as "health_status: healthy".
     */
    private static final String EVENT_FORMAT = "{{.Actor.Attributes.name}}\t{{.Action}}";

    private static final char FIELD_SEPARATOR = '\t';

    private static final String INSPECT = "inspect";

    /**
     * The four fields of a container's state that anything here decides on, and nothing
     * else. A template rather than the whole of the JSON, which is some thirty kilobytes
     * a service and would need a parser to read four values out of.
     *
     * <p>
     * The healthcheck is printed only when the image defines one, so a service without
     * one leaves the field empty rather than making the answer unreadable.
     */
    private static final String STATE_FORMAT = "{{.State.Status}}\t{{.State.OOMKilled}}\t"
            + "{{.State.ExitCode}}\t{{if .State.Health}}{{.State.Health.Status}}{{end}}";

    private static final int STATUS = 0;
    private static final int OOM_KILLED = 1;
    private static final int EXIT_CODE = 2;
    private static final int HEALTH = 3;

    /**
     * What Docker says when there is no such container: "no such object" from a recent
     * daemon and "No such container" from an older one, capitalised or not depending on
     * the version. It is the one failed inspect that is an answer about the container
     * rather than about the daemon.
     */
    private static final String NO_SUCH_CONTAINER = "no such";

    private final ProcessRunner processRunner;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ServiceStatusReporter reporter;
    private final Path composeFile;
    private final ImageAvailability images;

    /** The services a compose command is running against right now. */
    private final java.util.Set<String> busy = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The docker events process, for as long as there is one to end. */
    private volatile ProcessRunner.LiveProcess events;

    private volatile boolean closed;

    public DockerManager() {
        this(new SystemProcessRunner(), SwingUtilities::invokeLater, installBundledFiles());
    }

    /**
     * Lets a test watch the statuses as they are decided, by delivering them where
     * it can
     * see them rather than on the interface thread, put a stand-in in the way of every
     * Docker command, and point Docker at a compose file of its own choosing.
     */
    DockerManager(ProcessRunner processRunner, Consumer<Runnable> dispatcher, Path composeFile) {
        this.processRunner = processRunner;
        this.reporter = new ServiceStatusReporter(dispatcher);
        this.composeFile = composeFile;
        this.images = new ImageAvailability(processRunner, composeFile);
    }

    /**
     * Writes the service definitions somewhere Docker can read them, which an
     * installed
     * application has to do before it can start anything at all.
     *
     * <p>
     * A failure here is not recoverable: without these files there is no service to
     * start, so it is raised rather than quietly leaving every database unreachable
     * for
     * reasons the student cannot see.
     */
    private static Path installBundledFiles() {
        try {
            return BundledFiles.inUserDataDirectory().install();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "The launcher could not write the files Docker needs into "
                            + UserDataDirectory.current() + ".",
                    e);
        }
    }

    /**
     * Begins listening to Docker. Kept apart from construction so the listeners are
     * attached first, and nothing is decided before anyone is watching.
     */
    public DockerManager start() {
        watchEvents();
        scheduler.scheduleWithFixedDelay(this::pollAllKnownKeys, POLL_SECONDS, POLL_SECONDS,
                TimeUnit.SECONDS);
        return this;
    }

    /** Stops listening. The containers themselves are left running. */
    public void close() {
        // Set before shutting anything down: ending the event stream looks exactly like
        // the stream ending on its own, and that must not be mistaken for a lost daemon.
        closed = true;
        scheduler.shutdownNow();
        executor.shutdownNow();
        try {
            ProcessRunner.LiveProcess running = events;
            if (running != null) {
                running.stop();
            }
        } catch (Exception e) {
            // Nothing useful can be done while shutting down.
        }
    }

    public void setListener(ServiceStatusReporter.StatusListener listener) {
        reporter.setListener(listener);
    }

    public void setFailureListener(ServiceStatusReporter.FailureListener failureListener) {
        reporter.setFailureListener(failureListener);
    }

    public void setProgressListener(ServiceStatusReporter.ProgressListener progressListener) {
        reporter.setProgressListener(progressListener);
    }

    public void refreshStatus(String key) {
        reporter.watch(key);
        executor.submit(() -> reconcileStatus(key));
    }

    public void start(String key) {
        // The phase is entered here rather than on the worker, so the button is disabled
        // the instant it is pressed. Working out whether the image has to be fetched
        // first runs two Docker commands and takes the better part of a second, and a
        // button that stays live for that long gets pressed again.
        if (!claim(key, Phase.STARTING)) {
            return;
        }
        executor.submit(() -> {
            try {
                // Asked before starting, because once it is under way there is no telling
                // from the outside whether the wait is a download or a start, and the two
                // differ by minutes.
                Phase waiting = images.mustBeInstalled(key) ? Phase.INSTALLING : Phase.STARTING;
                runCommand(key, waiting, COMPOSE_UP, COMPOSE_DETACHED);
            } finally {
                busy.remove(key);
            }
        });
    }

    public void stop(String key) {
        if (!claim(key, Phase.STOPPING)) {
            return;
        }
        executor.submit(() -> {
            try {
                runCommand(key, Phase.STOPPING, COMPOSE_STOP);
            } finally {
                busy.remove(key);
            }
        });
    }

    /**
     * Takes charge of a service, unless a command is already running against it.
     *
     * <p>
     * Two commands at once against the same service is not a race the launcher can win:
     * both fetch the image, both then try to create the container, and the second is
     * refused by Docker with a name conflict that means nothing to a student.
     *
     * <p>
     * The guard is here rather than on the buttons because it has to cover every way in
     * -- the panel, the menu, and a second press that lands before the first has been
     * reported.
     *
     * @return whether this call is the one that should go ahead
     */
    private boolean claim(String key, Phase phase) {
        if (!busy.add(key)) {
            return false;
        }
        reporter.retrying(key);
        reporter.enterPhase(key, phase);
        return true;
    }

    /**
     * Runs one compose command and settles what the service is afterwards.
     *
     * <p>
     * The container is inspected before the phase is cleared, not after. Cleared
     * first,
     * there is a moment where no command is running and the answer is still the
     * stale one
     * from before it started, and the service flashes "stopped" between a start
     * finishing
     * and its result being read.
     */
    private void runCommand(String key, Phase phase, String... composeArgs) {
        reporter.enterPhase(key, phase);
        try {
            runCompose(key, phase, composeArgs);
            reconcileStatus(key);
        } finally {
            reporter.enterPhase(key, Phase.IDLE);
        }
    }

    private void pollAllKnownKeys() {
        for (String key : reporter.watchedKeys()) {
            reconcileStatus(key);
        }
    }

    /**
     * Asks Docker to report every container event until it is stopped.
     *
     * <p>
     * The command outlives this call and writes a line per event, so the stream ending is
     * the daemon being gone. That is the only ending there is: the launcher never asks
     * for one except when it is closing.
     */
    private void watchEvents() {
        if (closed) {
            return;
        }
        try {
            events = processRunner.stream(
                    List.of(DockerCommand.EXECUTABLE, EVENTS, FILTER, CONTAINER_EVENTS_ONLY,
                            FORMAT, EVENT_FORMAT),
                    this::handleEvent, this::connectionLost);
        } catch (Exception e) {
            // A stream that cannot even be begun is a connection that has been lost
            // before it was made, and is retried on the same terms as any other.
            connectionLost();
        }
    }

    private void handleEvent(String line) {
        if (closed) {
            return;
        }
        int separator = line.indexOf(FIELD_SEPARATOR);
        if (separator < 0) {
            return;
        }

        String name = line.substring(0, separator);
        if (!DockerCommand.isManagedContainer(name)) {
            return;
        }

        String key = DockerCommand.serviceKey(name);
        String action = line.substring(separator + 1);
        if (!SIGNIFICANT_ACTIONS.contains(baseAction(action))) {
            return;
        }

        // An event says something changed, not what the container now is: it cannot
        // report a healthcheck the image does not define, nor tell a pause from a
        // restart
        // loop. Rather than guess from the verb, the container is inspected, which is
        // the
        // one answer that covers every case the same way the poll does.
        executor.submit(() -> reconcileStatus(key));
    }

    /**
     * The verb of an event, without what follows it. Health events arrive as
     * "health_status: healthy", and it is the change that matters here, not the
     * result:
     * the container is inspected either way.
     */
    private static String baseAction(String action) {
        String normalized = action.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = normalized.indexOf(':');
        return colon < 0 ? normalized : normalized.substring(0, colon).trim();
    }

    private void scheduleReconnect() {
        if (closed) {
            return;
        }
        try {
            scheduler.schedule(this::watchEvents, RECONNECT_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            // The scheduler is on its way out, so there is nothing left to reconnect to.
        }
    }

    private void connectionLost() {
        if (closed) {
            return;
        }
        reporter.reportEverythingUnreachable();
        scheduleReconnect();
    }

    private void runCompose(String key, Phase phase, String... composeArgs) {
        try {
            List<String> command = new ArrayList<>(List.of(DockerCommand.EXECUTABLE,
                    DockerCommand.COMPOSE, "-f", composeFile.toString()));
            command.addAll(List.of(composeArgs));
            command.add(key);

            // Only an install is worth watching line by line. A start or a stop is over
            // in seconds and says almost nothing while it runs, so following it would put
            // a flicker of text on screen and no news in it.
            ProcessRunner.Result result = phase == Phase.INSTALLING
                    ? processRunner.run(command, null, progressOf(key))
                    : processRunner.run(command, null);
            if (result.failed()) {
                reporter.reportFailure(key, result.output());
            }
        } catch (Exception e) {
            reporter.reportFailure(key, e.getMessage());
        }
    }

    /**
     * Follows one install, rewriting what is on screen as each line arrives.
     *
     * <p>
     * The progress belongs to this one run: a service installed again later starts from a
     * blank slate rather than from where the last attempt left off.
     */
    private Consumer<String> progressOf(String key) {
        InstallProgress progress = new InstallProgress();
        return line -> {
            progress.accept(line);
            reporter.reportProgress(key, progress.text());
        };
    }

    private void reconcileStatus(String key) {
        reporter.observe(key, observationOf(processRunner.run(
                List.of(DockerCommand.EXECUTABLE, INSPECT, FORMAT, STATE_FORMAT,
                        DockerCommand.containerName(key)),
                null)));
    }

    /**
     * Reads a container's state into the one value the rules are written against.
     *
     * <p>
     * Four fields are asked for where one used to be. {@code running} alone is not
     * enough: it is true for a paused container and true throughout a restart loop, so a
     * service that is answering nobody, and one that is crashing over and over, both read
     * as working. The status field separates them, and OOMKilled is what tells a
     * container the kernel killed for its memory from one that merely stopped.
     *
     * <p>
     * A failure is not one answer but two, and they mean opposite things. No such
     * container is an answer -- there is nothing there, and starting it is exactly what
     * the student should be offered -- while a daemon that cannot be reached says nothing
     * about any container at all. Only the message tells them apart.
     */
    private static Observation observationOf(ProcessRunner.Result answer) {
        if (answer.failed()) {
            return answer.output().toLowerCase(Locale.ROOT).contains(NO_SUCH_CONTAINER)
                    ? Observation.ABSENT
                    : Observation.DAEMON_LOST;
        }

        String[] state = answer.output().lines().findFirst().orElse("")
                .split(String.valueOf(FIELD_SEPARATOR), -1);
        return Observation.of(field(state, STATUS), Boolean.parseBoolean(field(state, OOM_KILLED)),
                exitCode(field(state, EXIT_CODE)), field(state, HEALTH));
    }

    /** One field of the template's answer, or nothing when Docker printed fewer. */
    private static String field(String[] state, int index) {
        return index < state.length ? state[index].trim() : "";
    }

    /**
     * Docker prints a whole number here. Anything else is read as no exit code rather
     * than refused, because the status beside it is what decides and it is still good.
     */
    private static Integer exitCode(String reported) {
        try {
            return Integer.valueOf(reported);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
