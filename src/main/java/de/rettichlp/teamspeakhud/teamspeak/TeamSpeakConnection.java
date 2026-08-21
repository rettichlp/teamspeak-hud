package de.rettichlp.teamspeakhud.teamspeak;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.BiConsumer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Owns a single TeamSpeak ClientQuery socket: opening it, the blocking read loop, and closing. One instance per connection attempt;
 * {@link TeamSpeakClient} creates a fresh one for every {@code connect()}/reconnect rather than reusing these across attempts.
 */
@Getter
@RequiredArgsConstructor
public class TeamSpeakConnection {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 25639;

    private final Object writeLock = new Object();
    private final int generation;
    private final BiConsumer<String, Integer> lineHandler;

    private Socket socket;
    private PrintWriter output;
    private BufferedReader input;
    private volatile boolean closed;

    public void open() throws IOException {
        Socket newSocket = new Socket(HOST, PORT);
        this.output = new PrintWriter(new OutputStreamWriter(newSocket.getOutputStream(), UTF_8), true);
        this.input = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), UTF_8));
        this.socket = newSocket;
    }

    public void readLoop() {
        try {
            String line;
            while (!this.closed && (line = this.input.readLine()) != null) {
                this.lineHandler.accept(line, this.generation);
            }
        } catch (IOException e) {
            // socket closed by us or dropped by the peer; the caller reconnects
        }
    }

    public boolean write(String message) {
        synchronized (this.writeLock) {
            if (this.closed || this.output == null) {
                return false;
            }

            this.output.println(message);
            return !this.output.checkError();
        }
    }

    public void close() {
        this.closed = true;

        if (this.socket == null) {
            return;
        }

        try {
            this.socket.close();
        } catch (IOException e) {
            // nothing actionable; closing unblocks the read loop regardless
        }
    }
}
