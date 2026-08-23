package de.rettichlp.teamspeakhud.teamspeak.notify;

import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand;
import de.rettichlp.teamspeakhud.teamspeak.model.Client;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Set;

import static java.lang.Integer.parseInt;

/**
 * {@code notifytalkstatuschange}/{@code notifyclientupdated}: one member's status changed. Only the field(s) that actually changed are
 * present on the line, so every field below is guarded by its own {@code containsKey} rather than assuming the whole set is there -
 * unlike a full {@link de.rettichlp.teamspeakhud.teamspeak.command.ChannelClientListQuery} refresh, where every field is always
 * present.
 */
public record IncrementalUpdateNotify() implements TeamSpeakNotify {

    private static final Set<String> EVENT_NAMES = Set.of("notifytalkstatuschange", "notifyclientupdated");

    @Override
    public @NonNull Set<String> eventNames() {
        return EVENT_NAMES;
    }

    @Override
    public void handle(@NonNull String line, @NonNull TeamSpeakClient teamSpeakClient) {
        Map<String, String> values = TeamSpeakCommand.parseEntry(line);
        String clid = values.get("clid");
        if (clid == null) {
            return;
        }

        Client client = teamSpeakClient.getTeamSpeakChannel().getClients().get(parseInt(clid));
        if (client == null) {
            return;
        }

        applyValues(client, values);
    }

    private static void applyValues(Client client, @NonNull Map<String, String> values) {
        if (values.containsKey("client_nickname")) {
            client.setNickname(values.get("client_nickname"));
        }

        if (values.containsKey("client_flag_talking")) {
            client.setTalking("1".equals(values.get("client_flag_talking")));
        }

        // notifytalkstatuschange reports the talk state as "status" (1 = talking) instead of client_flag_talking.
        if (values.containsKey("status")) {
            client.setTalking("1".equals(values.get("status")));
        }

        if (values.containsKey("client_input_muted")) {
            client.setInputMuted("1".equals(values.get("client_input_muted")));
        }

        if (values.containsKey("client_output_muted")) {
            client.setOutputMuted("1".equals(values.get("client_output_muted")));
        }

        // client_input_hardware/client_output_hardware are 0 when no microphone/playback device is available at all, distinct from the
        // user muting themselves.
        if (values.containsKey("client_input_hardware")) {
            client.setInputHardwareDisabled("0".equals(values.get("client_input_hardware")));
        }

        if (values.containsKey("client_output_hardware")) {
            client.setOutputHardwareDisabled("0".equals(values.get("client_output_hardware")));
        }

        if (values.containsKey("client_away")) {
            client.setAway("1".equals(values.get("client_away")));
        }

        if (values.containsKey("client_is_muted")) {
            client.setLocallyMuted("1".equals(values.get("client_is_muted")));
        }

        if (values.containsKey("client_is_channel_commander")) {
            client.setChannelCommander("1".equals(values.get("client_is_channel_commander")));
        }
    }
}
