package de.rettichlp.teamspeakhud.teamspeak;

import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Periodically sends a {@code whoami} over an established {@link TeamSpeakClient} connection, both to keep the ClientQuery connection
 * alive and to detect a dead one. Schedules itself on the client's scheduler and manages its own lifecycle.
 */
@RequiredArgsConstructor
public class Heartbeat {

    private static final long HEARTBEAT_SECONDS = 5L;

    private final TeamSpeakClient client;

    private volatile ScheduledFuture<?> future;

    void start() {
        cancel();
        this.future = this.client.getScheduler().scheduleAtFixedRate(this::beat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, SECONDS);
    }

    void cancel() {
        Future<?> currentFuture = this.future;
        if (currentFuture != null) {
            currentFuture.cancel(false);
        }
    }

    private void beat() {
        if (this.client.isStopped() || !this.client.isConnected()) {
            return;
        }

        TeamSpeakConnection currentConnection = this.client.getConnection();
        if (currentConnection == null) {
            this.client.onConnectionLost(this.client.getGeneration().get());
            return;
        }

        Minecraft.getInstance().execute(() -> {
            if (this.client.isStopped() || currentConnection != this.client.getConnection()) {
                return; // superseded by a stop()/reconnect() since this beat was scheduled
            }

            this.client.refreshIdentity();
        });
    }
}
