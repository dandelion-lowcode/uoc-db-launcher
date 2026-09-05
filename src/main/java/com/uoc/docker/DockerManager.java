package com.uoc.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.EventType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import com.uoc.platform.UserDataDirectory;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private final DockerClient client;
    private final ProcessRunner processRunner;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ServiceStatusReporter reporter;
    private final Path composeFile;
    private final ImageAvailability images;

    /** The services a compose command is running against right now. */
    private final java.util.Set<String> busy = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private volatile boolean closed;

    public DockerManager() {
        this(defaultClient(), new SystemProcessRunner(), SwingUtilities::invokeLater,
                installBundledFiles());
    }

    /**
     * Lets a test watch the statuses as they are decided, by delivering them where
     * it can
     * see them rather than on the interface thread, and point Docker at a compose
     * file of
     * its own choosing.
     */
    DockerManager(DockerClient client, ProcessRunner processRunner, Consumer<Runnable> dispatcher,
            Path composeFile) {
        this.client = client;
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

    private static DockerClient defaultClient() {
        // The library resolves the endpoint from DOCKER_HOST, DOCKER_CONTEXT and the
        // active Docker context, so it reaches the same daemon as the command line.
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
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
        // Set before shutting anything down: closing the event stream makes Docker call
        // back with an error, and that must not be mistaken for a lost daemon.
        closed = true;
        scheduler.shutdownNow();
        executor.shutdownNow();
        try {
            client.close();
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

    private void watchEvents() {
        try {
            client.eventsCmd()
                    .withEventTypeFilter(EventType.CONTAINER)
                    .exec(new ResultCallback.Adapter<Event>() {
                        @Override
                        public void onNext(Event event) {
                            handleEvent(event);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            connectionLost();
                        }

                        @Override
                        public void onComplete() {
                            connectionLost();
                        }
                    });
        } catch (Exception e) {
            connectionLost();
        }
    }

    private void handleEvent(Event event) {
        String name = event.getActor() != null && event.getActor().getAttributes() != null
                ? event.getActor().getAttributes().get("name")
                : null;
        if (!DockerCommand.isManagedContainer(name)) {
            return;
        }

        String key = DockerCommand.serviceKey(name);
        String action = event.getAction();
        if (action == null || !SIGNIFICANT_ACTIONS.contains(baseAction(action))) {
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
        try {
            InspectContainerResponse info = client.inspectContainerCmd(DockerCommand.containerName(key)).exec();
            reporter.observe(key, observationOf(info.getState()));
        } catch (NotFoundException e) {
            reporter.observe(key, Observation.ABSENT);
        } catch (Exception e) {
            // Anything else means the question could not be put to Docker at all, which
            // says nothing about the container and everything about the daemon.
            reporter.observe(key, Observation.DAEMON_LOST);
        }
    }

    /**
     * Reads a container's state into the one value the rules are written against.
     *
     * <p>
     * Five fields are used where one used to be. {@code running} alone is not
     * enough: it
     * is true for a paused container and true throughout a restart loop, so a
     * service
     * that is answering nobody, and one that is crashing over and over, both read
     * as
     * working. The status field separates them, and OOMKilled is what tells a
     * container
     * the kernel killed for its memory from one that merely stopped.
     */
    private static Observation observationOf(InspectContainerResponse.ContainerState state) {
        if (state == null) {
            return Observation.ABSENT;
        }
        HealthState health = state.getHealth();
        String healthStatus = health == null ? null : health.getStatus();
        return Observation.of(state.getStatus(), Boolean.TRUE.equals(state.getOOMKilled()),
                state.getExitCodeLong().intValue(), healthStatus);
    }
}
