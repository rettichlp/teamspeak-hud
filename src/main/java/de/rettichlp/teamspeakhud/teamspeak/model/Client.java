package de.rettichlp.teamspeakhud.teamspeak.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Client {

    /**
     * The unique ID of the client.
     */
    private int clientId;

    /**
     * The nickname of the client, which is displayed in the TeamSpeak channel.
     */
    private String nickname;

    /**
     * Indicates whether the client is actively talking (as detected by the TeamSpeak server).
     */
    private boolean talking;

    /**
     * Microphone muted by the user's own choice (they can unmute themselves).
     */
    private boolean inputMuted;

    /**
     * Sound/headphones muted by the user's own choice (they can un-deafen themselves).
     */
    private boolean outputMuted;

    /**
     * No microphone available at all (disabled/not connected in TeamSpeak).
     */
    private boolean inputHardwareDisabled;

    /**
     * No playback device available at all (disabled/not connected in TeamSpeak).
     */
    private boolean outputHardwareDisabled;

    /**
     * Indicates whether the user is marked as away in the TeamSpeak client.
     */
    private boolean away;

    /**
     * Muted locally by the local user via TeamSpeak's own "mute client" feature.
     */
    private boolean locallyMuted;

    /**
     * Whether the user has "channel commander" mode enabled (a TeamSpeak feature that makes their talk status more prominent).
     */
    private boolean channelCommander;
}
