// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.primary;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedOutputStreamTest {
    @Test void copiesBuffersPreservesOrderAndFlushesBeforeReturning() throws Exception {
        ByteArrayOutputStream actual = new ByteArrayOutputStream(), expected = new ByteArrayOutputStream();
        try (BoundedOutputStream out = new BoundedOutputStream(actual)) {
            Random random = new Random(42);
            byte[] bytes = new byte[70001];
            for (int i = 0; i < 100; i++) {
                random.nextBytes(bytes); expected.write(bytes); out.write(bytes);
                Arrays.fill(bytes, (byte)0); // caller's buffer can be immediately reused
            }
            out.flush();
            assertArrayEquals(expected.toByteArray(), actual.toByteArray());
            out.write(7); expected.write(7);
        }
        assertArrayEquals(expected.toByteArray(), actual.toByteArray());
    }

    @Test void writerFailureUnblocksFullQueueAndClosesUnderlyingStream() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            boolean[] closed = {false};
            OutputStream failed = new OutputStream() {
                public void write(int b) throws IOException { throw new IOException("disk full"); }
                public void close() { closed[0] = true; }
            };
            BoundedOutputStream out = new BoundedOutputStream(failed);
            IOException e = assertThrows(IOException.class, () -> out.write(new byte[4 * 1024 * 1024]));
            assertEquals("disk full", e.getMessage());
            assertThrows(IOException.class, out::close);
            assertTrue(closed[0]);
        });
    }

    @Test void tryWithResourcesPreservesWriterFailureWithoutSelfSuppression() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            IOException error = assertThrows(IOException.class, () -> {
                try (BoundedOutputStream out = new BoundedOutputStream(new OutputStream() {
                    public void write(int b) throws IOException { throw new IOException("disk failure"); }
                })) { out.write(new byte[4 * 1024 * 1024]); }
            });
            assertEquals("disk failure", error.getMessage());
            assertEquals(1, error.getSuppressed().length);
        });
    }

    @Test void interruptedProducerJoinsWriterAndReleasesDestination() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            CountDownLatch started = new CountDownLatch(1), closed = new CountDownLatch(1);
            OutputStream stalled = new OutputStream() {
                public void write(int b) throws IOException {
                    started.countDown();
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException e) { throw new InterruptedIOException("writer interrupted"); }
                }
                public void close() { closed.countDown(); }
            };
            boolean[] interrupted = {false};
            Thread producer = new Thread(() -> {
                try (BoundedOutputStream out = new BoundedOutputStream(stalled)) { out.write(new byte[4 * 1024 * 1024]); }
                catch (IOException expected) { interrupted[0] = Thread.currentThread().isInterrupted(); }
            });
            producer.start(); assertTrue(started.await(3, TimeUnit.SECONDS)); producer.interrupt();
            producer.join(3000);
            assertFalse(producer.isAlive()); assertTrue(interrupted[0]); assertEquals(0, closed.getCount());
        });
    }
}
