package de.rettichlp.teamspeakhud.teamspeak.notify;

import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Set;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.configuration;
import static net.minecraft.network.chat.Component.literal;

/**
 * {@code notifyclientpoke}: someone poked us.
 */
public record ClientPokeNotify() implements TeamSpeakNotify {

    private static final Set<String> EVENT_NAMES = Set.of("notifyclientpoke");

    @Override
    public @NonNull Set<String> eventNames() {
        return EVENT_NAMES;
    }

    @Override
    public void handle(@NonNull String line, @NonNull TeamSpeakClient client) {
        if (!configuration.isPokeNotificationsEnabled()) {
            return;
        }

        Map<String, String> values = TeamSpeakCommand.parseEntry(line);
        String invokerName = values.getOrDefault("invokername", "?");
        TeamSpeakNotify.showToast(literal(invokerName), values.get("msg"));
    }
}
