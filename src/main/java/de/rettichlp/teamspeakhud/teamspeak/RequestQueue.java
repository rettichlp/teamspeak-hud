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

import static de.rettichlp.teamspeakhud.teamspeak.command.Response.failedToSend;
import static de.rettichlp.teamspeakhud.teamspeak.command.Response.parseResponse;

@RequiredArgsConstructor
public class RequestQueue {

    private final TeamSpeakClient client;

    private final Deque<QueuedRequest<?>> queue = new ArrayDeque<>();

    @Getter
    private volatile QueuedRequest<?> inFlight;

    public @NonNull <T> CompletableFuture<Response<T>> enqueue(@NonNull TeamSpeakCommand<T> command) {
        CompletableFuture<Response<T>> responseFuture = new CompletableFuture<>();

        if (this.client.getConnection() == null) {
            responseFuture.complete(failedToSend());
            return responseFuture;
        }

        synchronized (this.queue) {
            this.queue.addLast(new QueuedRequest<>(command, responseFuture));
        }

        promoteNext();
        return responseFuture;
    }

    public @Nullable Response<?> completeInFlight(@NonNull String errorLine) {
        QueuedRequest<?> current = this.inFlight;
        if (current == null) {
            return null;
        }

        this.inFlight = null;
        promoteNext();
        return current.complete(errorLine);
    }

    public void reset() {
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
            next.getResponseFuture().complete(failedToSend());
            promoteNext();
        }
    }

    @Getter
    @RequiredArgsConstructor
    static final class QueuedRequest<T> {

        private final TeamSpeakCommand<T> command;
        private final CompletableFuture<Response<T>> responseFuture;

        private T data;

        public void enrichWithData(@NonNull String dataLine) {
            this.data = this.command.parseResponse(dataLine);
        }

        private @NonNull Response<T> complete(@NonNull String errorLine) {
            Response<T> response = parseResponse(errorLine, this.data);
            this.responseFuture.complete(response);
            return response;
        }
    }
}
