package de.rettichlp.teamspeakhud.teamspeak.command;

import de.rettichlp.teamspeakhud.teamspeak.model.Channel;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand.parseEntry;
import static de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand.splitEntries;
import static java.lang.Integer.parseInt;

/**
 * {@code channellist}: every channel on the server with an id and name.
 */
public record ChannelListQuery() implements TeamSpeakCommand<List<Channel>> {

    @Override
    public @NonNull String commandLine() {
        return "channellist";
    }

    @Override
    public @NonNull List<Channel> parseResponse(@NonNull String responseLine) {
        List<Channel> channels = new ArrayList<>();

        for (String rawEntry : splitEntries(responseLine)) {
            Map<String, String> values = parseEntry(rawEntry);
            String cid = values.get("cid");
            if (cid == null) {
                continue;
            }

            channels.add(new Channel(parseInt(cid), values.getOrDefault("channel_name", ""), false, true, -1));
        }

        return channels;
    }
}
