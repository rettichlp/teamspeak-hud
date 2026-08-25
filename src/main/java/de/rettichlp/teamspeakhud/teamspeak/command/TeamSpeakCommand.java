package de.rettichlp.teamspeakhud.teamspeak.command;

import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * One ClientQuery command: the exact command line it sends, how to write itself to the wire, and how to parse whatever data line it
 * gets back.
 */
public sealed interface TeamSpeakCommand<R> permits AuthQuery, WhoAmIQuery, ChannelInfoQuery, ChannelClientListQuery {

    char BELL = 0x0007;

    char VERTICAL_TAB = 0x000B;

    /**
     * The raw ClientQuery command line to send.
     */
    @NonNull String commandLine();

    /**
     * Maps the raw data line ClientQuery sent back for this request into {@code R}.
     */
    R parseResponse(@NonNull String responseLine);

    /**
     * Whether ClientQuery answers this command with just the {@code error id=...} acknowledgement line instead of a separate data
     * line.
     */
    default boolean respondsViaErrorLine() {
        return false;
    }

    /**
     * Enqueues this command on {@code teamSpeakClient} and returns a future for its parsed response. The future completes with
     * {@code null} if the command couldn't be sent at all or if ClientQuery reported a failure for it.
     */
    default @NonNull CompletableFuture<R> send(@NonNull TeamSpeakClient teamSpeakClient) {
        return teamSpeakClient.enqueue(this).thenApply(responseLine -> responseLine == null ? null : parseResponse(responseLine));
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
