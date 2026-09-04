package de.rettichlp.teamspeakhud.teamspeak.command;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

import static de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand.parseEntry;
import static java.lang.Integer.parseInt;

public record Response<T>(boolean success, int id, @Nullable T data) {

    private static final int NOT_SENT_ID = -1;

    public static <T> @NonNull Response<T> failedToSend() {
        return new Response<>(false, NOT_SENT_ID, null);
    }

    /**
     * Parses the trailing {@code error id=...} acknowledgement line and folds {@code data} into it.
     */
    static <T> @NonNull Response<T> parse(@NonNull String errorLine, @Nullable T data) {
        Map<String, String> values = parseEntry(errorLine);
        int id = parseInt(values.getOrDefault("id", "-1"));
        boolean success = id == 0;
        return new Response<>(success, id, success ? data : null);
    }
}
