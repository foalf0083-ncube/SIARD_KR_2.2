// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.zip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CentralDirectorySpoolTest {
    @TempDir Path directory;
    private static final Date DATE = new Date(1_600_000_000_000L);
    private static final int ENTRIES = 3000;

    @AfterEach
    void clearProperties() {
        System.clearProperty(Zip64File.PROPERTY_SPILL_ENTRIES);
        System.clearProperty(CentralDirectorySpool.PROPERTY_SPOOL_DIRECTORY);
    }

    @Test
    void hugeIndexLimitIsCheckedWithoutAllocationOrSignedOverflow() throws Exception {
        assertEquals(1 << 27, CentralDirectorySpool.slotCapacity(1 << 27, 100_663_296L));
        assertEquals(1 << 28, CentralDirectorySpool.slotCapacity(1 << 27, 100_663_297L));
        assertEquals(1 << 30, CentralDirectorySpool.slotCapacity(1 << 30, 805_306_368L));
        assertThrows(IOException.class,
                () -> CentralDirectorySpool.slotCapacity(1 << 30, 805_306_369L));
    }

    @Test
    void scratchDirectoryCanBePlacedOnSeparateVolumeAndIsCleaned() throws Exception {
        Path scratch = Files.createDirectory(directory.resolve("scratch"));
        System.setProperty(CentralDirectorySpool.PROPERTY_SPOOL_DIRECTORY, scratch.toString());
        File file;
        try (CentralDirectorySpool spool = CentralDirectorySpool.create(directory.resolve("a").toFile())) {
            file = spool.getFile();
            assertEquals(scratch.toFile(), file.getParentFile());
            assertTrue(file.exists());
        }
        assertFalse(file.exists());
        System.setProperty(CentralDirectorySpool.PROPERTY_SPOOL_DIRECTORY,
                directory.resolve("missing").toString());
        assertThrows(IOException.class,
                () -> CentralDirectorySpool.create(directory.resolve("a").toFile()));
    }

    private Zip64File open(String name, int spillEntries) throws IOException {
        System.setProperty(Zip64File.PROPERTY_SPILL_ENTRIES, Integer.toString(spillEntries));
        try {
            return new Zip64File(directory.resolve(name).toFile());
        } finally {
            clearProperties();
        }
    }

    /** SIARD-like layout: folders, LOB files (stored and deflated, some empty), Korean names, table XML. */
    private static List<String> writeEntries(Zip64File zip) throws IOException {
        Random random = new Random(3);
        List<String> names = new ArrayList<>();
        zip.setComment("SIARD");
        for (int table = 0; table < 3; table++) {
            String folder = "content/schema0/table" + table + "/";
            for (String f : new String[]{"content/", "content/schema0/", folder, folder + "lob1/", folder + "lob2/"}) {
                if (zip.getFileEntry(f) == null) {
                    try (OutputStream out = zip.openEntryOutputStream(f, FileEntry.iMETHOD_STORED, DATE)) { /* folder */ }
                    names.add(f);
                }
            }
            for (int i = 0; i < ENTRIES / 3; i++) {
                String name = folder + (i % 2 == 0 ? "lob1/record" + i + ".bin" : "lob2/기록" + i + ".txt");
                assertNull(zip.getFileEntry(name));
                byte[] data = new byte[i % 50 == 0 ? 0 : random.nextInt(3000)];
                random.nextBytes(data);
                try (OutputStream out = zip.openEntryOutputStream(name, i % 3 == 0 ? FileEntry.iMETHOD_STORED : FileEntry.iMETHOD_DEFLATED, DATE)) {
                    out.write(data);
                }
                names.add(name);
            }
            String xml = folder + "table" + table + ".xml";
            try (OutputStream out = zip.openEntryOutputStream(xml, FileEntry.iMETHOD_DEFLATED, DATE)) {
                out.write(("<table>" + table + "</table>").getBytes(StandardCharsets.UTF_8));
            }
            names.add(xml);
        }
        return names;
    }

    private static void assertSameEntry(FileEntry expected, FileEntry actual) {
        assertNotNull(actual, expected.getName());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getOffset(), actual.getOffset());
        assertEquals(expected.getSize(), actual.getSize());
        assertEquals(expected.getCompressedSize(), actual.getCompressedSize());
        assertEquals(expected.getCrc(), actual.getCrc());
        assertEquals(expected.getMethod(), actual.getMethod());
        assertEquals(expected.getFlags(), actual.getFlags());
        assertEquals(expected.getDateTime(), actual.getDateTime());
        assertArrayEquals(expected.getExtra(), actual.getExtra());
        assertEquals(expected.getComment(), actual.getComment());
    }

    private long spoolFiles() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".cdspool")).count();
        }
    }

    @Test
    void spooledZipHasSameBytesAsHeapZip() throws Exception {
        Zip64File heap = open("heap.zip", 0);
        List<String> names = writeEntries(heap);
        heap.close();
        Zip64File spooled = open("spooled.zip", 10);
        assertEquals(names, writeEntries(spooled));
        assertEquals(1, spoolFiles());
        spooled.close();
        assertEquals(0, spoolFiles());
        assertArrayEquals(Files.readAllBytes(directory.resolve("heap.zip")),
                Files.readAllBytes(directory.resolve("spooled.zip")));
    }

    @Test
    void spooledEntriesAreFoundWithSameValuesAsReadFromTheFile() throws Exception {
        Zip64File heap = open("heap.zip", 0);
        writeEntries(heap);
        heap.close();
        Zip64File spooled = open("spooled.zip", 10);
        List<String> names = writeEntries(spooled);
        Zip64File reread = new Zip64File(directory.resolve("heap.zip").toFile(), true);
        try {
            for (String name : names)
                assertSameEntry(reread.getFileEntry(name), spooled.getFileEntry(name));
            assertNull(spooled.getFileEntry("content/schema0/table0/lob1/record1.bin"));
            assertNull(spooled.getFileEntry("content/schema0/table9/"));
            String last = names.get(names.size() - 1);
            assertThrows(java.util.zip.ZipException.class,
                    () -> spooled.openEntryOutputStream(last, FileEntry.iMETHOD_DEFLATED, DATE));
            assertThrows(java.util.zip.ZipException.class,
                    () -> spooled.openEntryOutputStream(names.get(500), FileEntry.iMETHOD_STORED, DATE));
        } finally {
            reread.close();
            spooled.close();
        }
        Zip64File zip = new Zip64File(directory.resolve("spooled.zip").toFile(), true);
        try (InputStream in = zip.openEntryInputStream("content/schema0/table2/table2.xml")) {
            assertEquals("<table>2</table>", new String(readAll(in), StandardCharsets.UTF_8));
        } finally {
            zip.close();
        }
    }

    @Test
    void deleteAndListMoveSpooledEntriesBackToTheHeap() throws Exception {
        Zip64File heap = open("heap.zip", 0);
        List<String> names = writeEntries(heap);
        heap.delete(names.get(1500));
        try (OutputStream out = heap.openEntryOutputStream("header/metadata.xml", FileEntry.iMETHOD_DEFLATED, DATE)) {
            out.write("<siardArchive/>".getBytes(StandardCharsets.UTF_8));
        }
        heap.close();
        Zip64File spooled = open("spooled.zip", 10);
        writeEntries(spooled);
        spooled.delete(names.get(1500));
        assertEquals(0, spoolFiles());
        try (OutputStream out = spooled.openEntryOutputStream("header/metadata.xml", FileEntry.iMETHOD_DEFLATED, DATE)) {
            out.write("<siardArchive/>".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(0, spoolFiles());
        spooled.close();
        assertArrayEquals(Files.readAllBytes(directory.resolve("heap.zip")),
                Files.readAllBytes(directory.resolve("spooled.zip")));

        Zip64File listed = open("listed.zip", 10);
        writeEntries(listed);
        List<String> listedNames = new ArrayList<>();
        for (FileEntry fe : listed.getListFileEntries())
            listedNames.add(fe.getName());
        assertEquals(names, listedNames);
        listed.close();
    }

    @Test
    void indexGrowsAndKeepsAllRecords() throws Exception {
        File file = directory.resolve("index.zip").toFile();
        try (CentralDirectorySpool spool = CentralDirectorySpool.create(file)) {
            int n = 200_000; // several index doublings and buffer flushes
            for (int i = 0; i < n; i++) {
                FileEntry fe = new FileEntry("content/schema0/table0/lob1/record" + i + ".bin");
                fe.setOffset(i * 1000L);
                fe.setMethod(FileEntry.iMETHOD_STORED);
                fe.setTimeStamp(DATE);
                fe.setCrc(0);
                fe.setSize(0);
                fe.setCompressedSize(0);
                fe.setExtra(new byte[0]);
                fe.setComment("");
                spool.add(fe.getName(), record(fe));
            }
            assertEquals(n, spool.size());
            for (int i = 0; i < n; i += 997) {
                byte[] found = spool.find("content/schema0/table0/lob1/record" + i + ".bin");
                assertNotNull(found);
                assertEquals(i * 1000L, Zip64File.getCentralDirectoryEntry(found).getOffset());
            }
            assertNull(spool.find("content/schema0/table0/lob1/record" + n + ".bin"));
        }
        assertEquals(0, spoolFiles());
    }

    private static byte[] record(FileEntry fe) throws Exception {
        java.lang.reflect.Method m = Zip64File.class.getDeclaredMethod("getCentralDirectoryRecord", FileEntry.class);
        m.setAccessible(true);
        return (byte[]) m.invoke(null, fe);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int n = in.read(buf); n != -1; n = in.read(buf))
            out.write(buf, 0, n);
        return out.toByteArray();
    }
}
