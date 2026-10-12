package dev.ronpresence.ipc;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Exercises actual Windows duplex named-pipe I/O, without touching Discord's pipes. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(10)
class WindowsPipeTest {
    interface CancellableKernel32 extends Kernel32 {
        boolean CancelIoEx(WinNT.HANDLE handle, Pointer overlapped);
    }
    @Test void canWriteWhileReaderIsBlocked() throws Exception {
        String address = "\\\\.\\pipe\\ron-presence-test-" + UUID.randomUUID();
        CancellableKernel32 api = Native.load("kernel32", CancellableKernel32.class, W32APIOptions.DEFAULT_OPTIONS);
        WinNT.HANDLE handle = api.CreateNamedPipe(address, 3, 0, 1, 4096, 4096, 0, null);
        assertNotEquals(WinBase.INVALID_HANDLE_VALUE, handle);
        ExecutorService executor = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable); thread.setDaemon(true); return thread;
        });
        try {
            Future<?> server = executor.submit(() -> {
                boolean connected = api.ConnectNamedPipe(handle, null);
                assertTrue(connected || api.GetLastError() == 535);
                byte[] request = new byte[4];
                var count = new IntByReference();
                assertTrue(api.ReadFile(handle, request, 4, count, null));
                assertEquals(4, count.getValue());
                assertEquals("ping", new String(request, StandardCharsets.UTF_8));
                assertTrue(api.WriteFile(handle, "pong".getBytes(StandardCharsets.UTF_8), 4, count, null));
            });
            Transport opened;
            try {
                opened = new Transport.WindowsPipe(address);
            } catch (IOException error) {
                // Restricted Windows runners can create a fixture pipe yet deny opening it.
                // Report an explicit skip, never a transport pass, for that environment.
                String message = error.getMessage();
                assumeFalse(message.contains("Windows error 5") || message.contains("Access is denied") || message.contains("액세스가 거부"),
                        "Environment denied access to the test named pipe: " + message);
                throw error;
            }
            try (Transport transport = opened) {
                var reading = new CountDownLatch(1);
                Future<String> response = executor.submit(() -> {
                    reading.countDown();
                    byte[] bytes = new byte[4]; transport.readFully(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                });
                assertTrue(reading.await(1, TimeUnit.SECONDS));
                // Ensure the reader is actually waiting on a silent server before writing.
                Thread.sleep(100);
                assertFalse(response.isDone());
                executor.submit(() -> { transport.write("ping".getBytes(StandardCharsets.UTF_8)); return null; })
                        .get(3, TimeUnit.SECONDS);
                assertEquals("pong", response.get(3, TimeUnit.SECONDS));
                server.get(3, TimeUnit.SECONDS);
                Future<?> idleRead = executor.submit(() -> {
                    assertThrows(IOException.class, () -> transport.readFully(new byte[1]));
                });
                Thread.sleep(50);
                transport.close();
                idleRead.get(3, TimeUnit.SECONDS);
            }
        } finally {
            api.CancelIoEx(handle, null);
            api.DisconnectNamedPipe(handle);
            api.CloseHandle(handle);
            executor.shutdownNow();
        }
    }
}
