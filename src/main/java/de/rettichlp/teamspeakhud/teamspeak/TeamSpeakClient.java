package de.rettichlp.teamspeakhud.teamspeak;

import de.rettichlp.teamspeakhud.teamspeak.model.TeamSpeakChannel;
import de.rettichlp.teamspeakhud.teamspeak.model.TeamSpeakUser;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
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
import static de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient.PendingResponse.AUTH;
import static de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient.PendingResponse.CHANNEL_CLIENT_LIST;
import static de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient.PendingResponse.CHANNEL_INFO;
import static de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient.PendingResponse.NONE;
import static de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient.PendingResponse.WHOAMI;
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
    private static final char BELL = 0x0007;
    private static final char VERTICAL_TAB = 0x000B;

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
    private volatile PendingResponse pending = NONE;
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
        sendRequest(AUTH, "auth apikey=" + apiKey);

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
        this.pending = NONE;
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

            if (this.pending != NONE) {
                return; // a request is already in flight, skip this beat rather than clobbering it
            }

            if (!sendRequest(WHOAMI, "whoami")) {
                this.pending = NONE;
                onConnectionLost(this.generation);
            }
        });
    }

    private void refreshIdentity() {
        sendRequest(WHOAMI, "whoami");
    }

    private void requestChannelInfo() {
        // "channelinfo" is a ServerQuery-only command (ClientQuery replies "error id=256 msg=command not found" for it). ClientQuery
        // only exposes "channellist" (optionally with -flags/-limits), so onChannelInfo() below scans that for the entry matching our
        // own channel ID instead. If a given ClientQuery version doesn't honor -flags/-limits, the extra fields are simply absent
        // there, and onChannelInfo() falls back to "unknown" (never full, no password) for them.
        sendRequest(CHANNEL_INFO, "channellist -flags -limits");
    }

    private void requestChannelMembers() {
        sendRequest(CHANNEL_CLIENT_LIST, "channelclientlist cid=" + this.teamSpeakChannel.getId() + " -voice -away");
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
     * Marks {@code expected} as the response we're now waiting for, then sends {@code command}, both in one go, so {@link #pending} is
     * never left set without a matching command actually having been sent (or the other way around). Returns {@code false} if
     * there's no connection to write to, or the write itself failed; either way {@link #pending} is left at {@code expected} for the
     * caller to reset if it cares (most callers don't: the next {@code error id=0 msg=ok}/data line simply won't arrive, and the
     * connection getting torn down cleans it up via {@link #reset()} regardless).
     */
    private boolean sendRequest(PendingResponse expected, String command) {
        TeamSpeakConnection currentConnection = this.connection;
        if (currentConnection == null) {
            return false;
        }

        this.pending = expected;
        return currentConnection.write(command);
    }

    private void dispatchLine(String line, int lineGeneration) {
        Minecraft.getInstance().execute(() -> handleLine(line, lineGeneration));
    }

    private void handleLine(String line, int lineGeneration) {
        if (this.stopped || lineGeneration != this.generation || line.isBlank()) {
            return;
        }

        if (line.equals("error id=0 msg=ok")) {
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

        // Anything left over is either the data line or the "error id=0 msg=ok" ack for whatever we last asked.
        switch (this.pending) {
            case AUTH -> onAuthenticated();
            case WHOAMI -> onWhoAmI(line);
            case CHANNEL_INFO -> onChannelInfo(line);
            case CHANNEL_CLIENT_LIST -> onChannelClientList(line);
            case NONE -> { /* unsolicited line outside a known request, ignore */ }
        }
    }

    private void handleError(@NonNull String line) {
        boolean success = line.startsWith("error id=0");

        if (this.pending == AUTH && !success) {
            LOGGER.warn("TeamSpeak authentication failed: {}", line);
            this.invalidApiKey = true;
            this.pending = NONE;

            TeamSpeakConnection currentConnection = this.connection;
            if (currentConnection != null) {
                currentConnection.close();
            }

            return;
        }

        if (!success) {
            LOGGER.warn("TeamSpeak ClientQuery request failed: {}", line);
        }

        this.pending = NONE;
    }

    private void onAuthenticated() {
        this.pending = NONE;
        this.connected = true;
        LOGGER.info("Connected to the TeamSpeak client");

        for (String event : NOTIFY_EVENTS) {
            send("clientnotifyregister schandlerid=0 event=" + event);
        }

        startHeartbeat();
        refreshIdentity();
    }

    private void onWhoAmI(String line) {
        this.pending = NONE;

        Map<String, String> values = parseEntry(line);
        String clid = values.get("clid");
        String cid = values.get("cid");
        if (clid == null || cid == null) {
            return;
        }

        this.ownClientId = parseInt(clid);
        this.teamSpeakChannel.setId(parseInt(cid));
        requestChannelInfo();
    }

    private void onChannelInfo(String line) {
        this.pending = NONE;

        // Reset the resolved fields (but not the id - that's the key we match entries against below) so a channel that no longer
        // matches anything (e.g. we somehow lost -flags/-limits support) doesn't keep showing stale data from a previous refresh.
        this.teamSpeakChannel.setName("");
        this.teamSpeakChannel.setPasswordProtected(false);
        this.teamSpeakChannel.setMaxClients(-1);
        this.teamSpeakChannel.setSubscribed(true);

        for (String rawEntry : splitEntries(line)) {
            Map<String, String> values = parseEntry(rawEntry);
            String cid = values.get("cid");
            if (cid == null || parseInt(cid) != this.teamSpeakChannel.getId()) {
                continue;
            }

            this.teamSpeakChannel.setName(values.getOrDefault("channel_name", ""));
            this.teamSpeakChannel.setPasswordProtected("1".equals(values.get("channel_flag_password")));

            String maxClients = values.get("channel_maxclients");
            this.teamSpeakChannel.setMaxClients(maxClients != null ? parseInt(maxClients) : -1);

            // Defaults to true above: a client is implicitly subscribed to its own current channel, so a missing field here
            // (unsupported ClientQuery version) should not be read as "not subscribed".
            String subscribed = values.get("channel_flag_are_subscribed");
            this.teamSpeakChannel.setSubscribed(subscribed == null || "1".equals(subscribed));
            break;
        }

        requestChannelMembers();
    }

    private void onChannelClientList(String line) {
        this.pending = NONE;
        this.teamSpeakChannel.getMembers().clear();

        for (String rawEntry : splitEntries(line)) {
            Map<String, String> values = parseEntry(rawEntry);
            String clid = values.get("clid");
            if (clid == null) {
                continue;
            }

            TeamSpeakUser user = new TeamSpeakUser(parseInt(clid));
            applyValues(user, values);
            this.teamSpeakChannel.getMembers().put(user.getClientId(), user);
        }
    }

    private void updateMemberFromNotify(String line) {
        Map<String, String> values = parseEntry(line);
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
        if (!configuration.isNotificationsEnabled()) {
            return;
        }

        if (line.startsWith("notifyclientpoke")) {
            Map<String, String> values = parseEntry(line);
            String invokerName = values.getOrDefault("invokername", "?");
            showToast(literal(invokerName), values.get("msg"));
        } else if (line.startsWith("notifytextmessage")) {
            Map<String, String> values = parseEntry(line);

            // don't toast our own messages being echoed back to us
            String invokerId = values.get("invokerid");
            if (invokerId != null && parseInt(invokerId) == this.ownClientId) {
                return;
            }

            String invokerName = values.getOrDefault("invokername", "?");

            // Channel messages name the channel they were sent in, since that's not otherwise obvious from the toast; private/server
            // messages just show who sent them.
            Component title = "2".equals(values.get("targetmode"))
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
     * Copies whichever of these ClientQuery fields are present in {@code values} onto {@code user}. Used both for the full
     * {@code channelclientlist} refresh (all fields present) and for incremental notify updates (only the field(s) that actually
     * changed are present); hence every field is guarded by its own {@code containsKey}, rather than assuming the whole set is always
     * there.
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

    private @NonNull Map<String, String> parseEntry(@NonNull String entry) {
        Map<String, String> values = new LinkedHashMap<>();

        for (String token : entry.trim().split(" ")) {
            int separatorIndex = token.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = token.substring(0, separatorIndex);
            String value = unescape(token.substring(separatorIndex + 1));
            values.put(key, value);
        }

        return values;
    }

    @Contract(pure = true)
    private String @NonNull [] splitEntries(@NonNull String line) {
        return line.split("\\|");
    }

    private static @NonNull String unescape(@NonNull CharSequence value) {
        StringBuilder result = new StringBuilder(value.length());

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            if (character != '\\' || i + 1 >= value.length()) {
                result.append(character);
                continue;
            }

            char next = value.charAt(++i);
            switch (next) {
                case 's' -> result.append(' ');
                case 'p' -> result.append('|');
                case '/' -> result.append('/');
                case '\\' -> result.append('\\');
                case 'a' -> result.append(BELL);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'v' -> result.append(VERTICAL_TAB);
                default -> result.append(next);
            }
        }

        return result.toString();
    }

    enum PendingResponse {

        NONE,
        AUTH,
        WHOAMI,
        CHANNEL_INFO,
        CHANNEL_CLIENT_LIST
    }
}
