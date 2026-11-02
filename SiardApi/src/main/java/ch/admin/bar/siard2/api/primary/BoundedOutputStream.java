// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.primary;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.*;

/** Single producer / ordered writer. Only owned byte arrays cross the thread boundary.
 * At most ten 64 KiB blocks (producer, consumer and eight queued) are retained.
 * Close always joins the writer before its owner can close the ZIP or delete the spool.
 */
final class BoundedOutputStream extends OutputStream {
    private static final int BLOCK = 65536;
    private final OutputStream output;
    private final ArrayBlockingQueue<Item> queue = new ArrayBlockingQueue<>(8);
    private final byte[] buffer = new byte[BLOCK];
    private int used;
    private Thread worker;
    private volatile IOException failure;
    private boolean closed;
    private static final Item END = new Item(null, null);
    private static final class Item {
        final byte[] bytes;
        final CountDownLatch barrier;
        Item(byte[] bytes, CountDownLatch barrier) { this.bytes = bytes; this.barrier = barrier; }
    }

    BoundedOutputStream(OutputStream output) { this.output = output; }
    private void check() throws IOException {
        if (failure != null) throw reportedFailure();
        if (closed) throw new IOException("Output is closed");
    }
    private void start() {
        if (worker != null) return;
        worker = new Thread(() -> {
            try {
                for (;;) {
                    Item item = queue.take();
                    if (item == END) break;
                    if (item.bytes != null) output.write(item.bytes);
                    else { output.flush(); item.barrier.countDown(); }
                }
            } catch (Throwable error) { failed(error); }
            finally {
                try { output.close(); } catch (Throwable error) { failed(error); }
            }
        }, "siard-xml-output");
        worker.setDaemon(true);
        worker.start();
    }
    private void failed(Throwable error) {
        IOException e = error instanceof IOException ? (IOException)error : new IOException("Output worker failed", error);
        if (failure == null) failure = e; else if (failure != e) failure.addSuppressed(e);
    }
    private IOException reportedFailure() {
        // A write and the try-with-resources close must not throw the same
        // instance: Java would replace it with a self-suppression exception.
        return new IOException(failure.getMessage(), failure);
    }
    private void submit(Item item) throws IOException {
        start();
        try {
            while (!queue.offer(item, 100, TimeUnit.MILLISECONDS)) check();
            check();
        } catch (InterruptedException e) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while queuing output");
        }
    }
    private void emit() throws IOException {
        if (used == 0) return;
        submit(new Item(Arrays.copyOf(buffer, used), null));
        used = 0;
    }
    @Override public void write(int value) throws IOException {
        check(); buffer[used++] = (byte)value; if (used == BLOCK) emit();
    }
    @Override public void write(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        check();
        while (length > 0) {
            int n = Math.min(length, BLOCK - used);
            System.arraycopy(bytes, offset, buffer, used, n);
            used += n; offset += n; length -= n;
            if (used == BLOCK) emit();
        }
    }
    @Override public void flush() throws IOException {
        check();
        if (worker == null) {
            output.write(buffer, 0, used); used = 0; output.flush(); return;
        }
        emit();
        CountDownLatch barrier = new CountDownLatch(1);
        submit(new Item(null, barrier));
        try { while (!barrier.await(100, TimeUnit.MILLISECONDS)) check(); }
        catch (InterruptedException e) {
            worker.interrupt(); Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while flushing output");
        }
        check();
    }
    @Override public void close() throws IOException {
        if (closed) { if (failure != null) throw reportedFailure(); return; }
        IOException pending = null;
        try {
            if (worker == null) {
                try (OutputStream owned = output) { owned.write(buffer, 0, used); }
            } else { emit(); submit(END); }
        } catch (IOException e) { pending = e; if (worker != null) worker.interrupt(); }
        finally {
            closed = true;
            boolean interrupted = Thread.interrupted();
            if (worker != null) {
                for (;;) {
                    try { worker.join(); break; }
                    catch (InterruptedException e) { interrupted = true; worker.interrupt(); }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
                if (pending == null) pending = new InterruptedIOException("Interrupted while closing output");
            }
        }
        if (pending != null) {
            if (failure != null && pending != failure) pending.addSuppressed(failure);
            throw pending;
        }
        if (failure != null) throw reportedFailure();
    }
}
