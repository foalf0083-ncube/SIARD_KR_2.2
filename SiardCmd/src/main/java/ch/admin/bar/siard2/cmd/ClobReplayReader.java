// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Replay only the MIME detector's reads; spill unusually large prefixes to disk. */
final class ClobReplayReader extends Reader {
    private static final int MEMORY_CHARS = 64 * 1024;
    private final Reader source;
    private char[] prefix;
    private int count, position;
    boolean capturing;
    private boolean closed;
    private Path spillPath;
    private RandomAccessFile spill;
    private byte[] bytes;
    private long spillRemaining;

    ClobReplayReader(Reader source, boolean capturing) {
        this.source = source;
        this.capturing = capturing;
        if (capturing) prefix = new char[1024];
    }

    void replayPrefix() throws IOException {
        capturing = false;
        if (spill != null) {
            spillRemaining = spill.length() / 2;
            spill.seek(0);
        }
    }

    @Override public int read(char[] chars, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, chars.length);
        if (closed) throw new IOException("Reader is closed");
        if (length == 0) return 0;
        if (!capturing && spill != null) {
            int n = (int)Math.min(Math.min((long)length, spillRemaining), bytes.length / 2);
            spill.readFully(bytes, 0, n * 2);
            for (int i=0; i<n; i++) chars[offset+i] = (char)((bytes[2*i] & 255) << 8 | bytes[2*i+1] & 255);
            spillRemaining -= n;
            if (spillRemaining == 0) releaseSpill();
            return n;
        }
        if (!capturing && position < count) {
            int n = Math.min(length, count - position);
            System.arraycopy(prefix, position, chars, offset, n);
            position += n;
            if (position == count) prefix = null;
            return n;
        }
        int n = source.read(chars, offset, length);
        if (capturing && n > 0) capture(chars, offset, n);
        return n;
    }

    private void capture(char[] chars, int offset, int length) throws IOException {
        if (spill == null && (long)count + length <= MEMORY_CHARS) {
            if (count + length > prefix.length)
                prefix = Arrays.copyOf(prefix, Math.min(MEMORY_CHARS, Math.max(count + length, prefix.length * 2)));
            System.arraycopy(chars, offset, prefix, count, length);
            count += length;
            return;
        }
        if (spill == null) {
            spillPath = Files.createTempFile("siard_clob_prefix_", ".tmp");
            spill = new RandomAccessFile(spillPath.toFile(), "rw");
            bytes = new byte[16 * 1024];
            writeChars(prefix, 0, count);
            count = position = 0;
            prefix = null;
        }
        writeChars(chars, offset, length);
    }

    private void writeChars(char[] chars, int offset, int length) throws IOException {
        // Store raw UTF-16 code units, including unmatched surrogates. A charset
        // encoder would silently replace those and corrupt the replayed value.
        while (length > 0) {
            int n = Math.min(length, bytes.length / 2);
            for (int i=0; i<n; i++) {
                char c = chars[offset+i];
                bytes[2*i] = (byte)(c >>> 8);
                bytes[2*i+1] = (byte)c;
            }
            spill.write(bytes, 0, n * 2);
            offset += n;
            length -= n;
        }
    }

    private void releaseSpill() throws IOException {
        IOException failure = null;
        try { if (spill != null) spill.close(); }
        catch (IOException e) { failure = e; }
        spill = null;
        bytes = null;
        try { if (spillPath != null) Files.deleteIfExists(spillPath); }
        catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        spillPath = null;
        if (failure != null) throw failure;
    }

    @Override public void close() throws IOException {
        if (!closed) {
            closed = true;
            prefix = null;
            try (Reader owned = source) { releaseSpill(); }
        }
    }
}
