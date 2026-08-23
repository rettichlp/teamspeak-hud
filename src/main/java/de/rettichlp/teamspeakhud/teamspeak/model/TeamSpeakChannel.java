package de.rettichlp.teamspeakhud.teamspeak.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparing;
import static java.util.Locale.ROOT;

@Data
public class TeamSpeakChannel {

    private final Map<Integer, Client> clients = new LinkedHashMap<>();

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
     * <p>
     * The TeamSpeak client itself actually sorts by channel group/role rank. However, ClientQuery's command set doesn't include a way
     * to resolve group IDs to their sort rank (group-list commands are ServerQuery-only), so alphabetical is the closest we can get
     * without that data.
     */
    public List<Client> getClientList() {
        List<Client> sorted = new ArrayList<>(this.clients.values());
        sorted.sort(comparing(entry -> entry.getNickname().toLowerCase(ROOT)));
        return sorted;
    }
}
