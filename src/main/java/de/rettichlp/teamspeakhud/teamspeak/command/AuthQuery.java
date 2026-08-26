package de.rettichlp.teamspeakhud.teamspeak.command;

import org.jspecify.annotations.NonNull;

/**
 * {@code auth apikey=...}: authenticates this ClientQuery connection.
 */
public record AuthQuery(String apiKey) implements TeamSpeakCommand<Void> {

    @Override
    public @NonNull String commandLine() {
        return "auth apikey=" + this.apiKey;
    }

    @Override
    public Void parseResponse(@NonNull String dataLine) {
        throw new UnsupportedOperationException("auth never sends a separate data line");
    }

    /**
     * Overridden so the API key never ends up in a log line or debugger view via the record's default {@code toString()}.
     */
    @Override
    public @NonNull String toString() {
        return "AuthQuery[apiKey=<redacted>]";
    }
}
