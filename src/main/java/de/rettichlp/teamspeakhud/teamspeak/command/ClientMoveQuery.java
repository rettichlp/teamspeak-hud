package de.rettichlp.teamspeakhud.teamspeak.command;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.LOGGER;

/**
 * {@code clientmove clid=&lt;clientId&gt; cid=&lt;channelId&gt;}: moves a client.
 */
public record ClientMoveQuery(int clientId, int channelId) implements TeamSpeakCommand<Void> {

    @Override
    public @NonNull String commandLine() {
        return "clientmove clid=" + this.clientId + " cid=" + this.channelId;
    }

    @Override
    public @Nullable Void parseResponse(@NonNull String dataLine) {
        LOGGER.warn("Unexpected data line for clientmove request: {}", dataLine);
        return null;
    }
}
