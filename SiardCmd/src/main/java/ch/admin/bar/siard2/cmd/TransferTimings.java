// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import java.io.FilterInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Locale;

/** Optional per-table diagnostics; source reads include driver buffering/conversion, not only DB time. */
final class TransferTimings {
    static final TransferTimings DISABLED = new TransferTimings(false);
    final boolean enabled;
    long queryNanos, fetchNanos, createNanos, rowNanos, getterNanos, xmlNanos, finishNanos;
    long sourceReadNanos, sourceReadCalls, mimeNanos, lobWriteNanos;

    TransferTimings(boolean enabled) { this.enabled = enabled; }

    static TransferTimings forTable() {
        return new TransferTimings(Boolean.getBoolean("siard.transfer.timings"));
    }

    long now() { return enabled ? System.nanoTime() : 0; }
    long elapsed(long start) { return enabled ? System.nanoTime() - start : 0; }

    void finishMime(long start, long readsBefore) {
        if (enabled) mimeNanos += Math.max(0, elapsed(start) - (sourceReadNanos - readsBefore));
    }

    void finishLobWrite(long start, long readsBefore) {
        if (enabled) lobWriteNanos += Math.max(0, elapsed(start) - (sourceReadNanos - readsBefore));
    }

    Reader source(Reader reader) {
        if (!enabled) return reader;
        return new FilterReader(reader) {
            @Override public int read(char[] chars, int offset, int length) throws IOException {
                long start = System.nanoTime();
                try { return in.read(chars, offset, length); }
                finally { sourceReadNanos += System.nanoTime() - start; sourceReadCalls++; }
            }
            @Override public int read() throws IOException {
                long start = System.nanoTime();
                try { return in.read(); }
                finally { sourceReadNanos += System.nanoTime() - start; sourceReadCalls++; }
            }
        };
    }

    InputStream source(InputStream input) {
        if (!enabled) return input;
        return new FilterInputStream(input) {
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                long start = System.nanoTime();
                try { return in.read(bytes, offset, length); }
                finally { sourceReadNanos += System.nanoTime() - start; sourceReadCalls++; }
            }
            @Override public int read() throws IOException {
                long start = System.nanoTime();
                try { return in.read(); }
                finally { sourceReadNanos += System.nanoTime() - start; sourceReadCalls++; }
            }
        };
    }

    String summary(String table, long rows) {
        return String.format(Locale.ROOT,
                "Transfer timings table=%s rows=%d query_ms=%.3f fetch_ms=%.3f create_ms=%.3f "
                + "row_ms=%.3f getter_ms=%.3f lob_source_ms=%.3f mime_cpu_ms=%.3f "
                + "lob_write_ms=%.3f xml_ms=%.3f finish_ms=%.3f source_read_calls=%d "
                + "(getter/lob_source/mime_cpu/lob_write are within row_ms; lob_source includes JDBC conversion/cache)",
                table, rows, queryNanos / 1e6, fetchNanos / 1e6, createNanos / 1e6,
                rowNanos / 1e6, getterNanos / 1e6, sourceReadNanos / 1e6, mimeNanos / 1e6,
                lobWriteNanos / 1e6, xmlNanos / 1e6, finishNanos / 1e6, sourceReadCalls);
    }
}
