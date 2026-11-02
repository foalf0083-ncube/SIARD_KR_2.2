// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.tika.Tika;
import org.apache.tika.detect.DefaultDetector;

/** Per-table, bounded memoization of COMPLETE detector inputs, never prefixes. */
final class MimeDetectionCache {
    static final String PROPERTY_BYTES = "siard.mime.cacheBytes";
    static final int MAX_INPUT = 8192;
    private static final int MAX_ENTRIES = 2048;
    private final Tika tika;
    private final long budget;
    private final Map<Key, String> entries = new LinkedHashMap<>(64, .75f, true);
    private long bytes;
    private long hits;

    MimeDetectionCache(Tika tika) {
        this.tika = tika;
        long configured = Long.getLong(PROPERTY_BYTES, 4L << 20);
        // User-supplied detectors may depend on stream identity or mutable state.
        budget = tika.getClass() == Tika.class && tika.getDetector().getClass() == DefaultDetector.class
                ? Math.max(0, Math.min(32L << 20, configured)) : 0;
    }

    String detect(byte[] value) {
        if (budget == 0 || value.length > MAX_INPUT) return tika.detect(value);
        return complete(value);
    }

    String detect(InputStream input) throws IOException {
        return detect(input, memoryLength(input));
    }

    static int memoryLength(InputStream input) {
        // available() is NOT generally the stream length. Trust only this exact JDK class.
        return input.getClass() == ByteArrayInputStream.class ? ((ByteArrayInputStream) input).available() : -1;
    }

    String detect(InputStream input, int memoryLength) throws IOException {
        if (budget == 0 || memoryLength < 0 || memoryLength > MAX_INPUT) return tika.detect(input);
        byte[] entire = input.readNBytes(memoryLength);
        if (entire.length != memoryLength) throw new IOException("In-memory MIME input was truncated");
        return complete(entire);
    }

    private String complete(byte[] input) {
        Key lookup = new Key(input);
        String result = entries.get(lookup);
        if (result != null) { hits++; return result; }
        // Tika's byte-array overload uses the same full-byte detection, with empty metadata.
        result = tika.detect(input);
        long cost = input.length + 128L;
        if (cost <= budget) {
            while (!entries.isEmpty() && (bytes + cost > budget || entries.size() >= MAX_ENTRIES)) {
                var it = entries.entrySet().iterator();
                bytes -= it.next().getKey().data.length + 128L; it.remove();
            }
            entries.put(new Key(input.clone()), result); bytes += cost;
        }
        return result;
    }

    long retainedBytes() { return bytes; }
    int size() { return entries.size(); }
    long hits() { return hits; }

    private static final class Key {
        final byte[] data;
        final int hash;
        Key(byte[] data) { this.data = data; hash = Arrays.hashCode(data); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof Key && Arrays.equals(data, ((Key) other).data);
        }
    }
}
