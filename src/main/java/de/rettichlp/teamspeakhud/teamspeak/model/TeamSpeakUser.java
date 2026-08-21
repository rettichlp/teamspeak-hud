package de.rettichlp.teamspeakhud.teamspeak.model;

import lombok.Data;

@Data
public class TeamSpeakUser {

    private final int clientId;

    private String nickname = "";
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

    private boolean away;

    /**
     * Whether the user has "channel commander" mode enabled (a TeamSpeak feature that makes their talk status more prominent).
     */
    private boolean channelCommander;
}
