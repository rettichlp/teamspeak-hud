package de.rettichlp.teamspeakhud.teamspeak.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparing;
import static java.util.Locale.ROOT;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Channel {

    /**
     * Used when no channel list entry matches {@link #id} at all (e.g. we somehow lost {@code -flags}/{@code -limits}
     * support), so a channel that no longer resolves doesn't keep showing stale data from a previous refresh.
     */
    public static final Channel UNKNOWN = new Channel();

    private final List<Client> clients = new ArrayList<>();

    private int id;
    private String name = "";
    private boolean passwordProtected;
    private boolean subscribed = true;
    private int maxClients = -1; // -1 means unlimited

    /**
     * Whether the channel is at its client limit, mirroring the TeamSpeak client's own red channel icon.
     */
    public boolean isFull() {
        return this.maxClients >= 0 && this.clients.size() >= this.maxClients;
    }

    /**
     * The users currently in the channel, sorted alphabetically by nickname.
     */
    public List<Client> getClientList() {
        List<Client> sorted = new ArrayList<>(this.clients);
        sorted.sort(comparing(entry -> entry.getNickname().toLowerCase(ROOT)));
        return sorted;
    }

    /**
     * The client with {@code clientId}, or {@code null} if no such client is currently in this channel.
     */
    public @Nullable Client getClient(int clientId) {
        return this.clients.stream().filter(client -> client.getClientId() == clientId).findFirst().orElse(null);
    }
}
