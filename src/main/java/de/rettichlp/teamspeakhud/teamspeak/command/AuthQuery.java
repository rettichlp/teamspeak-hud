package de.rettichlp.teamspeakhud.teamspeak.command;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.LOGGER;

/**
 * {@code auth apikey=...}: authenticates this ClientQuery connection.
 */
public record AuthQuery(String apiKey) implements TeamSpeakCommand<Void> {

    @Override
    public @NonNull String commandLine() {
        return "auth apikey=" + this.apiKey;
    }

    @Override
    public @Nullable Void parseResponse(@NonNull String dataLine) {
        LOGGER.warn("Unexpected data line for auth request: {}", dataLine);
        return null;
    }

    @Override
    public @NonNull String toString() {
        return "AuthQuery[apiKey=<redacted>]"; // hide api key in logs
    }
}
