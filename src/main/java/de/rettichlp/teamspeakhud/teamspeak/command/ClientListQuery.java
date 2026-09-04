package de.rettichlp.teamspeakhud.teamspeak.command;

import de.rettichlp.teamspeakhud.teamspeak.model.Client;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand.parseEntry;
import static de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand.splitEntries;
import static java.lang.Integer.parseInt;

/**
 * {@code clientlist}: every client connected to the server.
 */
public record ClientListQuery() implements TeamSpeakCommand<List<Client>> {

    @Override
    public @NonNull String commandLine() {
        return "clientlist";
    }

    @Override
    public @NonNull List<Client> parseResponse(@NonNull String responseLine) {
        List<Client> clients = new ArrayList<>();

        for (String rawEntry : splitEntries(responseLine)) {
            Map<String, String> values = parseEntry(rawEntry);
            String clid = values.get("clid");
            if (clid == null) {
                continue;
            }

            clients.add(new Client(parseInt(clid), values.getOrDefault("client_nickname", ""), false, false, false, false, false, false, false, false, 0L, 0L));
        }

        return clients;
    }
}
