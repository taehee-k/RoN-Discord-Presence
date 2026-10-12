package dev.ronpresence.ipc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ronpresence.Activity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class DiscordClientTest {
    private static final String ID = "123456789012345678";
    private static final Activity ACTIVITY = new Activity("Playing Classic", "Piglins", "", "");

    @Test void handshakeReadyActivityAcknowledgementAndPing() throws Exception {
        var pipe = new FakePipe();
        var now = new AtomicLong();
        try (var client = new DiscordClient(() -> pipe, s -> {}, now::get, false)) {
            client.update(ID, ACTIVITY);
            client.step();
            assertEquals(Frames.HANDSHAKE, pipe.opcode(0));
            assertEquals(ID, pipe.json(0).get("client_id").getAsString());
            client.step();
            assertEquals(1, pipe.sent.size(), "Must await READY before SET_ACTIVITY");
            pipe.reply(Frames.FRAME, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\"}");
            await(() -> { client.step(); return pipe.sent.size() == 2; });
            JsonObject command = pipe.json(1);
            assertEquals("SET_ACTIVITY", command.get("cmd").getAsString());
            assertEquals(ProcessHandle.current().pid(), command.getAsJsonObject("args").get("pid").getAsLong());
            assertEquals("Playing Classic", command.getAsJsonObject("args").getAsJsonObject("activity").get("details").getAsString());
            String nonce = command.get("nonce").getAsString();
            pipe.reply(Frames.FRAME, "{\"cmd\":\"SET_ACTIVITY\",\"nonce\":\"" + nonce + "\"}");
            pipe.reply(Frames.PING, "ping😀");
            await(() -> pipe.sent.size() == 3);
            assertEquals(Frames.PONG, pipe.opcode(2));
            assertEquals("ping😀", pipe.payload(2));
            now.set(TimeUnit.SECONDS.toNanos(10));
            client.step();
            assertEquals(3, pipe.sent.size(), "Unchanged activity is not resent");
            client.update(ID, new Activity("Spectating Classic", "Multiplayer", "", ""));
            client.step();
            assertEquals(4, pipe.sent.size());
            client.update("", null);
            client.step();
            assertTrue(pipe.closed, "Disabling must release Discord session");
        }
    }

    @Test void missingDiscordRetriesWithoutBusyLoopAndInvalidIdsNeverConnect() throws Exception {
        var attempts = new AtomicInteger();
        var now = new AtomicLong();
        var pipe = new FakePipe();
        try (var client = new DiscordClient(() -> {
            if (attempts.incrementAndGet() == 1) throw new IOException("not open");
            return pipe;
        }, s -> {}, now::get, false)) {
            client.update("not a token", ACTIVITY);
            client.step();
            assertEquals(0, attempts.get());
            client.update(ID, ACTIVITY);
            client.step(); client.step();
            assertEquals(1, attempts.get());
            now.set(TimeUnit.SECONDS.toNanos(15));
            client.step();
            assertEquals(2, attempts.get());
            assertEquals(1, pipe.sent.size());
        }
        assertTrue(pipe.closed);
    }

    @Test void stalledHandshakeAndDisconnectCanReconnect() throws Exception {
        var first = new FakePipe();
        var second = new FakePipe();
        var attempts = new AtomicInteger();
        var now = new AtomicLong();
        try (var client = new DiscordClient(() -> attempts.incrementAndGet() == 1 ? first : second,
                s -> {}, now::get, false)) {
            client.update(ID, ACTIVITY); client.step();
            now.set(TimeUnit.SECONDS.toNanos(11)); client.step();
            assertTrue(first.closed);
            now.set(TimeUnit.SECONDS.toNanos(26)); client.step();
            assertEquals(2, attempts.get());
            second.reply(Frames.FRAME, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\"}");
            await(() -> { client.step(); return second.sent.size() == 2; });
            second.reply(Frames.CLOSE, "{}");
            await(() -> second.closed);
        }
    }

    @Test void errorsAndUnacknowledgedCommandsCloseTheSession() throws Exception {
        var pipe = new FakePipe();
        var now = new AtomicLong();
        try (var client = new DiscordClient(() -> pipe, s -> {}, now::get, false)) {
            client.update(ID, ACTIVITY); client.step();
            pipe.reply(Frames.FRAME, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\"}");
            await(() -> { client.step(); return pipe.sent.size() == 2; });
            now.set(TimeUnit.SECONDS.toNanos(16)); client.step();
            assertTrue(pipe.closed);
        }
        var rejected = new FakePipe();
        try (var client = new DiscordClient(() -> rejected, s -> {}, now::get, false)) {
            client.update(ID, ACTIVITY); client.step();
            rejected.reply(Frames.FRAME, "{\"evt\":\"ERROR\",\"data\":{\"code\":4000}}");
            await(() -> rejected.closed);
        }
    }

    @Test void malformedFramesAreRejectedBeforeAllocatingPayload() throws Exception {
        for (int length : new int[]{-1, Frames.MAX_BYTES + 1}) {
            var pipe = new FakePipe();
            pipe.incoming.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(length).array());
            assertThrows(IOException.class, () -> Frames.read(pipe));
            pipe.close();
        }
        assertThrows(IOException.class, () -> Frames.encode(1, new byte[Frames.MAX_BYTES + 1]));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }

    private static final class FakePipe implements Transport {
        final PipedInputStream input = new PipedInputStream(8192);
        final PipedOutputStream incoming = new PipedOutputStream(input);
        final List<byte[]> sent = new CopyOnWriteArrayList<>();
        volatile boolean closed;
        FakePipe() throws IOException {}
        public void readFully(byte[] bytes) throws IOException { new DataInputStream(input).readFully(bytes); }
        public void write(byte[] bytes) throws IOException {
            if (closed) throw new IOException("closed");
            sent.add(bytes.clone());
        }
        public void close() throws IOException { closed = true; incoming.close(); input.close(); }
        void reply(int opcode, String payload) throws IOException {
            byte[] bytes = Frames.encode(opcode, payload.getBytes(StandardCharsets.UTF_8));
            // Fragment the header and payload to exercise partial reads.
            for (byte part : bytes) incoming.write(part);
            incoming.flush();
        }
        int opcode(int index) { return ByteBuffer.wrap(sent.get(index)).order(ByteOrder.LITTLE_ENDIAN).getInt(); }
        String payload(int index) { byte[] bytes = sent.get(index); return new String(bytes, 8, bytes.length - 8, StandardCharsets.UTF_8); }
        JsonObject json(int index) { return JsonParser.parseString(payload(index)).getAsJsonObject(); }
    }
}
