package de.rettichlp.teamspeakhud.teamspeak.command;

import de.rettichlp.teamspeakhud.teamspeak.model.Channel;
import org.jspecify.annotations.NonNull;

import java.util.Map;

import static de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand.parseEntry;
import static de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand.splitEntries;
import static de.rettichlp.teamspeakhud.teamspeak.model.Channel.UNKNOWN;
import static java.lang.Integer.parseInt;

/**
 * {@code channellist -flags -limits}: ClientQuery has no {@code channelinfo} command (that's ServerQuery-only, and replies "error
 * id=256 msg=command not found" here), so this scans the full channel list for the entry matching {@link #channelId} instead. If a
 * given ClientQuery version doesn't honor {@code -flags}/{@code -limits}, the extra fields are simply absent on that entry, and
 * {@link #parseResponse} falls back to "unknown" for them (never full, no password, subscribed).
 */
public record ChannelInfoQuery(int channelId) implements TeamSpeakCommand<Channel> {

    @Override
    public @NonNull String commandLine() {
        return "channellist -flags -limits";
    }

    @Override
    public @NonNull Channel parseResponse(@NonNull String responseLine) {
        for (String rawEntry : splitEntries(responseLine)) {
            Map<String, String> values = parseEntry(rawEntry);
            String cid = values.get("cid");
            if (cid == null || parseInt(cid) != this.channelId) {
                continue;
            }

            String name = values.getOrDefault("channel_name", "");
            boolean passwordProtected = "1".equals(values.get("channel_flag_password"));

            String maxClients = values.get("channel_maxclients");
            int parsedMaxClients = maxClients != null ? parseInt(maxClients) : -1;

            // Defaults to true: a client is implicitly subscribed to its own current channel, so a missing field here (unsupported
            // ClientQuery version) should not be read as "not subscribed".
            String subscribed = values.get("channel_flag_are_subscribed");
            boolean parsedSubscribed = subscribed == null || "1".equals(subscribed);

            return new Channel(this.channelId, name, passwordProtected, parsedSubscribed, parsedMaxClients);
        }

        return UNKNOWN;
    }
}
