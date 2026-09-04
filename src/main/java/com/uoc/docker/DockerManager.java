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

import javax.swing.SwingUtilities;
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
    private static final int RECONNECT_SECONDS = 5;

    private final DockerClient client;
    private final ProcessRunner processRunner;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ServiceStatusReporter reporter;
    private volatile boolean closed;

    public DockerManager() {
        this(defaultClient(), new SystemProcessRunner(), SwingUtilities::invokeLater);
    }

    /**
     * Lets a test watch the statuses as they are decided, by delivering them where it can
     * see them rather than on the interface thread.
     */
    DockerManager(DockerClient client, ProcessRunner processRunner, Consumer<Runnable> dispatcher) {
        this.client = client;
        this.processRunner = processRunner;
        this.reporter = new ServiceStatusReporter(dispatcher);
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

    public void refreshStatus(String key) {
        reporter.watch(key);
        executor.submit(() -> reconcileStatus(key));
    }

    public void start(String key) {
        reporter.expectRunning(key);
        executor.submit(() -> {
            runCompose(key, ServiceStatus.STARTING, COMPOSE_UP, COMPOSE_DETACHED);
            reconcileStatus(key);
        });
    }

    public void stop(String key) {
        reporter.expectStopped(key);
        executor.submit(() -> {
            runCompose(key, ServiceStatus.STOPPING, COMPOSE_STOP);
            reconcileStatus(key);
        });
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
        if (action == null) {
            return;
        }

        ServiceStatus status = ContainerStatus.fromEvent(action);
        if (status == null) {
            return;
        }
        reporter.report(key, status);
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

    private void runCompose(String key, ServiceStatus inProgressStatus, String... composeArgs) {
        reporter.report(key, inProgressStatus);
        try {
            List<String> command = new ArrayList<>(List.of(DockerCommand.EXECUTABLE,
                    DockerCommand.COMPOSE, "-f", DockerCommand.COMPOSE_FILE));
            command.addAll(List.of(composeArgs));
            command.add(key);

            ProcessRunner.Result result = processRunner.run(command, null);
            if (result.failed()) {
                reporter.reportFailure(key, result.output());
            }
        } catch (Exception e) {
            reporter.reportFailure(key, e.getMessage());
        }
    }

    private void reconcileStatus(String key) {
        try {
            InspectContainerResponse info = client.inspectContainerCmd(DockerCommand.containerName(key)).exec();
            InspectContainerResponse.ContainerState state = info.getState();
            if (state == null) {
                return;
            }

            HealthState health = state.getHealth();
            String healthStatus = health != null && health.getStatus() != null
                    ? health.getStatus().toString()
                    : null;

            ServiceStatus status = ContainerStatus.fromState(
                    Boolean.TRUE.equals(state.getRunning()), healthStatus);
            reporter.report(key, status);
        } catch (NotFoundException e) {
            reporter.report(key, ServiceStatus.STOPPED);
        } catch (Exception e) {
            reporter.report(key, ServiceStatus.ERROR);
        }
    }
}
