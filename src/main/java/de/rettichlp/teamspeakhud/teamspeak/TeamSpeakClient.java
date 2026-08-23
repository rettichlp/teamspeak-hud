package de.rettichlp.teamspeakhud.teamspeak;

import de.rettichlp.teamspeakhud.teamspeak.command.AuthQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.ChannelClientListQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.ChannelClientListQuery.ClientEntry;
import de.rettichlp.teamspeakhud.teamspeak.command.ChannelInfoQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand;
import de.rettichlp.teamspeakhud.teamspeak.command.WhoAmIQuery;
import de.rettichlp.teamspeakhud.teamspeak.model.TeamSpeakChannel;
import de.rettichlp.teamspeakhud.teamspeak.notify.ClientPokeNotify;
import de.rettichlp.teamspeakhud.teamspeak.notify.IncrementalUpdateNotify;
import de.rettichlp.teamspeakhud.teamspeak.notify.MembershipChangedNotify;
import de.rettichlp.teamspeakhud.teamspeak.notify.TeamSpeakNotify;
import de.rettichlp.teamspeakhud.teamspeak.notify.TextMessageNotify;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
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
     * it reacts once such a line arrives - see {@link #onAuthenticated} and {@link #handleLine}.
     */
    private static final List<TeamSpeakNotify> NOTIFY_EVENTS = List.of(
            new MembershipChangedNotify(),
            new IncrementalUpdateNotify(),
            new ClientPokeNotify(),
            new TextMessageNotify()
    );

    private final ApiKeyResolver apiKeyResolver = new ApiKeyResolver();
    private final TeamSpeakChannel teamSpeakChannel = new TeamSpeakChannel();
    private final ExecutorService reader = newSingleThreadExecutor(this::newDaemonThread);
    private final ScheduledExecutorService scheduler = newSingleThreadScheduledExecutor(this::newDaemonThread);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private volatile TeamSpeakConnection connection;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private volatile ScheduledFuture<?> reconnectFuture;
    private volatile boolean stopped = true;
    private volatile int generation;
    private volatile boolean connected;
    private volatile boolean invalidApiKey;

    /**
     * The {@link TeamSpeakCommand} whose response we're currently waiting on, or {@code null} if none is in flight. {@code auth} is
     * just another {@link TeamSpeakCommand} here ({@link AuthQuery}) even though it never produces a data line - see
     * {@link #handleError} for how its outcome is detected instead.
     */
    private volatile TeamSpeakCommand<?> pending;
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
        send(new AuthQuery(apiKey));

        // Blocks this reader thread until the socket closes; every line it reads, meanwhile, is handed off to handleLine() on the
        // render thread via dispatchLine().
        newConnection.readLoop();

        if (!this.stopped) {
            onConnectionLost(currentGeneration);
        }
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

    private void reset() {
        this.ownClientId = 0;
        this.pending = null;
    }

    private void startHeartbeat() {
        cancel(this.heartbeatFuture);
        this.heartbeatFuture = this.scheduler.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, SECONDS);
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

            if (this.pending != null) {
                return; // a request is already in flight, skip this beat rather than clobbering it
            }

            if (!send(new WhoAmIQuery())) {
                this.pending = null;
                onConnectionLost(this.generation);
            }
        });
    }

    /**
     * Re-resolves our own client/channel via {@code whoami}. Public so {@link MembershipChangedNotify} can trigger it directly.
     */
    public void refreshIdentity() {
        send(new WhoAmIQuery());
    }

    private void requestChannelInfo() {
        send(new ChannelInfoQuery(this.teamSpeakChannel.getId()));
    }

    private void requestChannelMembers() {
        send(new ChannelClientListQuery(this.teamSpeakChannel.getId()));
    }

    /**
     * Marks {@code command} as the response we're now waiting for, then sends it (via {@link TeamSpeakCommand#send}), both in one go,
     * so {@link #pending} is never left set without a matching command actually having been sent (or the other way around). Returns
     * {@code false} if there's no connection to write to, or the write itself failed; either way {@link #pending} is left set to
     * {@code command} for the caller to reset if it cares (most callers don't: the next data line/{@code error id=0 msg=ok} ack simply
     * won't arrive, and the connection getting torn down cleans it up via {@link #reset()} regardless).
     */
    private boolean send(TeamSpeakCommand<?> command) {
        TeamSpeakConnection currentConnection = this.connection;
        if (currentConnection == null) {
            return false;
        }

        this.pending = command;
        return command.send(currentConnection);
    }

    private void handleLine(String line, int lineGeneration) {
        if (this.stopped || lineGeneration != this.generation || line.isBlank()) {
            return;
        }

        if (line.startsWith("error id=")) {
            handleError(line);
            return;
        }

        for (TeamSpeakNotify notify : NOTIFY_EVENTS) {
            if (notify.matches(line)) {
                notify.handle(line, this);
                return;
            }
        }

        // Anything left over is the data line for whichever command we last asked.
        if (this.pending instanceof TeamSpeakCommand<?> command) {
            this.pending = null;
            switch (command) {
                case AuthQuery ignored -> LOGGER.warn("Unexpected data line while awaiting auth: {}", line);
                case WhoAmIQuery whoAmIQuery -> onWhoAmI(whoAmIQuery.parseResponse(line));
                case ChannelInfoQuery channelInfoQuery -> onChannelInfo(channelInfoQuery.parseResponse(line));
                case ChannelClientListQuery channelClientListQuery -> onChannelClientList(channelClientListQuery.parseResponse(line));
            }
        }
    }

    /**
     * Every successful ClientQuery request - not just {@code auth} - is acknowledged with exactly {@code error id=0 msg=ok}; for a
     * data-returning command that ack arrives after the data line, once {@link #pending} is already back to {@code null}, so it has
     * nothing left to do here. {@code auth} is the one request with no data line at all, so this ack is the only signal of its
     * outcome.
     */
    private void handleError(@NonNull String line) {
        boolean success = line.startsWith("error id=0");

        if (this.pending instanceof AuthQuery) {
            if (success) {
                onAuthenticated();
            } else {
                LOGGER.warn("TeamSpeak authentication failed: {}", line);
                this.invalidApiKey = true;

                TeamSpeakConnection currentConnection = this.connection;
                if (currentConnection != null) {
                    currentConnection.close();
                }
            }
        } else if (!success) {
            LOGGER.warn("TeamSpeak ClientQuery request failed: {}", line);
        }

        this.pending = null;
    }

    private void onAuthenticated() {
        this.connected = true;
        LOGGER.info("Connected to the TeamSpeak client");

        for (TeamSpeakNotify notify : NOTIFY_EVENTS) {
            notify.register(this.connection);
        }

        startHeartbeat();
        refreshIdentity();
    }

    private void onWhoAmI(WhoAmIQuery.@Nullable Response response) {
        if (response == null) {
            return;
        }

        this.ownClientId = response.clientId();

        if (response.channelId() != this.teamSpeakChannel.getId()) {
            // We ourselves moved to a different channel: its members have no relationship to the previous channel's, so drop them
            // outright rather than diffing against them in onChannelClientList().
            this.teamSpeakChannel.getMembers().clear();
        }

        this.teamSpeakChannel.setId(response.channelId());
        requestChannelInfo();
    }

    private void onChannelInfo(ChannelInfoQuery.@NonNull Response response) {
        this.teamSpeakChannel.setName(response.name());
        this.teamSpeakChannel.setPasswordProtected(response.passwordProtected());
        this.teamSpeakChannel.setMaxClients(response.maxClients());
        this.teamSpeakChannel.setSubscribed(response.subscribed());

        requestChannelMembers();
    }

    private void onChannelClientList(@NonNull List<ClientEntry> entries) {
        this.teamSpeakChannel.getMembers().clear();

        for (ClientEntry entry : entries) {
            this.teamSpeakChannel.getMembers().put(entry.getClientId(), entry);
        }
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
