package de.rettichlp.teamspeakhud.teamspeak;

import de.rettichlp.teamspeakhud.teamspeak.command.Response;
import de.rettichlp.teamspeakhud.teamspeak.command.TeamSpeakCommand;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

/**
 * Serializes the ClientQuery commands {@code client} sends: ClientQuery answers exactly one command at a time and carries no request
 * ids to correlate a response back to whichever command it belongs to, so at most one request may ever be written to the wire before
 * its response has been fully read.
 */
@RequiredArgsConstructor
public class RequestQueue {

    private final TeamSpeakClient client;

    private final Deque<QueuedRequest<?>> queue = new ArrayDeque<>();

    /**
     * The request whose response we're currently waiting on, or {@code null} if none is in flight.
     */
    private volatile QueuedRequest<?> inFlight;

    /**
     * Writes {@code command} to the wire if nothing else is currently in flight, or appends it to the queue to be sent once the
     * requests ahead of it have been answered.
     */
    public @NonNull <T> CompletableFuture<Response<T>> enqueue(@NonNull TeamSpeakCommand<T> command) {
        CompletableFuture<Response<T>> responseFuture = new CompletableFuture<>();

        if (this.client.getConnection() == null) {
            responseFuture.complete(Response.failedToSend());
            return responseFuture;
        }

        synchronized (this.queue) {
            this.queue.addLast(new QueuedRequest<>(command, responseFuture));
        }

        promoteNext();
        return responseFuture;
    }

    /**
     * Hands {@code dataLine} to whichever request is currently in flight.
     */
    void onDataLine(@NonNull String dataLine) {
        QueuedRequest<?> current = this.inFlight;
        if (current != null) {
            current.onDataLine(dataLine);
        }
    }

    /**
     * Reacts to the trailing {@code error id=...} acknowledgement line for whichever request is currently in flight.
     *
     * @return the response of the in-flight request
     */
    @Nullable Response<?> completeInFlight(@NonNull String errorLine) {
        QueuedRequest<?> current = this.inFlight;
        if (current == null) {
            return null;
        }

        advanceQueue();
        return current.complete(errorLine);
    }

    /**
     * Clears whatever is in flight and drops everything queued.
     */
    void reset() {
        synchronized (this.queue) {
            this.inFlight = null;
            this.queue.clear();
        }
    }

    /**
     * If nothing is currently in flight, pops the next queued request (if any) and writes it. If writing fails, that request is
     * treated as failed immediately and the next one is tried instead.
     */
    private void promoteNext() {
        QueuedRequest<?> next;
        synchronized (this.queue) {
            if (this.inFlight != null) {
                return;
            }

            next = this.queue.pollFirst();
            this.inFlight = next;
        }

        if (next == null) {
            return;
        }

        TeamSpeakConnection currentConnection = this.client.getConnection();
        boolean written = currentConnection != null && currentConnection.write(next.getCommand().commandLine());
        if (!written) {
            this.inFlight = null;
            next.getResponseFuture().complete(Response.failedToSend());
            promoteNext();
        }
    }

    private void advanceQueue() {
        this.inFlight = null;
        promoteNext();
    }

    /**
     * One request waiting for (or currently awaiting) its ClientQuery response, as tracked by {@link #queue}/{@link #inFlight}.
     */
    @Getter
    @RequiredArgsConstructor
    private static final class QueuedRequest<T> {

        private final TeamSpeakCommand<T> command;
        private final CompletableFuture<Response<T>> responseFuture;

        private T data;

        /**
         * Hands {@code dataLine} to the command to parse and stashes the result for {@link #complete}.
         */
        private void onDataLine(@NonNull String dataLine) {
            this.data = this.command.parseResponse(dataLine);
        }

        /**
         * Folds {@code errorLine} together with whatever data was previously stashed via {@link #onDataLine} into this request's
         * final {@link Response}, completes its future with it, and returns it.
         */
        private @NonNull Response<T> complete(@NonNull String errorLine) {
            Response<T> response = this.command.buildResponse(errorLine, this.data);
            this.responseFuture.complete(response);
            return response;
        }
    }
}
