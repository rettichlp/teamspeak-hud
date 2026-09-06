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
 * {@code channelclientlist cid=<channelId> -voice -away}: the full member list of one channel.
 */
public record ChannelClientListQuery(int channelId) implements TeamSpeakCommand<List<Client>> {

    @Override
    public @NonNull String commandLine() {
        return "channelclientlist cid=" + this.channelId + " -voice -away";
    }

    @Override
    public @NonNull List<Client> parseResponse(@NonNull String dataLine) {
        List<Client> clients = new ArrayList<>();

        for (String rawEntry : splitEntries(dataLine)) {
            Map<String, String> values = parseEntry(rawEntry);
            String clid = values.get("clid");
            if (clid == null) {
                continue;
            }

            clients.add(new Client(
                    parseInt(clid),
                    values.getOrDefault("client_nickname", ""),
                    "1".equals(values.get("client_flag_talking")),
                    "1".equals(values.get("client_input_muted")),
                    "1".equals(values.get("client_output_muted")),
                    "0".equals(values.get("client_input_hardware")),
                    "0".equals(values.get("client_output_hardware")),
                    "1".equals(values.get("client_away")),
                    "1".equals(values.get("client_is_muted")),
                    "1".equals(values.get("client_is_channel_commander")),
                    0L,
                    0L
            ));
        }

        return clients;
    }
}
