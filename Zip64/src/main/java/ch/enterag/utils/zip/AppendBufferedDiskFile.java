// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.zip;

import ch.enterag.utils.io.DiskFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * DiskFile used by Zip64File for files opened for writing. It writes the same
 * bytes in the same order as DiskFile and removes two costs.
 *
 * <p>Write coalescing. Zip64File writes each 4 KiB of deflated output and every
 * header separately, and DiskFile passes each call to the operating system.
 * Contiguous writes are collected in a buffer. The buffer is written before any
 * read, seek, length change, move or close, so the file content is unchanged.</p>
 *
 * <p>Append digest. When a SIARD archive is closed, ArchiveImpl stores the MD5
 * digest of the primary data, i.e. of the file from offset 0 to its current end.
 * DiskFile computes it by reading the whole file again in 4 KiB steps. For a file
 * that starts empty, the MD5 of the bytes appended from offset 0 is maintained
 * while writing and returned for digest("MD5", 0, end) when the range matches.
 * Any write that is not an append, and any truncation below the digested end or
 * move, discards this value; the digest is then read from the file as before.
 * Both ways digest the same bytes, so the value is the same.</p>
 *
 * <p>Switches: -Dsiard.zip.writeBuffer=0 disables write coalescing,
 * -Dsiard.zip.appendDigest=false disables the append digest.</p>
 */
final class AppendBufferedDiskFile extends DiskFile {
    static final String PROPERTY_WRITE_BUFFER = "siard.zip.writeBuffer";
    static final String PROPERTY_APPEND_DIGEST = "siard.zip.appendDigest";
    private static final int DEFAULT_WRITE_BUFFER = 1 << 20;
    private static final int READ_BUFFER = 1 << 20;
    private static final String APPEND_DIGEST_ALGORITHM = "MD5";

    private final byte[] buffer;
    private final byte[] single = new byte[1];
    /** bytes in buffer; while positive, the physical file pointer stays at bufferStart. */
    private int buffered;
    private long bufferStart;
    /** MD5 of [0, digestEnd), or null once the file is no longer append-only. */
    private MessageDigest appendDigest;
    private long digestEnd;

    static DiskFile open(File file, boolean readOnly) throws FileNotFoundException {
        return readOnly ? new DiskFile(file, true) : new AppendBufferedDiskFile(file);
    }

    static DiskFile open(String fileName, boolean readOnly) throws FileNotFoundException {
        return readOnly ? new DiskFile(fileName, true) : new AppendBufferedDiskFile(new File(fileName));
    }

    private AppendBufferedDiskFile(File file) throws FileNotFoundException {
        super(file);
        int size = Integer.getInteger(PROPERTY_WRITE_BUFFER, DEFAULT_WRITE_BUFFER);
        buffer = size > 0 ? new byte[size] : null;
        if (Boolean.parseBoolean(System.getProperty(PROPERTY_APPEND_DIGEST, "true"))) {
            try {
                if (super.length() == 0) appendDigest = MessageDigest.getInstance(APPEND_DIGEST_ALGORITHM);
            } catch (IOException | NoSuchAlgorithmException e) {
                appendDigest = null;
            }
        }
    }

    private long position() throws IOException {
        return buffered > 0 ? bufferStart + buffered : super.getFilePointer();
    }

    private void flushBuffer() throws IOException {
        if (buffered > 0) {
            int count = buffered;
            buffered = 0;
            super.write(buffer, 0, count);
        }
    }

    @Override
    public void write(int b) throws IOException {
        single[0] = (byte) b;
        write(single, 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) return;
        long position = position();
        if (appendDigest != null) {
            if (position == digestEnd) {
                appendDigest.update(b, off, len);
                digestEnd += len;
            } else {
                appendDigest = null;
            }
        }
        try {
            if (buffer == null) {
                super.write(b, off, len);
                return;
            }
            if (buffered == 0) bufferStart = position;
            if (len > buffer.length - buffered) {
                flushBuffer();
                if (len >= buffer.length) {
                    super.write(b, off, len);
                    return;
                }
                bufferStart = position;
            }
            System.arraycopy(b, off, buffer, buffered, len);
            buffered += len;
        } catch (IOException | RuntimeException failure) {
            appendDigest = null;
            throw failure;
        }
    }

    @Override
    public long getFilePointer() throws IOException {
        return position();
    }

    @Override
    public long length() throws IOException {
        long length = super.length();
        return buffered > 0 ? Math.max(length, bufferStart + buffered) : length;
    }

    @Override
    public void seek(long pos) throws IOException {
        flushBuffer();
        super.seek(pos);
    }

    @Override
    public void setLength(long newLength) throws IOException {
        flushBuffer();
        super.setLength(newLength);
        if (newLength < digestEnd) appendDigest = null;
    }

    @Override
    public int read() throws IOException {
        flushBuffer();
        return super.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        flushBuffer();
        return super.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        flushBuffer();
        return super.read(b, off, len);
    }

    @Override
    public long move(long lSource, long lDestination) throws IOException {
        flushBuffer();
        appendDigest = null;
        return super.move(lSource, lDestination);
    }

    @Override
    public long lastIndexOf(byte[] bufPattern) throws IOException {
        flushBuffer();
        return super.lastIndexOf(bufPattern);
    }

    @Override
    public byte[] digest(String sAlgorithm, long lStart, long lEnd) throws IOException {
        flushBuffer();
        if (appendDigest != null && APPEND_DIGEST_ALGORITHM.equals(sAlgorithm) && lStart == 0 && lEnd == digestEnd) {
            try {
                return ((MessageDigest) appendDigest.clone()).digest();
            } catch (CloneNotSupportedException e) {
                // read the file below
            }
        }
        return readDigest(sAlgorithm, lStart, lEnd);
    }

    /** Same contract as DiskFile.digest, reading sequentially with a larger buffer. */
    private byte[] readDigest(String sAlgorithm, long lStart, long lEnd) throws IOException {
        if (!sAlgorithm.equals("MD5") && !sAlgorithm.equals("SHA-1")) {
            throw new IllegalArgumentException("Digest algorithm must be MD5 or SHA-1!");
        }
        long lFilePointer = super.getFilePointer();
        byte[] bufDigest = null;
        try {
            MessageDigest md = MessageDigest.getInstance(sAlgorithm);
            byte[] buf = new byte[(int) Math.max(1, Math.min(READ_BUFFER, lEnd - lStart))];
            super.seek(lStart);
            for (long lPosition = lStart; lPosition < lEnd; ) {
                int iLength = (int) Math.min(buf.length, lEnd - lPosition);
                int iRead = super.read(buf, 0, iLength);
                if (iRead <= 0) {
                    throw new IOException("Could not read " + iLength + " bytes at position " + lPosition);
                }
                md.update(buf, 0, iRead);
                lPosition += iRead;
            }
            bufDigest = md.digest();
        } catch (NoSuchAlgorithmException nsae) {
            System.err.println(nsae.getClass().getName() + ": " + nsae.getMessage());
        }
        super.seek(lFilePointer);
        return bufDigest;
    }

    @Override
    public void close() throws IOException {
        try {
            flushBuffer();
        } finally {
            super.close();
        }
    }
}
