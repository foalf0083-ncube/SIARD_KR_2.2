// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.zip;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PrecompressedEntryTest {
    @TempDir Path dir;
    @Test void precompressedStreamsProduceIdenticalZipBytes() throws Exception {
        Path ordinary = dir.resolve("ordinary.zip"), raw = dir.resolve("raw.zip");
        Date date = new Date(1600000000000L);
        Zip64File a = new Zip64File(ordinary.toFile()), b = new Zip64File(raw.toFile());
        try (Closeable closeA = a::close; Closeable closeB = b::close) {
            for (int size : new int[]{0, 1, 4000, 65535, 65536, 65537, 2097153}) {
                for (boolean random : new boolean[]{false, true}) {
                    byte[] data = new byte[size];
                    if (random) new Random(size).nextBytes(data); else Arrays.fill(data, (byte)'x');
                    String name = "content/" + size + "-" + random;
                    try (OutputStream out = a.openEntryOutputStream(name, 8, date)) { out.write(data); }
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    Deflater def = new Deflater(-1, true);
                    try {
                        if (size > 0) try (DeflaterOutputStream out = new DeflaterOutputStream(bytes, def, 4096)) {
                            for (int i = 0; i < size; i += 731) out.write(data, i, Math.min(731, size-i));
                        }
                    } finally { def.end(); }
                    CRC32 crc = new CRC32(); crc.update(data);
                    b.appendDeflatedEntry(name, date, new ByteArrayInputStream(bytes.toByteArray()), bytes.size(), size, crc.getValue());
                }
            }
        }
        assertArrayEquals(Files.readAllBytes(ordinary), Files.readAllBytes(raw));
        try (ZipFile zip = new ZipFile(raw.toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (entry.getSize() == 0) continue; // bundled writer's existing zero-byte representation
                try (InputStream in = zip.getInputStream(entry)) { assertEquals(entry.getSize(), in.readAllBytes().length); }
            }
        }
    }

    @Test void truncatedAndExcessInputFailAndDoNotCommitAnEntry() throws Exception {
        Zip64File zip = new Zip64File(dir.resolve("failed.zip").toFile());
        try (Closeable close = zip::close) {
            assertThrows(IOException.class, () -> zip.appendDeflatedEntry("a", null, new ByteArrayInputStream(new byte[1]), 2, 5, 0));
            assertNull(zip.getFileEntry("a"));
            assertThrows(IOException.class, () -> zip.appendDeflatedEntry("a", null, new ByteArrayInputStream(new byte[3]), 2, 5, 0));
            assertNull(zip.getFileEntry("a"));
            try (OutputStream out = zip.openEntryOutputStream("valid", 8, null)) { out.write(42); }
        }
        try (ZipFile result = new ZipFile(dir.resolve("failed.zip").toFile())) {
            assertEquals(1, result.size());
            assertArrayEquals(new byte[]{42}, result.getInputStream(result.getEntry("valid")).readAllBytes());
        }
    }
}
