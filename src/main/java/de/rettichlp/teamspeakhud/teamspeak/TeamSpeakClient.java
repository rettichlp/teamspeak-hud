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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.LOGGER;
import static de.rettichlp.teamspeakhud.TeamSpeakHud.configuration;
import static de.rettichlp.teamspeakhud.teamspeak.Reconnector.RECONNECT_SECONDS;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Getter
public class TeamSpeakClient {

    /**
     * Every notification event this mod reacts to, each owning both its ClientQuery event name(s) (for {@code clientnotifyregister})
     * and how it reacts once such a line arrives.
     */
    private static final List<TeamSpeakNotify> NOTIFY_EVENTS = List.of(
            new MembershipChangedNotify(),
            new IncrementalUpdateNotify(),
            new ClientPokeNotify(),
            new TextMessageNotify()
    );

    private final AtomicInteger generation = new AtomicInteger();
    private final ApiKeyResolver apiKeyResolver = new ApiKeyResolver();
    private final Channel channel = new Channel();
    private final ExecutorService reader = newSingleThreadExecutor(this::newDaemonThread);
    private final ScheduledExecutorService scheduler = newSingleThreadScheduledExecutor(this::newDaemonThread);
    private final Heartbeat heartbeat = new Heartbeat(this);
    private final Reconnector reconnector = new Reconnector(this);

    /**
     * The {@link TeamSpeakCommand} whose response we're currently waiting on, or {@code null} if none is in flight.
     */
    @Setter
    private volatile TeamSpeakCommand<?> pendingCommand;
    private volatile TeamSpeakConnection connection;
    private volatile boolean stopped = true;
    private volatile boolean connected;

    private int ownClientId;

    public void start() {
        this.stopped = false;
        int currentGeneration = this.generation.incrementAndGet();
        connect(currentGeneration);
    }

    /**
     * Pauses the client, e.g. when the mod is disabled in the option screen. Resumable via a later {@link #start()}. The executors are
     * left running for that.
     *
     * @see #shutdown()
     */
    public void stop() {
        this.stopped = true;
        int stoppedGeneration = this.generation.incrementAndGet();
        submit(this.scheduler, () -> {
            this.connected = false;
            this.heartbeat.cancel();
            this.reconnector.cancel();

            TeamSpeakConnection currentConnection = this.connection;
            if (currentConnection != null && currentConnection.getGeneration() <= stoppedGeneration) {
                currentConnection.close();
                this.connection = null;
            }

            Minecraft.getInstance().execute(this::reset);
        });
    }

    /**
     * Tears the client down for good, e.g. when the game itself is closing. Unlike {@link #stop()}, this closes the connection
     * synchronously on the caller's thread before killing the executors.
     */
    public void shutdown() {
        this.stopped = true;

        TeamSpeakConnection currentConnection = this.connection;
        if (currentConnection != null) {
            currentConnection.close();
        }

        this.reader.shutdownNow();
        this.scheduler.shutdownNow();
    }

    public void connect(int currentGeneration) {
        submit(this.reader, () -> {
            if (this.stopped || currentGeneration != this.generation.get()) {
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
            if (this.stopped || currentGeneration != this.generation.get()) {
                newConnection.close();
                return;
            }

            new AuthQuery(apiKey).send(this);

            // blocks this reader thread until the socket closes; every line it reads, meanwhile, is handed off to handleLine()
            newConnection.readLoop();

            if (!this.stopped) {
                onConnectionLost(currentGeneration);
            }
        });
    }

    public void onConnectionLost(int lostGeneration) {
        submit(this.scheduler, () -> {
            if (this.stopped || lostGeneration != this.generation.get()) {
                return;
            }

            this.connected = false;
            this.heartbeat.cancel();

            TeamSpeakConnection currentConnection = this.connection;
            if (currentConnection != null) {
                currentConnection.close();
                this.connection = null;
            }

            Minecraft.getInstance().execute(this::reset);
            this.reconnector.schedule(lostGeneration);
        });
    }

    /**
     * Re-resolves our own client/channel via {@code whoami}.
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

    private void reset() {
        this.ownClientId = 0;
        this.pendingCommand = null;
    }

    private void handleLine(String line, int lineGeneration) {
        if (this.stopped || lineGeneration != this.generation.get() || line.isBlank()) {
            return;
        }

        // only react to lines when containing at least one key=value pair
        if (!line.contains("=")) {
            return;
        }

        // handle error / auth acknowledge
        if (line.startsWith("error id=")) {
            boolean failure = !line.startsWith("error id=0");

            if (this.pendingCommand instanceof AuthQuery authQuery) {
                this.pendingCommand = null;
                boolean authSuccess = authQuery.parseResponse(line);
                if (authSuccess) {
                    LOGGER.info("Connected to the TeamSpeak client");
                } else {
                    LOGGER.warn("TeamSpeak authentication failed: {}", line);
                }
                onAuthQuery(authSuccess);
            } else if (failure) {
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
                case AuthQuery ignored -> {
                }
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
                    Collection<Client> currentClients = channelClientListQuery.parseResponse(line);
                    List<Client> previousClients = this.channel.getClients();
                    Map<Integer, Client> previousClientsById = previousClients.stream().collect(toMap(Client::getClientId, identity()));

                    // populating a previously empty list means this is the first list for a channel we just entered/connected to:
                    // members were already there, not people who "just joined", so they shouldn't be highlighted
                    boolean isInitialPopulation = previousClients.isEmpty();
                    long now = currentTimeMillis();

                    Collection<Client> merged = new ArrayList<>(currentClients.size());
                    for (Client reportedClient : currentClients) {
                        Client previousClient = previousClientsById.remove(reportedClient.getClientId());
                        boolean freshlyJoined = previousClient == null || previousClient.hasLeavingHighlight();
                        reportedClient.setJoinedAt(freshlyJoined ? (isInitialPopulation ? 0L : now) : previousClient.getJoinedAt());
                        merged.add(reportedClient);
                    }

                    for (Client leftClient : previousClientsById.values()) {
                        if (!leftClient.hasLeavingHighlight()) {
                            leftClient.setLeftAt(now);
                        }

                        merged.add(leftClient);
                    }

                    previousClients.clear();
                    previousClients.addAll(merged);
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

            this.heartbeat.start();
            refreshIdentity();
        } else {
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

        int newChannelId = response.channelId();
        if (newChannelId != this.channel.getId()) {
            this.channel.getClients().clear();
        }

        this.channel.setId(newChannelId);
        // request channel info
        new ChannelInfoQuery(this.channel.getId()).send(this);
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
