package de.rettichlp.teamspeakhud.teamspeak.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static de.rettichlp.teamspeakhud.teamspeak.model.Client.TRANSITION_HIGHLIGHT_DURATION_MILLIS;
import static java.lang.System.currentTimeMillis;
import static java.util.Comparator.comparing;
import static java.util.Locale.ROOT;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Channel {

    /**
     * Used when no channel list entry matches {@link #id} at all (e.g. we somehow lost {@code -flags}/{@code -limits} support), so a
     * channel that no longer resolves doesn't keep showing stale data from a previous refresh.
     */
    public static final Channel UNKNOWN = new Channel();

    private final List<Client> clients = new ArrayList<>();

    private int id;
    private String name = "";
    private boolean passwordProtected;
    private boolean subscribed = true;
    private int maxClients = -1; // -1 means unlimited

    public boolean isFull() {
        if (this.maxClients < 0) {
            return false;
        }

        return this.clients.stream().filter(client -> !client.hasLeavingHighlight()).count() >= this.maxClients;
    }

    /**
     * The users currently in the channel, sorted alphabetically by nickname.
     */
    public List<Client> getClientList() {
        long now = currentTimeMillis();
        this.clients.removeIf(entry -> entry.hasLeavingHighlight() && now - entry.getLeftAt() >= TRANSITION_HIGHLIGHT_DURATION_MILLIS);

        List<Client> sorted = new ArrayList<>(this.clients);
        sorted.sort(comparing(entry -> entry.getNickname().toLowerCase(ROOT)));
        return sorted;
    }

    public @Nullable Client getClient(int clientId) {
        return this.clients.stream().filter(client -> client.getClientId() == clientId).findFirst().orElse(null);
    }
}
