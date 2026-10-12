package dev.ronpresence.ipc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class Frames {
    static final int HANDSHAKE = 0, FRAME = 1, CLOSE = 2, PING = 3, PONG = 4;
    static final int MAX_BYTES = 64 * 1024;
    record Frame(int opcode, byte[] payload) {
        String text() { return new String(payload, StandardCharsets.UTF_8); }
    }
    static Frame read(Transport transport) throws IOException {
        byte[] header = new byte[8];
        transport.readFully(header);
        ByteBuffer data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = data.getInt(), length = data.getInt();
        if (opcode < 0 || opcode > 4 || length < 0 || length > MAX_BYTES) throw new IOException("Invalid Discord IPC frame");
        byte[] payload = new byte[length];
        transport.readFully(payload);
        return new Frame(opcode, payload);
    }
    static byte[] encode(int opcode, byte[] payload) throws IOException {
        if (payload.length > MAX_BYTES) throw new IOException("Discord IPC payload too large");
        return ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(opcode).putInt(payload.length).put(payload).array();
    }
}
