// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.zip;

import ch.enterag.utils.io.DiskFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class ZipFailureCleanupTest {
    @TempDir Path directory;

    @Test
    void entryCountLimitFailsBeforeWritingAnEntry() throws Exception {
        Zip64File zip = new Zip64File(directory.resolve("count-limit.zip").toFile());
        Field count = Zip64File.class.getDeclaredField("m_iFileEntries");
        count.setAccessible(true);
        count.setInt(zip, Integer.MAX_VALUE);
        long before = zip.getDiskFile().length();
        try {
            assertThrows(java.util.zip.ZipException.class,
                    () -> zip.openEntryOutputStream("overflow", 0, new Date(0)));
            assertEquals(before, zip.getDiskFile().length());
            assertEquals(Integer.MAX_VALUE, zip.getFileEntries());
        } finally {
            count.setInt(zip, 0);
            zip.close();
        }
    }

    @Test
    void centralDirectoryFailureStillClosesFileAndPreservesBothErrors() throws Exception {
        Zip64File zip = new Zip64File(directory.resolve("failed.zip").toFile());
        try (OutputStream out = zip.openEntryOutputStream("data", 0, new Date(0))) {
            out.write(new byte[]{1, 2, 3});
        }
        zip.getDiskFile().close();
        class FailingFile extends DiskFile {
            boolean closed;
            FailingFile() throws IOException { super(directory.resolve("injected.bin").toFile()); }
            @Override public void write(byte[] b) throws IOException { throw new IOException("directory write failed"); }
            @Override public void close() throws IOException {
                closed = true;
                super.close();
                throw new IOException("disk close failed");
            }
        }
        FailingFile file = new FailingFile();
        Field field = Zip64File.class.getDeclaredField("m_df");
        field.setAccessible(true);
        field.set(zip, file);
        IOException failure = assertThrows(IOException.class, zip::close);
        assertEquals("directory write failed", failure.getMessage());
        assertTrue(file.closed, "The disk handle must close even when writing the directory fails");
        assertEquals("disk close failed", failure.getSuppressed()[0].getMessage());
    }
}
