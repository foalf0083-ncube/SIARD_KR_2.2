// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.zip;

import ch.enterag.utils.io.DiskFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AppendBufferedDiskFileTest {
    @TempDir Path directory;
    private static final Date DATE = new Date(1_600_000_000_000L);

    @AfterEach
    void clearProperties() {
        System.clearProperty(AppendBufferedDiskFile.PROPERTY_WRITE_BUFFER);
        System.clearProperty(AppendBufferedDiskFile.PROPERTY_APPEND_DIGEST);
    }

    /** Applies the same operations to both files and checks every observable result. */
    private static void mixedOperations(DiskFile f, StringBuilder log) throws IOException {
        Random random = new Random(7);
        byte[] small = new byte[100];
        byte[] page = new byte[4096];
        byte[] large = new byte[3 << 20];
        random.nextBytes(small);
        random.nextBytes(page);
        random.nextBytes(large);
        f.write(1);
        f.write(small);
        for (int i = 0; i < 300; i++) f.write(page, i % 7, 4096 - i % 7);
        log.append(f.getFilePointer()).append(',').append(f.length()).append(';');
        f.writeInt(0x01020304);
        f.writeLong(0x0102030405060708L);
        f.write(large);
        log.append(f.getFilePointer()).append(',').append(f.length()).append(';');
        f.seek(50);
        f.write(small, 3, 40);
        log.append(f.getFilePointer()).append(',').append(f.length()).append(';');
        byte[] read = new byte[500];
        f.seek(10);
        f.readFully(read);
        log.append(java.util.Arrays.hashCode(read)).append(';');
        log.append(f.read()).append(';');
        f.seek(f.length());
        f.write(page);
        f.setLength(f.length() - 1000);
        f.seek(f.length());
        f.write(small);
        f.setLength(f.length() + 123);
        log.append(f.getFilePointer()).append(',').append(f.length()).append(';');
        log.append(java.util.Arrays.toString(f.digest("MD5", 0, f.length()))).append(';');
        log.append(java.util.Arrays.toString(f.digest("SHA-1", 7, 70000))).append(';');
    }

    @Test
    void writesSameBytesAndResultsAsDiskFile() throws Exception {
        File plain = directory.resolve("plain.bin").toFile();
        File buffered = directory.resolve("buffered.bin").toFile();
        StringBuilder expected = new StringBuilder();
        StringBuilder actual = new StringBuilder();
        try (DiskFile f = new DiskFile(plain)) { mixedOperations(f, expected); }
        try (DiskFile f = AppendBufferedDiskFile.open(buffered, false)) { mixedOperations(f, actual); }
        assertEquals(expected.toString(), actual.toString());
        assertArrayEquals(Files.readAllBytes(plain.toPath()), Files.readAllBytes(buffered.toPath()));
    }

    @Test
    void appendDigestEqualsDigestOfFileBytes() throws Exception {
        File file = directory.resolve("append.bin").toFile();
        Random random = new Random(11);
        try (DiskFile f = AppendBufferedDiskFile.open(file, false)) {
            for (int i = 0; i < 500; i++) {
                byte[] chunk = new byte[random.nextInt(9000)];
                random.nextBytes(chunk);
                f.write(chunk);
            }
            long end = f.length();
            byte[] tracked = f.digest("MD5", 0, end);
            assertArrayEquals(md5(file, end), tracked);
            // a later overwrite discards the tracked value; the digest is read from the file
            f.seek(17);
            f.write(new byte[]{1, 2, 3});
            byte[] reread = f.digest("MD5", 0, f.length()); // flushes pending bytes first
            assertArrayEquals(md5(file, f.length()), reread);
        }
    }

    private static byte[] md5(File file, long end) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(bytes, 0, (int) end);
        return md.digest();
    }

    private byte[] writeZip(String name, boolean optimized) throws Exception {
        if (!optimized) {
            System.setProperty(AppendBufferedDiskFile.PROPERTY_WRITE_BUFFER, "0");
            System.setProperty(AppendBufferedDiskFile.PROPERTY_APPEND_DIGEST, "false");
        } else {
            clearProperties();
        }
        Path file = directory.resolve(name);
        Random random = new Random(5);
        Zip64File zip = new Zip64File(file.toFile());
        byte[] digest;
        long primaryEnd;
        try {
            zip.setComment("SIARD");
            try (OutputStream out = zip.openEntryOutputStream("content/", 0, DATE)) { /* folder */ }
            for (int i = 0; i < 400; i++) {
                byte[] value = ("제" + i + "조 한글 조항 ").repeat(1 + random.nextInt(300)).getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = zip.openEntryOutputStream("content/schema0/table0/lob1/record" + i + ".txt", 8, DATE)) {
                    out.write(value);
                }
            }
            try (OutputStream out = zip.openEntryOutputStream("content/schema0/table0/lob1/empty.txt", 8, DATE)) { /* empty */ }
            byte[] binary = new byte[2_500_000];
            random.nextBytes(binary);
            try (OutputStream out = zip.openEntryOutputStream("content/schema0/table0/lob2/record0.bin", 0, DATE)) {
                out.write(binary);
            }
            // like ArchiveImpl.close: digest of everything written before the central directory
            primaryEnd = zip.getDiskFile().length();
            digest = zip.getDiskFile().digest("MD5", 0, primaryEnd);
        } finally {
            zip.close();
        }
        byte[] bytes = Files.readAllBytes(file);
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(bytes, 0, (int) primaryEnd);
        assertArrayEquals(md.digest(), digest);
        try (java.util.zip.ZipFile check = new java.util.zip.ZipFile(file.toFile())) {
            assertEquals(403, check.size());
        }
        return bytes;
    }

    @Test
    void zipFileIsByteIdenticalWithAndWithoutOptimization() throws Exception {
        byte[] original = writeZip("original.zip", false);
        byte[] optimized = writeZip("optimized.zip", true);
        assertArrayEquals(original, optimized);
    }
}
