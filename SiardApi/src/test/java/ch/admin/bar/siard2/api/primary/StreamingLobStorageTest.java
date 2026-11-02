// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.primary;

import ch.admin.bar.siard2.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

class StreamingLobStorageTest {
    @TempDir Path directory;
    @Test
    void precompressionAndAsyncKeepEntryOrderAndCompressedPayloads() throws Exception {
        Path old;
        try {
            System.setProperty(RecordRetainerImpl.PROPERTY_PRECOMPRESS, "false");
            System.setProperty(RecordRetainerImpl.PROPERTY_ASYNC, "false");
            old = write("old-spool.siard", true, false);
        } finally {
            System.clearProperty(RecordRetainerImpl.PROPERTY_PRECOMPRESS);
            System.clearProperty(RecordRetainerImpl.PROPERTY_ASYNC);
        }
        Path current = write("compressed-spool.siard", true, false);
        verify(old); verify(current);
        var za = new ch.enterag.utils.zip.Zip64File(old.toFile());
        var zb = new ch.enterag.utils.zip.Zip64File(current.toFile());
        try (ZipFile a = new ZipFile(old.toFile()); ZipFile b = new ZipFile(current.toFile());
             Closeable closeA = za::close; Closeable closeB = zb::close) {
            assertEquals(a.stream().map(e -> e.getName()).collect(Collectors.toList()),
                    b.stream().map(e -> e.getName()).collect(Collectors.toList()));
            for (var entry : a.stream().collect(Collectors.toList())) {
                String name = entry.getName();
                if (!name.startsWith("content/") || entry.isDirectory()) continue;
                assertEquals(entry.getCrc(), b.getEntry(name).getCrc(), name);
                assertArrayEquals(raw(old, za.getFileEntry(name)), raw(current, zb.getFileEntry(name)), name);
            }
        }
    }

    private static byte[] raw(Path path, ch.enterag.utils.zip.FileEntry entry) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "r")) {
            f.seek(entry.getOffset() + 26);
            int nameLength = Short.toUnsignedInt(Short.reverseBytes(f.readShort()));
            int extraLength = Short.toUnsignedInt(Short.reverseBytes(f.readShort()));
            f.seek(entry.getOffset() + 30 + nameLength + extraLength);
            byte[] bytes = new byte[Math.toIntExact(entry.getCompressedSize())];
            f.readFully(bytes); return bytes;
        }
    }
    private final List<String> texts = Arrays.asList(null, "", "한글 😀 & < > \r\n\u0000",
            "가".repeat(4000), "나".repeat(4001), "a".repeat(65535) + "😀한글",
            "법률 조항\r\n".repeat(150000), "A\uffffB");

    private byte[] bytes(int row) {
        if (row == 0) return null;
        int[] sizes = {0, 0, 1, 4000, 4001, 65537, 1048577, 17};
        byte[] value = new byte[sizes[row]];
        new Random(714 + row).nextBytes(value);
        return value;
    }

    @Test
    void directAndStagedArchivesHaveIdenticalValuesReferencesAndDigests() throws Exception {
        Set<Path> before = spools();
        Path staged = write("staged.siard", false, false);
        Path direct = write("direct.siard", true, false);
        assertEquals(before, spools(), "XML scratch files must be removed after success");
        verify(staged);
        verify(direct);
        try (ZipFile oldZip = new ZipFile(staged.toFile()); ZipFile newZip = new ZipFile(direct.toFile())) {
            Set<String> oldNames = oldZip.stream().map(e -> e.getName()).collect(Collectors.toSet());
            assertEquals(oldNames, newZip.stream().map(e -> e.getName()).collect(Collectors.toSet()));
            assertEquals((texts.size() - 1) * 2,
                    newZip.stream().filter(e -> e.getName().endsWith(".txt")).count(),
                    "CLOB and NCLOB cells must all use files, including empty and short values");
            for (String name : oldNames) {
                var entry = oldZip.getEntry(name);
                if (!name.startsWith("content/") || entry.isDirectory() || entry.getSize() == 0) continue;
                try (InputStream a = oldZip.getInputStream(entry);
                     InputStream b = newZip.getInputStream(newZip.getEntry(name))) {
                    assertArrayEquals(a.readAllBytes(), b.readAllBytes(), name);
                }
            }
        }
    }

    @Test
    void internalLobsAlreadyExistInArchiveBeforeTableClose() throws Exception {
        ArchiveImpl archive = create("early.siard");
        TableImpl table = table(archive, "T");
        RecordRetainerImpl records = table.createRecordsStreamingLobs();
        ch.admin.bar.siard2.api.Record record = records.create();
        assertNull(((RecordImpl)record).getTemporaryLobFolder());
        record.getCell(0).setReader(new StringReader(texts.get(4)));
        record.getCell(2).setInputStream(new ByteArrayInputStream(bytes(5)));
        assertTrue(archive.existsFileEntry(record.getCell(0).getFilename()));
        assertTrue(archive.existsFileEntry(record.getCell(2).getFilename()));
        assertFalse(archive.existsFileEntry(table.getTableXml()));
        records.put(record);
        records.close();
        assertTrue(archive.existsFileEntry(table.getTableXml()));
        archive.close();
    }

    @Test
    void externalClobAndBlobStorageStillUsesRequestedFolder() throws Exception {
        Path file = write("external.siard", true, true);
        verify(file);
        try (var files = Files.walk(directory.resolve("external"))) {
            assertEquals((texts.size() - 1) * 2, files.filter(Files::isRegularFile).count());
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            assertFalse(zip.stream().anyMatch(e -> e.getName().endsWith(".bin")));
        }
    }

    @Test
    void failedReadersAndBinaryStreamsCloseAndAbortRemovesXmlSpool() throws Exception {
        for (boolean binary : new boolean[]{false, true}) {
            Set<Path> before = spools();
            ArchiveImpl archive = create("failed-" + binary + ".siard");
            TableImpl table = table(archive, "T");
            RecordRetainerImpl records = table.createRecordsStreamingLobs();
            Cell cell = records.create().getCell(binary ? 2 : 0);
            boolean[] closed = {false};
            IOException failure;
            if (binary) {
                InputStream source = new InputStream() {
                    int remaining = 9000;
                    public int read() throws IOException {
                        if (remaining-- <= 0) throw new IOException("source failed");
                        return 31;
                    }
                    public void close() { closed[0] = true; }
                };
                failure = assertThrows(IOException.class, () -> cell.setInputStream(source));
            } else {
                Reader source = new Reader() {
                    int remaining = 9000;
                    public int read(char[] c, int o, int n) throws IOException {
                        if (remaining <= 0) throw new IOException("source failed");
                        n = Math.min(n, remaining);
                        Arrays.fill(c, o, o + n, 'x');
                        remaining -= n;
                        return n;
                    }
                    public void close() { closed[0] = true; }
                };
                failure = assertThrows(IOException.class, () -> cell.setReader(source));
            }
            assertEquals("source failed", failure.getMessage());
            assertTrue(closed[0]);
            records.abort();
            assertEquals(before, spools());
            assertFalse(table.isCreating());
            archive.close(); // Release the failed archive; it is not a successful extraction.
        }
    }

    @Test
    void fragmentedAndZeroLengthReadsPreserveEveryBinaryByte() throws Exception {
        ArchiveImpl archive = create("fragments.siard");
        TableImpl table = table(archive, "T");
        RecordRetainerImpl records = table.createRecordsStreamingLobs();
        var record = records.create();
        byte[] wanted = bytes(5);
        boolean[] closed = {false};
        record.getCell(2).setInputStream(new ByteArrayInputStream(wanted) {
            int calls;
            @Override public synchronized int read(byte[] b, int o, int n) {
                return ++calls % 7 == 0 ? 0 : super.read(b, o, Math.min(n, 3));
            }
            @Override public void close() { closed[0] = true; }
        });
        assertTrue(closed[0]);
        records.put(record);
        records.close();
        archive.close();
        Archive reopened = ArchiveImpl.newInstance();
        reopened.open(directory.resolve("fragments.siard").toFile());
        RecordDispenser rows = reopened.getSchema(0).getTable(0).openRecords();
        Cell actual = rows.get().getCell(2);
        assertArrayEquals(wanted, actual.getBytes());
        assertEquals(wanted.length, actual.getByteLength());
        rows.close();
        assertTrue(reopened.isPrimaryDataUnchanged());
        reopened.close();
    }

    @Test
    void outputFailureKeepsItsCauseAndClosesBothStreams() throws Exception {
        Set<Path> before = spools();
        ArchiveImpl archive = spy((ArchiveImpl)ArchiveImpl.newInstance());
        archive.create(directory.resolve("output-failed.siard").toFile());
        archive.getMetaData().setDbName("FAILURE_TEST");
        archive.getMetaData().setDataOwner("Synthetic test");
        archive.getMetaData().setDataOriginTimespan("2026-10-22");
        archive.getMetaData().createMetaUser("TEST");
        archive.createSchema("S");
        TableImpl table = table(archive, "T");
        RecordRetainerImpl records = table.createRecordsStreamingLobs();
        boolean[] closed = {false, false};
        doAnswer(call -> {
            String name = call.getArgument(0);
            if (!name.endsWith(".bin")) return call.callRealMethod();
            return new OutputStream() {
                public void write(int b) throws IOException { throw new IOException("disk full"); }
                public void write(byte[] b, int o, int n) throws IOException { throw new IOException("disk full"); }
                public void close() throws IOException { closed[0] = true; throw new IOException("output close"); }
            };
        }).when(archive).createFileEntry(anyString());
        InputStream source = new ByteArrayInputStream(bytes(4)) {
            @Override public void close() throws IOException { closed[1] = true; throw new IOException("source close"); }
        };
        IOException error = assertThrows(IOException.class, () -> records.create().getCell(2).setInputStream(source));
        assertEquals("disk full", error.getMessage());
        assertEquals(2, error.getSuppressed().length);
        assertEquals("output close", error.getSuppressed()[0].getMessage());
        assertEquals("source close", error.getSuppressed()[1].getMessage());
        assertTrue(closed[0] && closed[1]);
        records.abort();
        assertEquals(before, spools());
        archive.close();
    }

    @Test
    void emptyAndConsecutiveTablesFinalizeIndependently() throws Exception {
        Set<Path> before = spools();
        ArchiveImpl archive = create("tables.siard");
        for (int i = 0; i < 3; i++) {
            TableImpl table = table(archive, "T" + i);
            RecordRetainerImpl records = table.createRecordsStreamingLobs();
            if (i != 1) {
                var row = records.create();
                row.getCell(0).setReader(new StringReader(texts.get(5)));
                records.put(row);
            }
            records.close();
        }
        archive.close();
        assertEquals(before, spools());
        Archive reopened = ArchiveImpl.newInstance();
        reopened.open(directory.resolve("tables.siard").toFile());
        for (int i = 0; i < 3; i++) {
            Table table = reopened.getSchema(0).getTable(i);
            assertEquals(i == 1 ? 0 : 1, table.getMetaTable().getRows());
            RecordDispenser rows = table.openRecords();
            if (i != 1) assertEquals(texts.get(5), rows.get().getCell(0).getString());
            rows.close();
        }
        assertTrue(reopened.isPrimaryDataUnchanged());
        reopened.close();
    }

    @Test
    void arrayElementsKeepIndependentLobPathsAndValues() throws Exception {
        ArchiveImpl archive = create("arrays.siard");
        TableImpl table = (TableImpl)archive.getSchema(0).createTable("ARRAYS");
        for (String type : new String[]{"CLOB", "BLOB"}) {
            MetaColumn column = table.getMetaTable().createMetaColumn(type + "_VALUES");
            column.setType(type);
            column.setCardinality(3);
        }
        RecordRetainerImpl records = table.createRecordsStreamingLobs();
        var row = records.create();
        row.getCell(0).getElement(0).setReader(new StringReader(texts.get(4)));
        row.getCell(0).getElement(1).setReader(new StringReader(texts.get(5)));
        row.getCell(1).getElement(0).setInputStream(new ByteArrayInputStream(bytes(4)));
        row.getCell(1).getElement(1).setInputStream(new ByteArrayInputStream(new byte[0]));
        assertNotEquals(row.getCell(0).getElement(0).getFilename(), row.getCell(0).getElement(1).getFilename());
        records.put(row);
        records.close();
        archive.close();
        Archive reopened = ArchiveImpl.newInstance();
        reopened.open(directory.resolve("arrays.siard").toFile());
        RecordDispenser reader = reopened.getSchema(0).getTable(0).openRecords();
        var actual = reader.get();
        assertEquals(texts.get(4), actual.getCell(0).getElement(0).getString());
        assertEquals(texts.get(5), actual.getCell(0).getElement(1).getString());
        assertArrayEquals(bytes(4), actual.getCell(1).getElement(0).getBytes());
        assertArrayEquals(new byte[0], actual.getCell(1).getElement(1).getBytes());
        reader.close();
        assertTrue(reopened.isPrimaryDataUnchanged());
        reopened.close();
    }

    private Path write(String name, boolean direct, boolean external) throws Exception {
        ArchiveImpl archive = create(name);
        TableImpl table = table(archive, "T");
        if (external) {
            for (int i : new int[]{0, 2}) {
                Path folder = Files.createDirectories(directory.resolve("external/c" + i));
                table.getMetaTable().getMetaColumn(i).setLobFolder(folder.toUri());
            }
        }
        RecordRetainer records = direct ? table.createRecordsStreamingLobs() : table.createRecords();
        for (int i = 0; i < texts.size(); i++) {
            var record = records.create();
            String text = texts.get(i);
            if (text != null) {
                record.getCell(0).setReader(new StringReader(text));
                record.getCell(1).setReader(new StringReader(text));
                record.getCell(2).setInputStream(new ByteArrayInputStream(bytes(i)));
                record.getCell(3).setReader(new StringReader("<law>한글 " + i + "</law>"));
            }
            records.put(record);
        }
        records.close();
        archive.close();
        return directory.resolve(name);
    }

    private void verify(Path file) throws Exception {
        Archive archive = ArchiveImpl.newInstance();
        archive.open(file.toFile());
        assertTrue(archive.isPrimaryDataUnchanged(), "Archive digest");
        Table table = archive.getSchema(0).getTable(0);
        assertEquals(texts.size(), table.getMetaTable().getRows());
        RecordDispenser records = table.openRecords();
        for (int i = 0; i < texts.size(); i++) {
            var row = records.get();
            for (int c = 0; c < 4; c++) {
                Cell cell = row.getCell(c);
                if (i == 0) { assertTrue(cell.isNull()); continue; }
                assertFalse(cell.isNull());
                assertNotNull(cell.getFilename(), "All non-NULL LOB cells must reference files");
                if (c == 2) {
                    try (InputStream input = cell.getInputStream()) {
                        assertArrayEquals(bytes(i), input.readAllBytes());
                    }
                    assertEquals(bytes(i).length, cell.getByteLength());
                } else {
                    String expected = c == 3 ? "<law>한글 " + i + "</law>" : texts.get(i);
                    StringWriter out = new StringWriter();
                    try (Reader input = cell.getReader()) { input.transferTo(out); }
                    assertEquals(expected, out.toString());
                    assertEquals(expected.length(), cell.getCharLength());
                }
            }
        }
        records.close();
        archive.close();
    }

    private ArchiveImpl create(String name) throws Exception {
        ArchiveImpl archive = (ArchiveImpl)ArchiveImpl.newInstance();
        archive.create(directory.resolve(name).toFile());
        archive.getMetaData().setDbName("LOB_STREAM_TEST");
        archive.getMetaData().setDataOwner("Synthetic test");
        archive.getMetaData().setDataOriginTimespan("2026-10-22");
        archive.getMetaData().createMetaUser("TEST");
        archive.createSchema("S");
        return archive;
    }

    private TableImpl table(Archive archive, String name) throws Exception {
        TableImpl table = (TableImpl)archive.getSchema(0).createTable(name);
        String[] types = {"CLOB", "NCLOB", "BLOB", "XML"};
        for (int i = 0; i < types.length; i++) table.getMetaTable().createMetaColumn("C" + i).setType(types[i]);
        return table;
    }

    private Set<Path> spools() throws IOException {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("siard_table_")).collect(Collectors.toSet());
        }
    }
}
