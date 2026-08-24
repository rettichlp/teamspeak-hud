package de.rettichlp.teamspeakhud.teamspeak;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Schedules a single reconnection attempt after the connection is lost, debouncing so at most one attempt is ever pending at a time.
 * Schedules itself on the client's scheduler and manages its own lifecycle.
 */
@RequiredArgsConstructor
public class Reconnector {

    public static final long RECONNECT_SECONDS = 10L;

    private final TeamSpeakClient client;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> future;

    void schedule(int lostGeneration) {
        if (this.scheduled.compareAndSet(false, true)) {
            this.future = this.client.getScheduler().schedule(() -> reconnect(lostGeneration), RECONNECT_SECONDS, SECONDS);
        }
    }

    void cancel() {
        this.scheduled.set(false);

        ScheduledFuture<?> currentFuture = this.future;
        if (currentFuture != null) {
            currentFuture.cancel(false);
        }
    }

    private void reconnect(int reconnectGeneration) {
        this.scheduled.set(false);
        if (this.client.isStopped() || reconnectGeneration != this.client.getGeneration()) {
            return;
        }

        this.client.connectAsync(reconnectGeneration);
    }
}
