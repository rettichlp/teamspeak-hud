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

public sealed interface TeamSpeakNotify permits MembershipChangedNotify, IncrementalUpdateNotify, ClientPokeNotify, TextMessageNotify {

    @NonNull Set<String> eventNames();

    void handle(@NonNull String line, @NonNull TeamSpeakClient client);

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
