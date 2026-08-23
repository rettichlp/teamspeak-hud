package de.rettichlp.teamspeakhud.teamspeak;

import de.rettichlp.teamspeakhud.teamspeak.command.AuthQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.ChannelClientListQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.ChannelInfoQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand;
import de.rettichlp.teamspeakhud.teamspeak.command.WhoAmIQuery;
import de.rettichlp.teamspeakhud.teamspeak.model.Channel;
import de.rettichlp.teamspeakhud.teamspeak.model.Client;
import de.rettichlp.teamspeakhud.teamspeak.notify.ClientPokeNotify;
import de.rettichlp.teamspeakhud.teamspeak.notify.IncrementalUpdateNotify;
import de.rettichlp.teamspeakhud.teamspeak.notify.MembershipChangedNotify;
import de.rettichlp.teamspeakhud.teamspeak.notify.TeamSpeakNotify;
import de.rettichlp.teamspeakhud.teamspeak.notify.TextMessageNotify;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.LOGGER;
import static de.rettichlp.teamspeakhud.TeamSpeakHud.configuration;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

@Getter
public class TeamSpeakClient {

    private static final long HEARTBEAT_SECONDS = 5L;
    private static final long RECONNECT_SECONDS = 10L;

    /**
     * Every notify event this mod reacts to, each owning both its ClientQuery event name(s) (for {@code clientnotifyregister}) and how
     * it reacts once such a line arrives.
     */
    private static final List<TeamSpeakNotify> NOTIFY_EVENTS = List.of(
            new MembershipChangedNotify(),
            new IncrementalUpdateNotify(),
            new ClientPokeNotify(),
            new TextMessageNotify()
    );

    private final ApiKeyResolver apiKeyResolver = new ApiKeyResolver();
    private final Channel channel = new Channel();
    private final ExecutorService reader = newSingleThreadExecutor(this::newDaemonThread);
    private final ScheduledExecutorService scheduler = newSingleThreadScheduledExecutor(this::newDaemonThread);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    /**
     * The {@link TeamSpeakCommand} whose response we're currently waiting on, or {@code null} if none is in flight.
     */
    @Setter
    private volatile TeamSpeakCommand<?> pendingCommand;
    private volatile TeamSpeakConnection connection;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private volatile ScheduledFuture<?> reconnectFuture;
    private volatile boolean stopped = true;
    private volatile int generation;
    private volatile boolean connected;
    private volatile boolean invalidApiKey;

    private int ownClientId;

    public void start() {
        this.stopped = false;
        int currentGeneration = ++this.generation;
        submit(this.reader, () -> connect(currentGeneration));
    }

    /**
     * Generation is bumped here, synchronously on the caller's thread, rather than inside {@link #stopInternal},
     * so that a {@link #stop()} immediately followed by {@link #start()} (e.g. toggling the mod off/on in the
     * option screen) can never race: {@code stopInternal} only tears down connections that predate the
     * generation it captured, so a fresh connection opened by a subsequent {@code start()} is never torn down
     * out of order.
     */
    public void stop() {
        this.stopped = true;
        int stoppedGeneration = ++this.generation;
        submit(this.scheduler, () -> stopInternal(stoppedGeneration));
    }

    public void shutdown() {
        this.stopped = true;

        TeamSpeakConnection currentConnection = this.connection;
        if (currentConnection != null) {
            currentConnection.close();
        }

        this.reader.shutdownNow();
        this.scheduler.shutdownNow();
    }

    private void stopInternal(int stoppedGeneration) {
        this.connected = false;
        cancel(this.heartbeatFuture);
        cancel(this.reconnectFuture);
        this.reconnectScheduled.set(false);

        TeamSpeakConnection currentConnection = this.connection;
        if (currentConnection != null && currentConnection.getGeneration() <= stoppedGeneration) {
            currentConnection.close();
            this.connection = null;
        }

        Minecraft.getInstance().execute(this::reset);
    }

    /**
     * Re-resolves our own client/channel via {@code whoami}. Public so {@link MembershipChangedNotify} can trigger it directly.
     */
    public void refreshIdentity() {
        new WhoAmIQuery().send(this);
    }

    private String resolveApiKey() {
        String manualApiKey = configuration.getManualApiKey().strip();
        if (!manualApiKey.isEmpty()) {
            return manualApiKey;
        }

        return this.apiKeyResolver.resolve().orElse(null);
    }

    private void onConnectionLost(int lostGeneration) {
        submit(this.scheduler, () -> {
            if (this.stopped || lostGeneration != this.generation) {
                return;
            }

            this.connected = false;
            cancel(this.heartbeatFuture);

            TeamSpeakConnection currentConnection = this.connection;
            if (currentConnection != null) {
                currentConnection.close();
                this.connection = null;
            }

            Minecraft.getInstance().execute(this::reset);

            if (this.reconnectScheduled.compareAndSet(false, true)) {
                this.reconnectFuture = this.scheduler.schedule(() -> reconnect(lostGeneration), RECONNECT_SECONDS, SECONDS);
            }
        });
    }

    private void reconnect(int reconnectGeneration) {
        this.reconnectScheduled.set(false);
        if (this.stopped || reconnectGeneration != this.generation) {
            return;
        }

        submit(this.reader, () -> connect(reconnectGeneration));
    }

    private void connect(int currentGeneration) {
        if (this.stopped || currentGeneration != this.generation) {
            return;
        }

        String apiKey = resolveApiKey();
        if (apiKey == null) {
            LOGGER.info("No TeamSpeak ClientQuery API key available, retrying in {}s", RECONNECT_SECONDS);
            onConnectionLost(currentGeneration);
            return;
        }

        TeamSpeakConnection newConnection = new TeamSpeakConnection(currentGeneration, (line, lineGeneration) -> Minecraft.getInstance().execute(() -> handleLine(line, lineGeneration)));
        try {
            newConnection.open();
        } catch (IOException e) {
            onConnectionLost(currentGeneration);
            return;
        }

        this.connection = newConnection;
        if (this.stopped || currentGeneration != this.generation) {
            newConnection.close();
            return;
        }

        this.invalidApiKey = false;
        new AuthQuery(apiKey).send(this);

        // Blocks this reader thread until the socket closes; every line it reads, meanwhile, is handed off to handleLine() on the
        // render thread via dispatchLine().
        newConnection.readLoop();

        if (!this.stopped) {
            onConnectionLost(currentGeneration);
        }
    }

    private void startHeartbeat() {
        cancel(this.heartbeatFuture);
        this.heartbeatFuture = this.scheduler.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, SECONDS);
    }

    private void reset() {
        this.ownClientId = 0;
        this.pendingCommand = null;
    }

    private void heartbeat() {
        if (this.stopped || !this.connected) {
            return;
        }

        TeamSpeakConnection currentConnection = this.connection;
        if (currentConnection == null) {
            onConnectionLost(this.generation);
            return;
        }

        Minecraft.getInstance().execute(() -> {
            if (this.stopped || currentConnection != this.connection) {
                return; // superseded by a stop()/reconnect() since this beat was scheduled
            }

            if (this.pendingCommand != null) {
                return; // a request is already in flight, skip this beat rather than clobbering it
            }

            if (!new WhoAmIQuery().send(this)) {
                this.pendingCommand = null;
                onConnectionLost(this.generation);
            }
        });
    }

    private void handleLine(String line, int lineGeneration) {
        if (this.stopped || lineGeneration != this.generation || line.isBlank()) {
            return;
        }

        // only react to lines when containing at least one key=value pair
        if (!line.contains("=")) {
            return;
        }

        // handle error / auth acknowledge
        if (line.startsWith("error id=")) {
            boolean success = line.startsWith("error id=0");

            if (this.pendingCommand instanceof AuthQuery authQuery) {
                this.pendingCommand = null;
                boolean authSuccess = authQuery.parseResponse(line);
                if (authSuccess) {
                    LOGGER.info("Connected to the TeamSpeak client");
                } else {
                    LOGGER.warn("TeamSpeak authentication failed: {}", line);
                }
                onAuthQuery(authSuccess);
            } else if (!success) {
                LOGGER.warn("TeamSpeak ClientQuery request failed: {}", line);
                this.pendingCommand = null;
            }

            return;
        }

        // handle registered notifies
        for (TeamSpeakNotify notify : NOTIFY_EVENTS) {
            if (notify.matches(line)) {
                notify.handle(line, this);
                return;
            }
        }

        // handle commands
        if (this.pendingCommand instanceof AuthQuery) {
            return;
        }

        if (this.pendingCommand instanceof TeamSpeakCommand<?> command) {
            this.pendingCommand = null;
            switch (command) {
                case AuthQuery ignored -> {}
                case WhoAmIQuery whoAmIQuery -> onWhoAmI(whoAmIQuery.parseResponse(line));
                case ChannelInfoQuery channelInfoQuery -> {
                    Channel parsedResponse = channelInfoQuery.parseResponse(line);
                    this.channel.setId(parsedResponse.getId());
                    this.channel.setName(parsedResponse.getName());
                    this.channel.setPasswordProtected(parsedResponse.isPasswordProtected());
                    this.channel.setSubscribed(parsedResponse.isSubscribed());
                    this.channel.setMaxClients(parsedResponse.getMaxClients());
                    // request channel members
                    new ChannelClientListQuery(this.channel.getId()).send(this);
                }
                case ChannelClientListQuery channelClientListQuery -> {
                    Collection<Client> clients = channelClientListQuery.parseResponse(line);
                    this.channel.getClients().clear();
                    this.channel.getClients().addAll(clients);
                }
            }
        }
    }

    private void onAuthQuery(@NonNull Boolean success) {
        if (success) {
            this.connected = true;

            TeamSpeakConnection currentConnection = this.connection;
            if (currentConnection != null) {
                for (TeamSpeakNotify notify : NOTIFY_EVENTS) {
                    notify.register(currentConnection);
                }
            }

            startHeartbeat();
            refreshIdentity();
        } else {
            this.invalidApiKey = true;
            TeamSpeakConnection currentConnection = this.connection;
            if (currentConnection != null) {
                currentConnection.close();
            }
        }
    }

    private void onWhoAmI(WhoAmIQuery.@Nullable Response response) {
        if (response == null) {
            return;
        }

        this.ownClientId = response.clientId();

        this.channel.setId(response.channelId());
        // request channel info
        new ChannelInfoQuery(this.channel.getId()).send(this);
    }

    private void cancel(Future<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private void submit(@NonNull Executor executor, Runnable runnable) {
        try {
            executor.execute(runnable);
        } catch (RejectedExecutionException e) {
            // executor was shut down; nothing to do
        }
    }

    private @NonNull Thread newDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "TeamSpeak-HUD");
        thread.setDaemon(true);
        return thread;
    }
}
