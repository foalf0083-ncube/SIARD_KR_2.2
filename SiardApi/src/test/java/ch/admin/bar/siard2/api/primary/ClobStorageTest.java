// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.primary;

import ch.admin.bar.siard2.api.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClobStorageTest {
    @TempDir Path directory;

    @Test
    void roundTripsNullEmptyUnicodeControlsBoundariesAndLargeClobs() throws Exception {
        List<String> values = Arrays.asList(null, "", "  한글 😀 & < > \\u0000\r\n\t\u0000  ",
                "x".repeat(3999), "한".repeat(4000), "x".repeat(4001),
                "문서".repeat(100000), "A\uffffB", "A\ufffeB");
        Path file = writeArchive("mixed.siard", values, false);
        assertEquals(values, readArchive(file));
        assertFileRepresentation(file, values);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            long lobFiles = zip.stream().filter(e -> e.getName().endsWith(".txt")).count();
            assertEquals(values.size() - 1, lobFiles,
                    "Every non-NULL CLOB, including empty and short values, needs a file");
            assertTrue(zip.stream().anyMatch(e -> e.getName().endsWith(".xsd") && e.getSize() > 0));
        }
    }

    @Test
    void externalLobSettingStillWritesShortAndEmptyValuesAsFiles() throws Exception {
        List<String> values = Arrays.asList(null, "", "짧은 본문");
        Path file = writeArchive("external.siard", values, true);
        assertEquals(values, readArchive(file));
        try (var paths = Files.walk(directory.resolve("external"))) {
            assertEquals(2, paths.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void readerIsClosedWhenReadingFails() throws Exception {
        Archive archive = ArchiveImpl.newInstance();
        archive.create(directory.resolve("failure.siard").toFile());
        initializeMetadata(archive);
        Table table = archive.createSchema("S").createTable("T");
        table.getMetaTable().createMetaColumn("BODY").setType("CLOB");
        RecordRetainer records = table.createRecords();
        boolean[] closed = {false};
        Reader reader = new Reader() {
            @Override public int read(char[] c, int o, int n) throws IOException { throw new IOException("read failed"); }
            @Override public void close() { closed[0] = true; }
        };
        assertThrows(IOException.class, () -> records.create().getCell(0).setReader(reader));
        assertTrue(closed[0]);
        records.close();
        archive.close();
    }

    @Test
    void readerFilesIgnoreInlineLimitAndPreserveFragmentedAndZeroLengthReads() throws Exception {
        for (String type : new String[]{"CLOB", "NCLOB", "XML"}) {
          for (boolean direct : new boolean[]{false, true}) {
            Path file = directory.resolve(type + "-" + direct + ".siard");
            Archive archive = ArchiveImpl.newInstance();
            archive.create(file.toFile());
            archive.setMaxInlineSize(65536);
            initializeMetadata(archive);
            Table table = archive.createSchema("S").createTable("T");
            table.getMetaTable().createMetaColumn("BODY").setType(type);
            List<String> values = Arrays.asList("", "<law>한글 😀\r\n조항</law>",
                    "한".repeat(4001), "법률 조항 😀\r\n".repeat(2000));
            RecordRetainer records = direct ? ((TableImpl)table).createRecordsStreamingLobs()
                    : table.createRecords();
            for (String value : values) {
                ch.admin.bar.siard2.api.Record record = records.create();
                record.getCell(0).setReader(new StringReader(value) {
                    private int calls;
                    @Override public int read(char[] buffer, int offset, int length) throws IOException {
                        return ++calls % 7 == 0 ? 0 : super.read(buffer, offset, Math.min(length, 3));
                    }
                });
                records.put(record);
            }
            records.close();
            archive.close();
            assertEquals(values, readArchive(file));
            assertFileRepresentation(file, values);
            try (ZipFile zip = new ZipFile(file.toFile())) {
                long lobFiles = zip.stream().filter(e -> e.getName().endsWith(".txt")
                        || e.getName().endsWith(".xml") && !e.getName().endsWith("metadata.xml")
                        && !e.getName().endsWith("table0.xml")).count();
                assertEquals(values.size(), lobFiles);
            }
          }
        }
    }

    /** Inspect ZIP/XML independently of the SIARD reader, including empty LOB files. */
    private void assertFileRepresentation(Path file, List<String> values) throws Exception {
        try (ZipFile zip = new ZipFile(file.toFile());
             InputStream xml = zip.getInputStream(zip.getEntry("content/schema0/table0/table0.xml"))) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var rows = factory.newDocumentBuilder().parse(xml).getElementsByTagNameNS("*", "row");
            assertEquals(values.size(), rows.getLength());
            for (int i = 0; i < values.size(); i++) {
                var cells = ((Element)rows.item(i)).getElementsByTagNameNS("*", "c1");
                String expected = values.get(i);
                if (expected == null) {
                    assertEquals(0, cells.getLength(), "NULL remains an omitted cell");
                    continue;
                }
                assertEquals(1, cells.getLength());
                Element cell = (Element)cells.item(0);
                assertEquals("", cell.getTextContent(), "CLOB data must not be inlined");
                assertEquals(String.valueOf(expected.length()), cell.getAttribute("length"));
                String reference = cell.getAttribute("file");
                assertTrue(reference.startsWith("content/schema0/table0/"));
                var entry = zip.getEntry(reference);
                assertNotNull(entry, "Missing LOB entry " + reference);
                byte[] actual;
                if (expected.isEmpty()) {
                    // The original ZIP writer uses a zero-byte deflated entry
                    // for an empty LOB. JDK's inflater rejects that legacy form;
                    // check its ZIP metadata here and its contents via readArchive.
                    assertEquals(0, entry.getSize());
                    assertEquals(0, entry.getCompressedSize());
                    assertEquals(0, entry.getCrc());
                    actual = new byte[0];
                } else {
                    try (InputStream input = zip.getInputStream(entry)) { actual = input.readAllBytes(); }
                }
                assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), actual);
                String digest = HexFormat.of().formatHex(
                        MessageDigest.getInstance(cell.getAttribute("digestType")).digest(actual));
                assertEquals(digest, cell.getAttribute("digest").toLowerCase(Locale.ROOT));
            }
        }
    }

    private Path writeArchive(String name, List<String> values, boolean external) throws Exception {
        Path file = directory.resolve(name);
        Archive archive = ArchiveImpl.newInstance();
        archive.create(file.toFile());
        initializeMetadata(archive);
        Table table = archive.createSchema("S").createTable("T");
        MetaColumn column = table.getMetaTable().createMetaColumn("BODY");
        column.setType("CLOB");
        if (external) {
            Path folder = Files.createDirectories(directory.resolve("external"));
            column.setLobFolder(folder.toUri());
        }
        RecordRetainer records = table.createRecords();
        for (String value : values) {
            ch.admin.bar.siard2.api.Record record = records.create();
            if (value != null) record.getCell(0).setReader(new StringReader(value));
            records.put(record);
        }
        records.close();
        archive.close();
        return file;
    }

    private void initializeMetadata(Archive archive) throws Exception {
        MetaData metadata = archive.getMetaData();
        metadata.setDbName("CLOB_TEST");
        metadata.setDataOwner("Synthetic test");
        metadata.setDataOriginTimespan("2026-10-21");
        metadata.createMetaUser("TEST");
    }

    private List<String> readArchive(Path file) throws Exception {
        Archive archive = ArchiveImpl.newInstance();
        archive.open(file.toFile());
        Table table = archive.getSchema(0).getTable(0);
        RecordDispenser records = table.openRecords();
        List<String> values = new ArrayList<>();
        for (long row = 0; row < table.getMetaTable().getRows(); row++) {
            Cell cell = records.get().getCell(0);
            if (cell.isNull()) values.add(null);
            else {
                StringWriter text = new StringWriter();
                try (Reader reader = cell.getReader()) { reader.transferTo(text); }
                values.add(text.toString());
            }
        }
        records.close();
        archive.close();
        return values;
    }
}
