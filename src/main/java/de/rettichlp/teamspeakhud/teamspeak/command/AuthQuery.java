package de.rettichlp.teamspeakhud.teamspeak.command;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

/**
 * {@code auth apikey=...}: authenticates this ClientQuery connection.
 */
public record AuthQuery(String apiKey) implements TeamSpeakCommand<Boolean> {

    @Override
    public @NonNull String commandLine() {
        return "auth apikey=" + this.apiKey;
    }

    @Contract(pure = true)
    @Override
    public @NonNull Boolean parseResponse(@NonNull String responseLine) {
        return responseLine.startsWith("error id=0");
    }

    /**
     * Overridden so the API key never ends up in a log line or debugger view via the record's default {@code toString()}.
     */
    @Override
    public @NonNull String toString() {
        return "AuthQuery[apiKey=<redacted>]";
    }
}
