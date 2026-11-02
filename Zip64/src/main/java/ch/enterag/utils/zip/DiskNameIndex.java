// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.zip;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Temporary ordinal and hash-slot index. Exact names remain in the central-directory
 * spool; hashes only select candidates. Two bounded page caches use at most 4 MiB,
 * or 6 MiB during a rehash, independently of the number of entries.
 */
final class DiskNameIndex implements Closeable {
    private final Path directory;
    private final Pages records;
    private Pages slots;
    private long capacity = 1L << 16;
    private int size;

    DiskNameIndex(Path directory) throws IOException {
        this.directory = directory;
        records = new Pages(directory);
        try { slots = new Pages(directory); slots.extend(capacity * 4); }
        catch (IOException | RuntimeException | Error e) {
            try { records.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            if (slots != null) try { slots.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    static long capacityFor(long capacity, long count) throws IOException {
        if (count < 0 || count > Integer.MAX_VALUE - 1L) throw new IOException("ZIP ordinal limit exceeded");
        while (count * 4 > capacity * 3) capacity *= 2;
        return capacity;
    }

    void add(int hash, long offset) throws IOException {
        long grown = capacityFor(capacity, (long)size + 1);
        if (grown != capacity) {
            Pages replacement = new Pages(directory);
            boolean committed = false;
            try {
                replacement.extend(grown * 4);
                for (int i = 0; i < size; i++) insert(replacement, grown, hash(i), i);
                Pages old = slots;
                slots = replacement; capacity = grown; committed = true;
                old.close();
            } finally { if (!committed) replacement.close(); }
        }
        records.putInt((long)size * 16, hash);
        records.putLong((long)size * 16 + 8, offset);
        insert(slots, capacity, hash, size);
        size++;
    }

    private static void insert(Pages slots, long capacity, int hash, int ordinal) throws IOException {
        long mask = capacity - 1, slot = Integer.toUnsignedLong(hash) & mask;
        while (slots.getInt(slot * 4) != 0) slot = (slot + 1) & mask;
        slots.putInt(slot * 4, ordinal + 1);
    }
    long firstSlot(int hash) { return Integer.toUnsignedLong(hash) & (capacity - 1); }
    long nextSlot(long slot) { return (slot + 1) & (capacity - 1); }
    int ordinal(long slot) throws IOException { return slots.getInt(slot * 4) - 1; }
    int hash(int ordinal) throws IOException { return records.getInt((long)ordinal * 16); }
    long offset(int ordinal) throws IOException { return records.getLong((long)ordinal * 16 + 8); }
    int cachedPages() { return records.cache.size() + slots.cache.size(); }

    @Override public void close() throws IOException {
        try (Pages a = records; Pages b = slots) { /* discard scratch pages */ }
    }

    /** All int/long fields are naturally aligned, so never straddle a page. */
    private static final class Pages implements Closeable {
        private static final int PAGE_SIZE = 16384, MAX_PAGES = 128;
        private final Path path;
        private final RandomAccessFile file;
        private final LinkedHashMap<Long, Page> cache = new LinkedHashMap<>(128, .75f, true);
        private long length;
        private static final class Page { final byte[] bytes = new byte[PAGE_SIZE]; boolean dirty; }

        Pages(Path directory) throws IOException {
            path = Files.createTempFile(directory, "siard_index_", ".tmp");
            try { file = new RandomAccessFile(path.toFile(), "rw"); }
            catch (IOException e) { try { Files.delete(path); } catch (IOException cleanup) { e.addSuppressed(cleanup); } throw e; }
            path.toFile().deleteOnExit();
        }
        void extend(long length) throws IOException { file.setLength(length); this.length = length; }
        Page page(long position) throws IOException {
            long number = position / PAGE_SIZE;
            Page page = cache.get(number);
            if (page != null) return page;
            if (cache.size() == MAX_PAGES) {
                var iterator = cache.entrySet().iterator();
                var oldest = iterator.next();
                if (oldest.getValue().dirty) {
                    long start = oldest.getKey() * PAGE_SIZE;
                    file.seek(start); file.write(oldest.getValue().bytes);
                    length = Math.max(length, start + PAGE_SIZE);
                }
                iterator.remove();
            }
            page = new Page();
            long start = number * PAGE_SIZE;
            if (start < length) {
                file.seek(start); file.readFully(page.bytes, 0, (int)Math.min(PAGE_SIZE, length-start));
            }
            cache.put(number, page); return page;
        }
        long get(long position, int bytes) throws IOException {
            Page page = page(position); int offset = (int)(position % PAGE_SIZE);
            long value = 0;
            for (int i = 0; i < bytes; i++) value |= (page.bytes[offset+i] & 255L) << (8*i);
            return value;
        }
        void put(long position, long value, int bytes) throws IOException {
            Page page = page(position); int offset = (int)(position % PAGE_SIZE);
            for (int i = 0; i < bytes; i++) page.bytes[offset+i] = (byte)(value >>> (8*i));
            page.dirty = true;
        }
        int getInt(long position) throws IOException { return (int)get(position, 4); }
        long getLong(long position) throws IOException { return get(position, 8); }
        void putInt(long position, int value) throws IOException { put(position, value, 4); }
        void putLong(long position, long value) throws IOException { put(position, value, 8); }
        @Override public void close() throws IOException {
            cache.clear();
            try { file.close(); } finally { Files.deleteIfExists(path); }
        }
    }
}
