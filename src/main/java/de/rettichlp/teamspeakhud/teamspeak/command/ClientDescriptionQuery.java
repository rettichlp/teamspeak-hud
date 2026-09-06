package de.rettichlp.teamspeakhud.teamspeak.command;

import org.jspecify.annotations.NonNull;

import static de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand.parseEntry;

/**
 * {@code clientvariable clid=<clientId> client_description}: a single client's description.
 */
public record ClientDescriptionQuery(int clientId) implements TeamSpeakCommand<String> {

    @Override
    public @NonNull String commandLine() {
        return "clientvariable clid=" + this.clientId + " client_description";
    }

    @Override
    public @NonNull String parseResponse(@NonNull String responseLine) {
        return parseEntry(responseLine).getOrDefault("client_description", "");
    }
}
