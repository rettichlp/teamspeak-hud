package de.rettichlp.teamspeakhud.teamspeak.command;

import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public sealed interface TeamSpeakCommand<T> permits AuthQuery, WhoAmIQuery, ChannelInfoQuery, ChannelClientListQuery {

    char BELL = 0x0007;

    char VERTICAL_TAB = 0x000B;

    @NonNull String commandLine();

    T parseResponse(@NonNull String dataLine);

    default @NonNull Response<T> buildResponse(@NonNull String errorLine, @Nullable T data) {
        return Response.parse(errorLine, data);
    }

    default @NonNull CompletableFuture<Response<T>> send(@NonNull TeamSpeakClient teamSpeakClient) {
        return teamSpeakClient.getRequestQueue().enqueue(this);
    }

    static @NonNull Map<String, String> parseEntry(@NonNull String entry) {
        Map<String, String> values = new LinkedHashMap<>();

        for (String token : entry.trim().split(" ")) {
            int separatorIndex = token.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = token.substring(0, separatorIndex);
            String value = unescape(token.substring(separatorIndex + 1));
            values.put(key, value);
        }

        return values;
    }

    static String @NonNull [] splitEntries(@NonNull String line) {
        return line.split("\\|");
    }

    @SuppressWarnings("AssignmentToForLoopParameter")
    private static @NonNull String unescape(@NonNull CharSequence value) {
        StringBuilder result = new StringBuilder(value.length());

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            if (character != '\\' || i + 1 >= value.length()) {
                result.append(character);
                continue;
            }

            char next = value.charAt(++i);
            switch (next) {
                case 's' -> result.append(' ');
                case 'p' -> result.append('|');
                case '/' -> result.append('/');
                case '\\' -> result.append('\\');
                case 'a' -> result.append(BELL);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'v' -> result.append(VERTICAL_TAB);
                default -> result.append(next);
            }
        }

        return result.toString();
    }
}
