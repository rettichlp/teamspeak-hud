package de.rettichlp.teamspeakhud.teamspeak.notify;

import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static net.minecraft.network.chat.Component.literal;

/**
 * One ClientQuery notify event: the event name(s) it's registered for via {@code clientnotifyregister}, and how it reacts once such a
 * line arrives unsolicited (i.e. not as the response to a request we sent - see
 * {@link de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand} for those instead).
 */
public sealed interface TeamSpeakNotify permits MembershipChangedNotify, IncrementalUpdateNotify, ClientPokeNotify, TextMessageNotify {

    /**
     * The ClientQuery event name(s) this reacts to; each is registered separately via {@code clientnotifyregister}.
     */
    @NonNull Set<String> eventNames();

    /**
     * Reacts to {@code line}, one of {@link #eventNames()} having already been confirmed to prefix it (see {@link #matches}).
     */
    void handle(@NonNull String line, @NonNull TeamSpeakClient client);

    /**
     * Whether {@code line} is this notify event, i.e. one of {@link #eventNames()} is a prefix of it.
     */
    default boolean matches(@NonNull String line) {
        return eventNames().stream().anyMatch(line::startsWith);
    }

    /**
     * Writes {@link #eventNames()} to {@code connection}.
     */
    default void register(@NonNull TeamSpeakConnection connection) {
        eventNames().forEach(eventName -> connection.write("clientnotifyregister schandlerid=0 event=" + eventName));
    }

    static void showToast(@NonNull Component title, @Nullable String message) {
        Component messageComponent = message == null || message.isEmpty() ? null : literal(message);
        SystemToast.add(Minecraft.getInstance().gui.toastManager(), new SystemToast.SystemToastId(), title, messageComponent);
    }
}
