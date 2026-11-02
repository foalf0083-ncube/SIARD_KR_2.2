// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.primary;

import ch.enterag.utils.zip.Zip64File;
import java.io.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArchiveFailureCleanupTest {
    @TempDir Path directory;

    @Test
    void metadataFailureStillClosesZipAndResetsArchive() throws Exception {
        ArchiveImpl archive = spy((ArchiveImpl) ArchiveImpl.newInstance());
        archive.create(directory.resolve("metadata-failed.siard").toFile());
        archive.getMetaData().setDbName("FAILURE_FIXTURE");
        Zip64File zip = archive.getZipFile();
        IOException failure = new IOException("metadata export failed");
        doThrow(failure).when(archive).exportMetaDataSchema(any(OutputStream.class));
        assertSame(failure, assertThrows(IOException.class, archive::close));
        assertNull(archive.getZipFile());
        assertThrows(IOException.class, () -> zip.getDiskFile().length());
    }
}
