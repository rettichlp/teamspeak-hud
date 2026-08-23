package de.rettichlp.teamspeakhud.teamspeak.notify;

import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Set;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.configuration;
import static java.lang.Integer.parseInt;
import static net.minecraft.network.chat.Component.literal;
import static net.minecraft.network.chat.Component.translatable;

/**
 * {@code notifytextmessage}: a chat message was sent to us (private) or to our current channel.
 */
public record TextMessageNotify() implements TeamSpeakNotify {

    private static final Set<String> EVENT_NAMES = Set.of("notifytextmessage");

    @Override
    public @NonNull Set<String> eventNames() {
        return EVENT_NAMES;
    }

    @Override
    public void handle(@NonNull String line, @NonNull TeamSpeakClient client) {
        Map<String, String> values = TeamSpeakCommand.parseEntry(line);

        // don't toast our own messages being echoed back to us
        String invokerId = values.get("invokerid");
        if (invokerId != null && parseInt(invokerId) == client.getOwnClientId()) {
            return;
        }

        boolean isChannelMessage = "2".equals(values.get("targetmode"));
        if (isChannelMessage ? !configuration.isChannelMessageNotificationsEnabled() : !configuration.isPrivateMessageNotificationsEnabled()) {
            return;
        }

        String invokerName = values.getOrDefault("invokername", "?");
        Component title = isChannelMessage
                ? translatable("tsh.notification.message.channel.title", invokerName, client.getChannel().getName())
                : literal(invokerName);

        TeamSpeakNotify.showToast(title, values.get("msg"));
    }
}
