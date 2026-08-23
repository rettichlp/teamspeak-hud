package de.rettichlp.teamspeakhud.teamspeak.command;

import org.jspecify.annotations.NonNull;

/**
 * {@code auth apikey=...}: authenticates this ClientQuery connection. Unlike every other command, ClientQuery never sends a data line
 * for this - only the {@code error id=...} ack, which {@link de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient#handleError} reacts
 * to directly; {@link #parseResponse} is therefore never actually invoked.
 */
public record AuthQuery(String apiKey) implements TeamSpeakCommand<Void> {

    @Override
    public @NonNull String commandLine() {
        return "auth apikey=" + this.apiKey;
    }

    @Override
    public Void parseResponse(@NonNull String responseLine) {
        throw new UnsupportedOperationException("auth never produces a data line");
    }

    /**
     * Overridden so the API key never ends up in a log line or debugger view via the record's default {@code toString()}.
     */
    @Override
    public @NonNull String toString() {
        return "AuthQuery[apiKey=<redacted>]";
    }
}
