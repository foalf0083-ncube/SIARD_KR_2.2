// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.primary;

import ch.admin.bar.siard2.api.Record;

import ch.admin.bar.siard2.api.RecordRetainer;
import ch.admin.bar.siard2.api.Table;
import ch.admin.bar.siard2.api.generated.table.RecordType;
import ch.enterag.utils.FU;
import ch.enterag.utils.jaxb.XMLStreamFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

@Slf4j
public class RecordRetainerImpl implements RecordRetainer {
    private static final int iBUFFER_SIZE = 8192;
    private static final String[] ATTRIBUTE_ORDER = {"file", "length", "digestType", "digest"};
    private Table _table = null;
    private CountingOutputStream _osXml = null;
    private XMLStreamWriter _xsw = null;
    private long _lRecord = -1L;
    private File _fileTemporaryLobFolder = null;
    private final boolean streamLobs;
    private Path xmlSpool;
    private DeflatedXmlSpool compressedXml;
    static final String PROPERTY_PRECOMPRESS = "siard.xml.precompress";
    static final String PROPERTY_ASYNC = "siard.xml.async";

    private static OutputStream xmlOutput(OutputStream output) {
        return Boolean.parseBoolean(System.getProperty(PROPERTY_ASYNC, "true"))
                ? new BoundedOutputStream(output) : output;
    }
    private long streamedLobFiles, streamedLobBytes;
    private long lastLobReport = System.nanoTime();
    private char[] lobChars;
    private byte[] lobBytes;
    private boolean charsBorrowed, bytesBorrowed;

    char[] borrowLobChars() {
        if (charsBorrowed) return new char[iBUFFER_SIZE];
        if (lobChars == null) lobChars = new char[iBUFFER_SIZE];
        charsBorrowed = true;
        return lobChars;
    }

    void releaseLobChars(char[] buffer) {
        if (buffer == lobChars) charsBorrowed = false;
    }

    byte[] borrowLobBytes() {
        if (bytesBorrowed) return new byte[iBUFFER_SIZE];
        if (lobBytes == null) lobBytes = new byte[iBUFFER_SIZE];
        bytesBorrowed = true;
        return lobBytes;
    }

    void releaseLobBytes(byte[] buffer) {
        if (buffer == lobBytes) bytesBorrowed = false;
    }

    private URI getTemporaryLobFolder() {
        return this._fileTemporaryLobFolder == null ? null : FU.toUri(this._fileTemporaryLobFolder);
    }

    private ArchiveImpl getArchiveImpl() {
        return (ArchiveImpl) this._table.getParentSchema().getParentArchive();
    }

    static XMLStreamWriter writeHeader(OutputStream osXml, Table table) throws IOException {
        XMLStreamWriter xsw = null;

        try {
            xsw = XMLStreamFactory.createStreamWriter(osXml);
            xsw.setDefaultNamespace("http://www.bar.admin.ch/xmlns/siard/2/table.xsd");
            xsw.setPrefix("xsi", "http://www.w3.org/2001/XMLSchema-instance");
            xsw.writeStartDocument("UTF-8", "1.0");
            xsw.writeCharacters("\n");
            xsw.writeStartElement("table");
            xsw.writeNamespace(null, "http://www.bar.admin.ch/xmlns/siard/2/table.xsd");
            xsw.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            xsw.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "schemaLocation", "http://www.bar.admin.ch/xmlns/siard/2/table.xsd " + table.getMetaTable().getFolder() + ".xsd");
            xsw.writeAttribute("version", "2.2");
            return xsw;
        } catch (XMLStreamException var4) {
            XMLStreamException xse = var4;
            throw new IOException("Start of document could not be written!", xse);
        }
    }

    public RecordRetainerImpl(Table table) throws IOException {
        this(table, false);
    }

    RecordRetainerImpl(Table table, boolean streamLobs) throws IOException {
        this.streamLobs = streamLobs;
        TableImpl ti = (TableImpl) table;
        ti.setCreating(true);
        this._table = table;
        if (this.getArchiveImpl().canModifyPrimaryData()) {
            if (!this.getArchiveImpl().existsFolderEntry(ti.getTableFolder())) {
                this.getArchiveImpl().createFolderEntry(ti.getTableFolder());
            }

            this._lRecord = 0L;
            try {
                if (streamLobs) {
                    // ZIP permits one open entry. Spooling XML keeps that entry
                    // available for each LOB, without creating per-value files.
                    xmlSpool = Files.createTempFile("siard_table_", ".xml");
                    OutputStream output;
                    if (Boolean.parseBoolean(System.getProperty(PROPERTY_PRECOMPRESS, "true"))) {
                        compressedXml = new DeflatedXmlSpool(xmlSpool);
                        output = compressedXml;
                    } else output = new BufferedOutputStream(Files.newOutputStream(xmlSpool), 65536);
                    this._osXml = new CountingOutputStream(xmlOutput(output));
                } else {
                    this._fileTemporaryLobFolder = Files.createTempDirectory("siard_lobs_").toFile();
                    this._fileTemporaryLobFolder.deleteOnExit();
                    this._osXml = new CountingOutputStream(xmlOutput(this.getArchiveImpl().createFileEntry(ti.getTableXml())));
                }
                this._xsw = writeHeader(this._osXml, this._table);
            } catch (IOException | RuntimeException | Error failure) {
                try { abort(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        } else {
            throw new IOException("Table cannot be opened for writing!");
        }
    }

    private static void indent(XMLStreamWriter xsw, int depth) throws XMLStreamException {
        xsw.writeCharacters("\n");
        for (int i = 0; i < depth; i++) {
            xsw.writeCharacters("  ");
        }
    }

    private static void putRowElement(Element el, XMLStreamWriter xsw, int depth) throws XMLStreamException {
        indent(xsw, depth);
        xsw.writeStartElement(el.getLocalName());

        // 먼저 순서가 정해진 속성들을 쓰기
        for (String attrName : ATTRIBUTE_ORDER) {
            if (el.hasAttribute(attrName)) {
                xsw.writeAttribute(attrName, el.getAttribute(attrName));
            }
        }
        
        // 나머지 속성들을 쓰기 (순서가 정해지지 않은 것들)
        int i;
        for (i = 0; i < el.getAttributes().getLength(); ++i) {
            Attr attr = (Attr) el.getAttributes().item(i);
            String name = attr.getName();
            boolean alreadyWritten = "file".equals(name) || "length".equals(name)
                    || "digestType".equals(name) || "digest".equals(name);
            if (!alreadyWritten) {
                xsw.writeAttribute(attr.getName(), attr.getValue());
            }
        }

        for (i = 0; i < el.getChildNodes().getLength(); ++i) {
            Node node = el.getChildNodes().item(i);
            if (node.getNodeType() == 1) {
                putRowElement((Element) node, xsw, depth + 1);
            } else if (node.getNodeType() == 3) {
                xsw.writeCharacters(node.getTextContent());
            }
        }

        xsw.writeEndElement();
    }

    private static void putRecordType(RecordType rt, XMLStreamWriter xsw) throws XMLStreamException {
        indent(xsw, 1);
        xsw.writeStartElement("row");

        for (int i = 0; i < rt.getAny().size(); ++i) {
            putRowElement((Element) rt.getAny().get(i), xsw, 2);
        }

        indent(xsw, 1);
        xsw.writeEndElement();
    }

    static void writeRecord(Record record, XMLStreamWriter xsw) throws IOException, XMLStreamException {
        putRecordType(((RecordImpl) record).getRecordType(), xsw);
    }

    public void put(Record record) throws IOException {
        try {
            writeRecord(record, this._xsw);
            ++this._lRecord;
        } catch (XMLStreamException var3) {
            XMLStreamException xse = var3;
            throw new IOException("Error writing record " + this._lRecord + "!", xse);
        }
    }

    OutputStream openLob(String folder, String filename) throws IOException {
        if (!streamLobs || _xsw == null) throw new IOException("Streaming record writer is not open");
        ArchiveImpl archive = getArchiveImpl();
        if (!archive.existsFolderEntry(folder)) archive.createFolderEntry(folder);
        return new FilterOutputStream(archive.createFileEntry(folder + filename)) {
            private long bytes;
            private boolean closed;

            @Override public void write(int b) throws IOException {
                out.write(b);
                bytes++;
            }

            @Override public void write(byte[] buffer, int offset, int length) throws IOException {
                out.write(buffer, offset, length);
                bytes += length;
            }

            @Override public void close() throws IOException {
                if (closed) return;
                closed = true;
                out.close();
                streamedLobFiles++;
                streamedLobBytes += bytes;
                long now = System.nanoTime();
                if (now - lastLobReport >= 10_000_000_000L) {
                    log.info("LOBs written directly: {} files, {} bytes", streamedLobFiles, streamedLobBytes);
                    lastLobReport = now;
                }
            }
        };
    }

    /** Release extraction scratch resources without committing a partial table. */
    public void abort() throws IOException {
        IOException failure = null;
        if (_xsw != null) {
            try { _xsw.close(); } catch (XMLStreamException e) { failure = new IOException(e); }
            _xsw = null;
        }
        if (_osXml != null) {
            try { _osXml.close(); } catch (IOException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }
        if (xmlSpool != null) {
            try { Files.deleteIfExists(xmlSpool); xmlSpool = null; } catch (IOException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }
        if (_fileTemporaryLobFolder != null) {
            // Scalar extraction creates an empty scratch directory. Do not erase
            // staged LOBs if a caller used the legacy writer and needs recovery.
            try { Files.deleteIfExists(_fileTemporaryLobFolder.toPath()); }
            catch (java.nio.file.DirectoryNotEmptyException retained) { /* retain staged values */ }
            catch (IOException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }
        ((TableImpl)_table).setCreating(false);
        _lRecord = -1;
        if (failure != null) throw failure;
    }

    private void copyLobFiles(Path temporaryFolder, String internalFolder, LobCopyProgress progress) throws IOException {
        if (!Files.exists(temporaryFolder)) return;
        // Iterate incrementally: a table can have hundreds of thousands of LOB files.
        try (DirectoryStream<Path> files = Files.newDirectoryStream(temporaryFolder)) {
            boolean folderCreated = false;
            for (Path file : files) {
                String internalFile = internalFolder + file.getFileName();
                if (Files.isDirectory(file)) {
                    copyLobFiles(file, internalFile + "/", progress);
                } else {
                    if (!folderCreated) {
                        if (!getArchiveImpl().existsFolderEntry(internalFolder))
                            getArchiveImpl().createFolderEntry(internalFolder);
                        folderCreated = true;
                    }
                    try (InputStream input = Files.newInputStream(file);
                         OutputStream output = getArchiveImpl().createFileEntry(internalFile)) {
                        for (int count; (count = input.read(progress.buffer)) != -1;) {
                            output.write(progress.buffer, 0, count);
                            progress.bytes += count;
                        }
                    }
                    Files.delete(file);
                    progress.files++;
                    long now = System.nanoTime();
                    if (now - progress.lastReport >= 10_000_000_000L) {
                        log.info("Packing LOB files: {} files, {} bytes, {} seconds", progress.files,
                                progress.bytes, (now - progress.started) / 1_000_000_000L);
                        progress.lastReport = now;
                    }
                }
            }
        }
        Files.delete(temporaryFolder);
    }

    private static final class LobCopyProgress {
        final byte[] buffer = new byte[65536];
        final long started = System.nanoTime();
        long lastReport = started;
        long files, bytes;
    }

    static void writeFooter(XMLStreamWriter xsw) throws IOException {
        try {
            xsw.writeCharacters("\n");
            xsw.writeEndDocument();
        } catch (XMLStreamException var2) {
            XMLStreamException xse = var2;
            throw new IOException("End of document could not be written!", xse);
        }
    }

    public void close() throws IOException {
        try {
            if (this._xsw == null) {
                throw new IOException("Table records have not been created!");
            }

            TableImpl ti = (TableImpl) this._table;
            writeFooter(this._xsw);
            this._xsw.close();
            this._osXml.close();
            this._xsw = null;
            this._table.getMetaTable().setRows(this._lRecord);
            if (streamLobs) {
                long start = System.nanoTime();
                log.info("Finishing table XML: {} bytes; LOBs already written: {} files, {} bytes",
                        _osXml.getByteCount(), streamedLobFiles, streamedLobBytes);
                if (compressedXml != null) {
                    getArchiveImpl().appendDeflatedFileEntry(ti.getTableXml(), xmlSpool,
                            compressedXml.size(), compressedXml.crc());
                } else {
                    try (InputStream input = Files.newInputStream(xmlSpool);
                         OutputStream output = getArchiveImpl().createFileEntry(ti.getTableXml())) {
                        byte[] buffer = new byte[65536];
                        for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
                    }
                }
                Files.delete(xmlSpool);
                xmlSpool = null;
                log.info("Table XML finished: {} ms", (System.nanoTime() - start) / 1_000_000L);
            }
            try (OutputStream osXsd = this.getArchiveImpl().createFileEntry(ti.getTableXsd())) {
                this._table.exportTableSchema(osXsd);
            }
            if (!streamLobs) {
            LobCopyProgress copyProgress = new LobCopyProgress();
            log.info("Packing LOB files for table {}", ti.getTableFolder());
            this.copyLobFiles(FU.fromUri(this.getTemporaryLobFolder()).toPath(), ti.getTableFolder(), copyProgress);
            log.info("LOB packing finished: {} files, {} bytes, {} ms", copyProgress.files,
                    copyProgress.bytes, (System.nanoTime() - copyProgress.started) / 1_000_000L);
            }
        } catch (XMLStreamException var3) {
            XMLStreamException xse = var3;
            throw new IOException("XMLStreamWriter could not be closed!", xse);
        }

        ((TableImpl) this._table).setCreating(false);
        this._lRecord = -1L;
    }

    public Record create() throws IOException {
        Record record = RecordImpl.newInstance(this._table, this.getPosition(), this.getTemporaryLobFolder());
        if (streamLobs) ((RecordImpl)record).setStreamingLobWriter(this);
        return record;
    }

    public long getPosition() {
        return this._lRecord;
    }

    public long getByteCount() {
        return this._osXml.getByteCount();
    }

    private class CountingOutputStream extends OutputStream {
        private OutputStream _os = null;
        private long _lCount = 0L;

        public CountingOutputStream(OutputStream os) {
            this._os = os;
        }

        public void write(int b) throws IOException {
            this._os.write(b);
            ++this._lCount;
        }

        public void write(byte[] buf) throws IOException {
            this._os.write(buf);
            this._lCount += buf.length;
        }

        public void write(byte[] buf, int iOffset, int iLength) throws IOException {
            this._os.write(buf, iOffset, iLength);
            this._lCount += iLength;
        }

        public void close() throws IOException {
            this._os.close();
        }

        public long getByteCount() {
            return this._lCount;
        }
    }
}
