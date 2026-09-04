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

@RequiredArgsConstructor
public class RequestQueue {

    private final TeamSpeakClient client;

    private final Deque<QueuedRequest<?>> queue = new ArrayDeque<>();

    private volatile QueuedRequest<?> inFlight;

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

    void onDataLine(@NonNull String dataLine) {
        QueuedRequest<?> current = this.inFlight;
        if (current != null) {
            current.onDataLine(dataLine);
        }
    }

    @Nullable Response<?> completeInFlight(@NonNull String errorLine) {
        QueuedRequest<?> current = this.inFlight;
        if (current == null) {
            return null;
        }

        advanceQueue();
        return current.complete(errorLine);
    }

    void reset() {
        synchronized (this.queue) {
            this.inFlight = null;
            this.queue.clear();
        }
    }

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

    @Getter
    @RequiredArgsConstructor
    private static final class QueuedRequest<T> {

        private final TeamSpeakCommand<T> command;
        private final CompletableFuture<Response<T>> responseFuture;

        private T data;

        private void onDataLine(@NonNull String dataLine) {
            this.data = this.command.parseResponse(dataLine);
        }

        private @NonNull Response<T> complete(@NonNull String errorLine) {
            Response<T> response = this.command.buildResponse(errorLine, this.data);
            this.responseFuture.complete(response);
            return response;
        }
    }
}
