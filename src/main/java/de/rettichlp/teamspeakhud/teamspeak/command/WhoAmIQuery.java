package de.rettichlp.teamspeakhud.teamspeak.command;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

import static de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand.parseEntry;
import static java.lang.Integer.parseInt;

/**
 * {@code whoami}: identifies our own client and current channel.
 */
public record WhoAmIQuery() implements TeamSpeakCommand<WhoAmIQuery.Response> {

    @Override
    public @NonNull String commandLine() {
        return "whoami";
    }

    @Override
    public @Nullable Response parseResponse(@NonNull String responseLine) {
        Map<String, String> values = parseEntry(responseLine);
        String clid = values.get("clid");
        String cid = values.get("cid");
        if (clid == null || cid == null) {
            return null;
        }

        return new Response(parseInt(clid), parseInt(cid));
    }

    public record Response(int clientId, int channelId) {
    }
}
