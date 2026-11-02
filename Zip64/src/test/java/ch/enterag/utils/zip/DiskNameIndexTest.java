// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.zip;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DiskNameIndexTest {
    @TempDir Path directory;
    @Test void growthEvictionAndHashCollisionsRetainEveryOffset() throws Exception {
        try (DiskNameIndex index = new DiskNameIndex(directory)) {
            for (int i = 0; i < 200000; i++) index.add(i < 40 ? 19 : CentralDirectorySpool.hash("entry-"+i), 5_000_000_000L+i*123L);
            Random random = new Random(51);
            for (int k = 0; k < 20000; k++) {
                int i = random.nextInt(200000);
                assertEquals(5_000_000_000L+i*123L, index.offset(i));
                int hash = i < 40 ? 19 : CentralDirectorySpool.hash("entry-"+i);
                assertEquals(hash, index.hash(i));
                long slot = index.firstSlot(hash);
                while (index.ordinal(slot) != i) { assertTrue(index.ordinal(slot) >= 0); slot = index.nextSlot(slot); }
            }
            assertTrue(index.cachedPages() <= 256);
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void hugeBoundariesUseLongArithmeticWithoutAllocatingData() throws Exception {
        assertEquals(1L<<27, DiskNameIndex.capacityFor(65536, 100_000_000));
        assertEquals(1L<<32, DiskNameIndex.capacityFor(65536, Integer.MAX_VALUE-1L));
        assertThrows(IOException.class, () -> DiskNameIndex.capacityFor(65536, Integer.MAX_VALUE));
    }

    @Test void forcedMigrationPreservesWholeZipBytesAndDuplicateDetection() throws Exception {
        Path heap = directory.resolve("heap.zip"), disk = directory.resolve("disk.zip");
        try {
            for (Path output : new Path[]{heap,disk}) {
                System.setProperty(Zip64File.PROPERTY_SPILL_ENTRIES, "3");
                System.setProperty(CentralDirectorySpool.PROPERTY_DISK_INDEX_ENTRIES, output == heap ? "1000000" : "7");
                Zip64File zip = new Zip64File(output.toFile());
                try (Closeable close = zip::close) {
                    for (int i = 0; i < 3000; i++) {
                        String name = "content/\uD14C\uC774\uBE14/entry"+i;
                        try (OutputStream out = zip.openEntryOutputStream(name, 8, new Date(1600000000000L))) { out.write(i); }
                        assertNotNull(zip.getFileEntry(name));
                    }
                    assertThrows(java.util.zip.ZipException.class,
                        () -> zip.openEntryOutputStream("content/\uD14C\uC774\uBE14/entry37", 8, null));
                    assertNull(zip.getFileEntry("missing"));
                }
            }
        } finally {
            System.clearProperty(Zip64File.PROPERTY_SPILL_ENTRIES);
            System.clearProperty(CentralDirectorySpool.PROPERTY_DISK_INDEX_ENTRIES);
        }
        assertArrayEquals(Files.readAllBytes(heap), Files.readAllBytes(disk));
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }
}
