package de.rettichlp.teamspeakhud.teamspeak;

import de.rettichlp.teamspeakhud.teamspeak.command.ChannelClientListQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.ChannelClientListQuery.ClientEntry;
import de.rettichlp.teamspeakhud.teamspeak.command.ChannelInfoQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.WhoAmIQuery;
import de.rettichlp.teamspeakhud.teamspeak.model.TeamSpeakChannel;
import de.rettichlp.teamspeakhud.teamspeak.model.TeamSpeakUser;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.LOGGER;
import static de.rettichlp.teamspeakhud.TeamSpeakHud.configuration;
import static java.lang.Integer.parseInt;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static net.minecraft.network.chat.Component.literal;
import static net.minecraft.network.chat.Component.translatable;

@Getter
public class TeamSpeakClient {

    private static final long HEARTBEAT_SECONDS = 5L;
    private static final long RECONNECT_SECONDS = 10L;

    /**
     * Notify events meaning "who's in the channel changed".
     */
    private static final Set<String> MEMBERSHIP_EVENTS = Set.of("notifycliententerview", "notifyclientleftview", "notifyclientmoved");

    /**
     * Notify events that only change one user's status.
     */
    private static final Set<String> INCREMENTAL_UPDATE_EVENTS = Set.of("notifytalkstatuschange", "notifyclientupdated");

    /**
     * Notify events that trigger a toast notification rather than updating any state.
     */
    private static final Set<String> NOTIFICATION_EVENTS = Set.of("notifyclientpoke", "notifytextmessage");

    private static final Set<String> NOTIFY_EVENTS = concat(concat(MEMBERSHIP_EVENTS.stream(), INCREMENTAL_UPDATE_EVENTS.stream()), NOTIFICATION_EVENTS.stream()).collect(toSet());

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
     * Either {@link Pending#NONE}/{@link Pending#AUTH}, or a {@link TeamSpeakQuery} instance whose data line we're waiting on - see
     * {@link Pending} for why this isn't a single sealed type.
     */
    private volatile Object pending = Pending.NONE;
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

        TeamSpeakConnection newConnection = new TeamSpeakConnection(currentGeneration, this::dispatchLine);
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
        sendAuth(apiKey);

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
        this.pending = Pending.NONE;
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

            if (this.pending != Pending.NONE) {
                return; // a request is already in flight, skip this beat rather than clobbering it
            }

            if (!sendQuery(new WhoAmIQuery())) {
                this.pending = Pending.NONE;
                onConnectionLost(this.generation);
            }
        });
    }

    private void refreshIdentity() {
        sendQuery(new WhoAmIQuery());
    }

    private void requestChannelInfo() {
        sendQuery(new ChannelInfoQuery(this.teamSpeakChannel.getId()));
    }

    private void requestChannelMembers() {
        sendQuery(new ChannelClientListQuery(this.teamSpeakChannel.getId()));
    }

    /**
     * Writes a fire-and-forget command we don't act on the response of (e.g. {@code clientnotifyregister}), silently doing nothing if
     * there's currently no connection.
     */
    private void send(String command) {
        TeamSpeakConnection currentConnection = this.connection;
        if (currentConnection != null) {
            currentConnection.write(command);
        }
    }

    /**
     * Marks {@link Pending#AUTH} as the response we're now waiting for, then sends the {@code auth} command, both in one go, so
     * {@link #pending} is never left set without a matching command actually having been sent. Unlike every other request, ClientQuery
     * never sends a data line for {@code auth} - only the success/failure of the request itself, via {@link #handleError}.
     */
    private void sendAuth(String apiKey) {
        TeamSpeakConnection currentConnection = this.connection;
        if (currentConnection == null) {
            return;
        }

        this.pending = Pending.AUTH;
        currentConnection.write("auth apikey=" + apiKey);
    }

    /**
     * Marks {@code query} as the response we're now waiting for, then sends its command line, both in one go, so {@link #pending} is
     * never left set without a matching command actually having been sent (or the other way around). Returns {@code false} if
     * there's no connection to write to, or the write itself failed; either way {@link #pending} is left set to {@code query} for the
     * caller to reset if it cares (most callers don't: the next data line/{@code error id=0 msg=ok} ack simply won't arrive, and the
     * connection getting torn down cleans it up via {@link #reset()} regardless).
     */
    private boolean sendQuery(TeamSpeakQuery<?> query) {
        TeamSpeakConnection currentConnection = this.connection;
        if (currentConnection == null) {
            return false;
        }

        this.pending = query;
        return currentConnection.write(query.commandLine());
    }

    private void dispatchLine(String line, int lineGeneration) {
        Minecraft.getInstance().execute(() -> handleLine(line, lineGeneration));
    }

    private void handleLine(String line, int lineGeneration) {
        if (this.stopped || lineGeneration != this.generation || line.isBlank()) {
            return;
        }

        if (line.startsWith("error id=")) {
            handleError(line);
            return;
        }

        if (MEMBERSHIP_EVENTS.stream().anyMatch(line::startsWith)) {
            refreshIdentity();
            return;
        }

        if (INCREMENTAL_UPDATE_EVENTS.stream().anyMatch(line::startsWith)) {
            updateMemberFromNotify(line);
            return;
        }

        if (NOTIFICATION_EVENTS.stream().anyMatch(line::startsWith)) {
            onNotificationEvent(line);
            return;
        }

        // Anything left over is the data line for whichever query we last asked (auth never produces one, see sendAuth()).
        if (this.pending instanceof TeamSpeakQuery<?> query) {
            this.pending = Pending.NONE;
            applyQueryResponse(query, line);
        }
    }

    private void applyQueryResponse(TeamSpeakQuery<?> query, String line) {
        switch (query) {
            case WhoAmIQuery whoAmIQuery -> onWhoAmI(whoAmIQuery.parseResponse(line));
            case ChannelInfoQuery channelInfoQuery -> onChannelInfo(channelInfoQuery.parseResponse(line));
            case ChannelClientListQuery channelClientListQuery -> onChannelClientList(channelClientListQuery.parseResponse(line));
        }
    }

    /**
     * Every successful ClientQuery request - not just {@code auth} - is acknowledged with exactly {@code error id=0 msg=ok}; for a
     * data-returning query that ack arrives after the data line, once {@link #pending} is already back to {@link Pending#NONE}, so it
     * has nothing left to do here. {@code auth} is the one request with no data line at all, so this ack is the only signal of its
     * outcome.
     */
    private void handleError(@NonNull String line) {
        boolean success = line.startsWith("error id=0");

        if (this.pending == Pending.AUTH) {
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

        this.pending = Pending.NONE;
    }

    private void onAuthenticated() {
        this.connected = true;
        LOGGER.info("Connected to the TeamSpeak client");

        for (String event : NOTIFY_EVENTS) {
            send("clientnotifyregister schandlerid=0 event=" + event);
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
            TeamSpeakUser user = new TeamSpeakUser(entry.clientId());
            user.setNickname(entry.nickname());
            user.setTalking(entry.talking());
            user.setInputMuted(entry.inputMuted());
            user.setOutputMuted(entry.outputMuted());
            user.setInputHardwareDisabled(entry.inputHardwareDisabled());
            user.setOutputHardwareDisabled(entry.outputHardwareDisabled());
            user.setAway(entry.away());
            user.setLocallyMuted(entry.locallyMuted());
            user.setChannelCommander(entry.channelCommander());

            this.teamSpeakChannel.getMembers().put(user.getClientId(), user);
        }
    }

    private void updateMemberFromNotify(String line) {
        Map<String, String> values = ClientQueryLine.parseEntry(line);
        String clid = values.get("clid");
        if (clid == null) {
            return;
        }

        TeamSpeakUser user = this.teamSpeakChannel.getMembers().get(parseInt(clid));
        if (user != null) {
            applyValues(user, values);
        }
    }

    private void onNotificationEvent(String line) {
        if (line.startsWith("notifyclientpoke")) {
            if (!configuration.isPokeNotificationsEnabled()) {
                return;
            }

            Map<String, String> values = ClientQueryLine.parseEntry(line);
            String invokerName = values.getOrDefault("invokername", "?");
            showToast(literal(invokerName), values.get("msg"));
        } else if (line.startsWith("notifytextmessage")) {
            Map<String, String> values = ClientQueryLine.parseEntry(line);

            // don't toast our own messages being echoed back to us
            String invokerId = values.get("invokerid");
            if (invokerId != null && parseInt(invokerId) == this.ownClientId) {
                return;
            }

            boolean isChannelMessage = "2".equals(values.get("targetmode"));
            if (isChannelMessage ? !configuration.isChannelMessageNotificationsEnabled() : !configuration.isPrivateMessageNotificationsEnabled()) {
                return;
            }

            String invokerName = values.getOrDefault("invokername", "?");
            Component title = isChannelMessage
                    ? translatable("tsh.notification.message.channel.title", invokerName, this.teamSpeakChannel.getName())
                    : literal(invokerName);

            showToast(title, values.get("msg"));
        }
    }

    private void showToast(Component title, @Nullable String message) {
        Component messageComponent = message == null || message.isEmpty() ? null : literal(message);
        SystemToast.add(Minecraft.getInstance().gui.toastManager(), new SystemToast.SystemToastId(), title, messageComponent);
    }

    /**
     * Applies whichever of these ClientQuery fields are present in {@code values} onto {@code user}. Used for incremental notify
     * updates only (a full member refresh goes through {@link #onChannelClientList}'s {@link ClientEntry} instead), where only the
     * field(s) that actually changed are present; hence every field is guarded by its own {@code containsKey}, rather than assuming
     * the whole set is always there.
     */
    private void applyValues(TeamSpeakUser user, @NonNull Map<String, String> values) {
        if (values.containsKey("client_nickname")) {
            user.setNickname(values.get("client_nickname"));
        }

        if (values.containsKey("client_flag_talking")) {
            user.setTalking("1".equals(values.get("client_flag_talking")));
        }

        // notifytalkstatuschange reports the talk state as "status" (1 = talking) instead of client_flag_talking.
        if (values.containsKey("status")) {
            user.setTalking("1".equals(values.get("status")));
        }

        if (values.containsKey("client_input_muted")) {
            user.setInputMuted("1".equals(values.get("client_input_muted")));
        }

        if (values.containsKey("client_output_muted")) {
            user.setOutputMuted("1".equals(values.get("client_output_muted")));
        }

        // client_input_hardware/client_output_hardware are 0 when no microphone/playback device is available at all, distinct from the
        // user muting themselves.
        if (values.containsKey("client_input_hardware")) {
            user.setInputHardwareDisabled("0".equals(values.get("client_input_hardware")));
        }

        if (values.containsKey("client_output_hardware")) {
            user.setOutputHardwareDisabled("0".equals(values.get("client_output_hardware")));
        }

        if (values.containsKey("client_away")) {
            user.setAway("1".equals(values.get("client_away")));
        }

        if (values.containsKey("client_is_muted")) {
            user.setLocallyMuted("1".equals(values.get("client_is_muted")));
        }

        if (values.containsKey("client_is_channel_commander")) {
            user.setChannelCommander("1".equals(values.get("client_is_channel_commander")));
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

    /**
     * The two states {@link #pending} can be in that aren't "waiting on a particular {@link TeamSpeakQuery}'s data line": nothing in
     * flight, or the one request ({@code auth}) that never produces a data line at all. For every other request, {@link #pending}
     * instead holds the {@link TeamSpeakQuery} instance itself, so {@link #handleLine} can pattern-match it straight to the concrete
     * query without an extra unwrapping step; {@link #pending} is typed {@code Object} rather than a single sealed type to allow both.
     */
    private enum Pending {
        NONE,
        AUTH
    }
}
