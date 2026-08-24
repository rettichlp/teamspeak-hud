package de.rettichlp.teamspeakhud.teamspeak.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Client {

    /**
     * How long a newly joined or left member is highlighted in the HUD.
     */
    public static final long TRANSITION_HIGHLIGHT_DURATION_MILLIS = 5_000L;

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

    /**
     * When this member joined the channel ({@link System#currentTimeMillis()}), or {@code 0} if the join was never tracked (e.g.
     * present at an initial channel load).
     */
    private long joinedAt;

    /**
     * When this member left the channel ({@link System#currentTimeMillis()}), or {@code 0} while they're still in it.
     */
    private long leftAt;

    /**
     * Whether this member joined recently enough to still be highlighted in the HUD.
     */
    public boolean hasJoinHighlight() {
        return this.joinedAt != 0 && System.currentTimeMillis() - this.joinedAt < TRANSITION_HIGHLIGHT_DURATION_MILLIS;
    }

    /**
     * Whether this member has left the channel and is currently only being kept around to be highlighted in the HUD.
     */
    public boolean hasLeavingHighlight() {
        return this.leftAt != 0;
    }
}
