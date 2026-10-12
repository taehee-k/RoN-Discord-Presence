package dev.ronpresence.ipc;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;

interface Transport extends Closeable {
    void readFully(byte[] bytes) throws IOException;
    void write(byte[] bytes) throws IOException;

    static Transport connect() throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        var roots = new LinkedHashSet<String>();
        if (windows) roots.add("\\\\.\\pipe");
        else {
            for (String variable : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
                String value = System.getenv(variable);
                if (value != null && !value.isBlank()) roots.add(value);
            }
            roots.add("/tmp");
        }
        IOException last = null;
        for (String root : roots) {
            for (int index = 0; index < 10; index++) {
                String address = root + (windows ? "\\" : "/") + "discord-ipc-" + index;
                try {
                    return windows ? new WindowsPipe(address) : new UnixSocket(Path.of(address));
                } catch (IOException error) { last = error; }
            }
        }
        throw new IOException("Discord desktop IPC is unavailable", last);
    }

    final class WindowsPipe implements Transport {
        private final Kernel32 api = Kernel32.INSTANCE;
        private final WinNT.HANDLE handle;
        private boolean closed;

        WindowsPipe(String address) throws IOException {
            handle = api.CreateFile(address, WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                    0, null, WinNT.OPEN_EXISTING, 0, null);
            if (WinBase.INVALID_HANDLE_VALUE.equals(handle)) throw failure("open");
        }

        // Never leave a synchronous Windows read pending while waiting for Discord.
        // Only this reader consumes bytes, so bytes observed by Peek remain available.
        public void readFully(byte[] bytes) throws IOException {
            int offset = 0;
            while (offset < bytes.length) {
                synchronized (this) {
                    if (closed) throw new EOFException("Discord pipe closed");
                    var available = new IntByReference();
                    if (!api.PeekNamedPipe(handle, null, 0, null, available, null)) throw failure("peek");
                    if (available.getValue() > 0) {
                        byte[] chunk = new byte[Math.min(bytes.length - offset, available.getValue())];
                        var count = new IntByReference();
                        if (!api.ReadFile(handle, chunk, chunk.length, count, null)) throw failure("read");
                        if (count.getValue() == 0) throw new EOFException("Discord pipe closed");
                        System.arraycopy(chunk, 0, bytes, offset, count.getValue());
                        offset += count.getValue();
                        continue;
                    }
                }
                try { Thread.sleep(10); }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Discord pipe reader interrupted", error);
                }
            }
        }
        public synchronized void write(byte[] bytes) throws IOException {
            if (closed) throw new EOFException("Discord pipe closed");
            var count = new IntByReference();
            if (!api.WriteFile(handle, bytes, bytes.length, count, null)) throw failure("write");
            if (count.getValue() != bytes.length) throw new IOException("Incomplete Discord pipe write");
        }
        public synchronized void close() {
            if (!closed) { closed = true; api.CloseHandle(handle); }
        }
        private IOException failure(String operation) {
            return new IOException("Discord pipe " + operation + " failed (Windows error " + api.GetLastError() + ")");
        }
    }

    final class UnixSocket implements Transport {
        private final SocketChannel socket;
        UnixSocket(Path path) throws IOException {
            socket = SocketChannel.open(StandardProtocolFamily.UNIX);
            try { socket.connect(UnixDomainSocketAddress.of(path)); }
            catch (IOException | RuntimeException error) { socket.close(); throw error; }
        }
        public void readFully(byte[] bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) if (socket.read(buffer) < 0) throw new EOFException();
        }
        public void write(byte[] bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) socket.write(buffer);
        }
        public void close() throws IOException { socket.close(); }
    }
}
