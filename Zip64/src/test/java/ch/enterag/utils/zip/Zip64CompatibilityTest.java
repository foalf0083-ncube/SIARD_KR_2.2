// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.zip;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Zip64CompatibilityTest {
    @TempDir Path directory;
    private static final Date DATE = new Date(1_600_000_000_000L);

    @Test
    void characterReaderReachesEofAcrossCompressedBufferBoundary() throws Exception {
        byte[] expected;
        try (InputStream fixture = getClass().getResourceAsStream("/complex_clob_boundary.txt")) {
            expected = fixture.readAllBytes();
        }
        Path file = directory.resolve("boundary.zip");
        Zip64File archive = new Zip64File(file.toFile());
        try (OutputStream out = archive.openEntryOutputStream("body.txt", 8, DATE)) {
            out.write(expected);
        } finally { archive.close(); }
        try (ZipFile standard = new ZipFile(file.toFile())) {
            assertEquals(4097, standard.getEntry("body.txt").getCompressedSize());
            assertArrayEquals(expected, standard.getInputStream(standard.getEntry("body.txt")).readAllBytes());
        }
        archive = new Zip64File(file.toFile(), true);
        try (Reader reader = new InputStreamReader(archive.openEntryInputStream("body.txt"), StandardCharsets.UTF_8)) {
            StringWriter actual = new StringWriter();
            reader.transferTo(actual);
            assertEquals(new String(expected, StandardCharsets.UTF_8), actual.toString());
        } finally { archive.close(); }
    }

    @Test
    void preservesStoredDeflatedUnicodeEmptyEntriesAndUpdates() throws Exception {
        Path file = directory.resolve("mixed.zip");
        LinkedHashMap<String, byte[]> expected = new LinkedHashMap<>();
        expected.put("folder/", new byte[0]);
        expected.put("folder/empty.txt", new byte[0]);
        expected.put("folder/한글 조항.txt", "제1조\r\n① 한글 😀 & < >".repeat(500).getBytes(StandardCharsets.UTF_8));
        byte[] random = new byte[200_000];
        new Random(10).nextBytes(random);
        expected.put("random.bin", random);
        Zip64File archive = new Zip64File(file.toFile());
        try {
            archive.setComment("CLOB 시험");
            int index = 0;
            for (var entry : expected.entrySet()) {
                try (OutputStream out = archive.openEntryOutputStream(entry.getKey(), index++ % 2 == 0 ? 0 : 8, DATE)) {
                    out.write(entry.getValue());
                }
            }
        } finally { archive.close(); }
        verify(file, expected, "CLOB 시험");

        archive = new Zip64File(file.toFile());
        try {
            archive.delete("folder/empty.txt");
            expected.remove("folder/empty.txt");
            byte[] tail = "추가한 본문".getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = archive.openEntryOutputStream("tail.txt", 8, DATE)) { out.write(tail); }
            expected.put("tail.txt", tail);
        } finally { archive.close(); }
        verify(file, expected, "CLOB 시험");
    }

    @Test
    void readsZip64CentralDirectoryWithMoreThan65535Entries() throws Exception {
        Path file = directory.resolve("many.zip");
        int count = 65_538;
        Zip64File archive = new Zip64File(file.toFile());
        try {
            for (int i = 0; i < count; i++) {
                try (OutputStream out = archive.openEntryOutputStream("record" + i + ".txt", 8, DATE)) {
                    out.write(i & 0xff);
                }
            }
        } finally { archive.close(); }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            assertEquals(count, zip.size());
            for (int i : new int[]{0, 32768, 65534, 65535, count - 1}) {
                try (InputStream in = zip.getInputStream(zip.getEntry("record" + i + ".txt"))) {
                    assertArrayEquals(new byte[]{(byte)i}, in.readAllBytes());
                }
            }
        }
        archive = new Zip64File(file.toFile(), true);
        try { assertEquals(count, archive.getFileEntries()); }
        finally { archive.close(); }
    }

    private void verify(Path file, Map<String, byte[]> expected, String comment) throws Exception {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            assertEquals(expected.size(), zip.size());
            assertEquals(comment, zip.getComment());
            for (var entry : expected.entrySet()) {
                ZipEntry stored = zip.getEntry(entry.getKey());
                assertNotNull(stored);
                assertEquals(entry.getValue().length, stored.getSize());
                // The bundled writer emits zero compressed bytes for empty deflated
                // entries. Preserve that legacy format and check it with its reader below.
                if (entry.getValue().length > 0) {
                    try (InputStream in = zip.getInputStream(stored)) { assertArrayEquals(entry.getValue(), in.readAllBytes()); }
                }
                CRC32 crc = new CRC32();
                crc.update(entry.getValue());
                assertEquals(crc.getValue(), stored.getCrc());
            }
        }
        Zip64File archive = new Zip64File(file.toFile(), true);
        try {
            for (var entry : expected.entrySet()) {
                if (!entry.getKey().endsWith("/")) {
                    try (InputStream in = archive.openEntryInputStream(entry.getKey())) {
                        assertArrayEquals(entry.getValue(), in.readAllBytes());
                    }
                }
            }
        } finally { archive.close(); }
    }
}
