// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.primary;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/** One raw DEFLATE stream, using the same level and flush policy as Zip64File. */
final class DeflatedXmlSpool extends OutputStream {
    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    private final CRC32 crc = new CRC32();
    private final DeflaterOutputStream output;
    private long size;
    private boolean closed;

    DeflatedXmlSpool(Path path) throws IOException {
        try {
            output = new DeflaterOutputStream(new BufferedOutputStream(Files.newOutputStream(path), 65536),
                    deflater, 4096);
        } catch (IOException | RuntimeException | Error failure) {
            deflater.end();
            throw failure;
        }
    }

    @Override public void write(int b) throws IOException { write(new byte[]{(byte)b}, 0, 1); }
    @Override public void write(byte[] b, int off, int len) throws IOException {
        if (closed) throw new IOException("XML compression spool is closed");
        output.write(b, off, len);
        crc.update(b, off, len);
        size = Math.addExact(size, len);
    }
    @Override public void flush() throws IOException { output.flush(); }
    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try { output.close(); } finally { deflater.end(); }
    }
    long size() { return size; }
    long crc() { return crc.getValue(); }
}
