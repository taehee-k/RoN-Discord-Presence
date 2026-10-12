package dev.ronpresence.ipc;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ronpresence.Activity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Local Discord IPC only. All reads/writes occur on daemon workers, never the game thread. */
public final class DiscordClient implements AutoCloseable {
    @FunctionalInterface interface Connector { Transport connect() throws IOException; }
    private record Request(String appId, Activity activity) {}
    private final Connector connector;
    private final Consumer<String> log;
    private final LongSupplier clock;
    private final ScheduledExecutorService worker;
    private volatile Request desired = new Request("", null);
    private volatile Connection active;
    private volatile boolean closed;
    private long nextConnect, nextSend;
    private boolean announcedUnavailable;

    public DiscordClient(Consumer<String> log) {
        this(Transport::connect, log, System::nanoTime, true);
    }

    DiscordClient(Connector connector, Consumer<String> log, LongSupplier clock, boolean schedule) {
        this.connector = connector;
        this.log = log;
        this.clock = clock;
        worker = Executors.newSingleThreadScheduledExecutor(action -> {
            Thread thread = new Thread(action, "ron-discord-worker");
            thread.setDaemon(true);
            return thread;
        });
        log.accept("[RoN RPC] Worker created; monotonicTime=" + clock.getAsLong());
        if (schedule) worker.scheduleWithFixedDelay(this::step, 0, 1, TimeUnit.SECONDS);
    }

    public static boolean validApplicationId(String id) { return id != null && id.matches("[0-9]{17,20}"); }

    public void update(String appId, Activity activity) {
        Request request = new Request(validApplicationId(appId) ? appId : "", activity);
        if (!request.equals(desired)) log.accept("[RoN RPC] Activity queued; validId=" + validApplicationId(appId)
                + ", activityPresent=" + (activity != null));
        desired = request;
    }

    void step() {
        if (closed) return;
        long now = clock.getAsLong();
        Request request = desired;
        Connection connection = active;
        try {
            if (connection != null && (!connection.appId.equals(request.appId) || request.activity == null)) {
                // Closing the IPC session clears its activity, including when disabled or logging out.
                connection.close();
                active = connection = null;
            }
            if (request.appId.isBlank() || request.activity == null) return;
            if (connection != null && (connection.dead ||
                    (!connection.ready && now - connection.openedAt > TimeUnit.SECONDS.toNanos(10)) ||
                    (connection.pending != null && now - connection.sentAt > TimeUnit.SECONDS.toNanos(15)))) {
                log.accept("[RoN RPC] Reconnecting: disconnected=" + connection.dead
                        + ", ready=" + connection.ready + ", awaitingAcknowledgement=" + (connection.pending != null));
                connection.close();
                active = connection = null;
                nextConnect = now + TimeUnit.SECONDS.toNanos(15);
            }
            if (connection == null && now >= nextConnect) {
                nextConnect = now + TimeUnit.SECONDS.toNanos(15);
                log.accept("[RoN RPC] Opening Discord IPC pipe.");
                connection = new Connection(request.appId, connector.connect(), now);
                log.accept("[RoN RPC] IPC pipe opened.");
                active = connection;
                if (closed) { connection.close(); return; }
                connection.start();
                nextSend = 0;
            }
            if (connection != null && connection.ready && connection.pending == null &&
                    now >= nextSend && !Objects.equals(connection.lastSent, request.activity)) {
                connection.sendActivity(request.activity, now);
                nextSend = now + TimeUnit.SECONDS.toNanos(5);
                announcedUnavailable = false;
            }
        } catch (IOException | RuntimeException error) {
            if (connection != null) connection.close();
            active = null;
            nextConnect = now + TimeUnit.SECONDS.toNanos(15);
            if (!announcedUnavailable) {
                log.accept("Discord presence unavailable; retrying in the background: " + error.getMessage()
                        + (error.getCause() == null ? "" : " / " + error.getCause().getMessage()));
                announcedUnavailable = true;
            }
        }
    }

    @Override public void close() {
        closed = true;
        worker.shutdownNow();
        Connection connection = active;
        if (connection != null) connection.close();
    }

    private final class Connection {
        final String appId;
        final Transport transport;
        final long openedAt;
        volatile boolean ready, dead;
        volatile String pending;
        volatile long sentAt;
        Activity lastSent;

        Connection(String appId, Transport transport, long now) {
            this.appId = appId; this.transport = transport; openedAt = now;
        }

        void start() throws IOException {
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", appId);
            log.accept("[RoN RPC] Sending HANDSHAKE.");
            send(Frames.HANDSHAKE, handshake.toString().getBytes(StandardCharsets.UTF_8));
            log.accept("[RoN RPC] HANDSHAKE written; waiting for READY.");
            Thread reader = new Thread(this::readLoop, "ron-discord-reader");
            reader.setDaemon(true);
            reader.start();
        }

        synchronized void send(int opcode, byte[] payload) throws IOException {
            transport.write(Frames.encode(opcode, payload));
        }

        void sendActivity(Activity activity, long now) throws IOException {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", activity == null ? JsonNull.INSTANCE : activity.json());
            JsonObject command = new JsonObject();
            command.addProperty("cmd", "SET_ACTIVITY");
            command.add("args", args);
            String nonce = UUID.randomUUID().toString();
            command.addProperty("nonce", nonce);
            sentAt = now;
            pending = nonce;
            log.accept("[RoN RPC] Sending SET_ACTIVITY.");
            send(Frames.FRAME, command.toString().getBytes(StandardCharsets.UTF_8));
            log.accept("[RoN RPC] SET_ACTIVITY written; waiting for acknowledgement.");
            lastSent = activity;
        }

        void readLoop() {
            try {
                while (!closed && !dead) {
                    Frames.Frame frame = Frames.read(transport);
                    if (frame.opcode() == Frames.CLOSE) {
                        log.accept("[RoN RPC] Discord sent CLOSE.");
                        break;
                    }
                    if (frame.opcode() == Frames.PING) { send(Frames.PONG, frame.payload()); continue; }
                    if (frame.opcode() != Frames.FRAME) continue;
                    JsonObject reply = JsonParser.parseString(frame.text()).getAsJsonObject();
                    String event = string(reply, "evt");
                    if ("ERROR".equals(event)) {
                        // Avoid logging Discord profile data or an entire response.
                        JsonObject data = reply.has("data") && reply.get("data").isJsonObject()
                                ? reply.getAsJsonObject("data") : new JsonObject();
                        log.accept("Discord rejected the presence request: code=" + string(data, "code")
                                + ", message=" + string(data, "message"));
                        break;
                    }
                    if ("READY".equals(event) && "DISPATCH".equals(string(reply, "cmd"))) {
                        ready = true;
                        log.accept("Discord RPC connected (READY).");
                    }
                    String nonce = string(reply, "nonce");
                    if (nonce != null && nonce.equals(pending)) {
                        pending = null;
                        if (!acknowledged) {
                            acknowledged = true;
                            log.accept("Discord accepted the Rich Presence activity (SET_ACTIVITY).");
                        }
                    }
                }
            } catch (IOException | RuntimeException error) {
                if (!closed && !dead) log.accept("Discord RPC reader disconnected: " + error.getClass().getSimpleName());
                // A closed/restarted Discord client is normal. The worker reconnects.
            } finally { close(); }
        }

        void close() {
            dead = true;
            try { transport.close(); } catch (IOException ignored) {}
        }
        private boolean acknowledged;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }
}
