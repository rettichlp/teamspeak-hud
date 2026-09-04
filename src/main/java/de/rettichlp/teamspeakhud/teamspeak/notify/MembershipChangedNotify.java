package de.rettichlp.teamspeakhud.teamspeak.notify;

import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * {@code notifycliententerview}/{@code notifyclientleftview}/{@code notifyclientmoved}: who's in the channel changed.
 */
public record MembershipChangedNotify() implements TeamSpeakNotify {

    private static final Set<String> EVENT_NAMES = Set.of("notifycliententerview", "notifyclientleftview", "notifyclientmoved");

    @Override
    public @NonNull Set<String> eventNames() {
        return EVENT_NAMES;
    }

    @Override
    public void handle(@NonNull String line, @NonNull TeamSpeakClient client) {
        client.refreshIdentity();
    }
}
