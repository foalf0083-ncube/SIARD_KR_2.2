// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
/*== CentralDirectorySpool.java ========================================
Keeps central directory records of new entries in a temporary file.
------------------------------------------------------------------------
Usage:
  Zip64File spools the central directory records of new entries once
  the number of entries on the heap reaches siard.zip.spillEntries
  (default 100000, 0 = never). The records are the exact bytes the
  central directory will contain, in entry order, so copying the spool
  at close produces the same ZIP file as writing them from the heap.
======================================================================*/

package ch.enterag.utils.zip;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import ch.enterag.utils.io.DiskFile;

/**
 * Append-only store of central directory records with a compact index by
 * entry name (32-bit name hash and spool offset per entry, about 20 bytes
 * on the heap instead of a FileEntry with its name and map node).
 */
final class CentralDirectorySpool implements Closeable {
    static final String PROPERTY_SPOOL_DIRECTORY = "siard.zip.spoolDirectory";
    private static final int iFIXED_SIZE = 46;
    private static final int iBUFFER_SIZE = 1 << 20;
    /** 2^15 longs stay below the humongous object size of G1 regions from 1 MiB */
    private static final int iCHUNK_BITS = 15;
    private static final int iCHUNK_SIZE = 1 << iCHUNK_BITS;

    private final File m_file;
    private final RandomAccessFile m_raf;
    private final byte[] m_buf = new byte[iBUFFER_SIZE];
    private int m_iBuffered = 0;
    private long m_lFlushed = 0;
    /** spool offset and name hash of each record, by ordinal, in chunks */
    private long[][] m_offsets = new long[16][];
    private int[][] m_hashes = new int[16][];
    private int m_iRecords = 0;
    /** open addressing table of ordinal + 1 (0 = empty) */
    private int[] m_slots = new int[1 << 16];
    static final String PROPERTY_DISK_INDEX_ENTRIES = "siard.zip.diskIndexEntries";
    private final int diskThreshold = Math.max(0, Math.min(10_000_000,
            Integer.getInteger(PROPERTY_DISK_INDEX_ENTRIES, 1_000_000)));
    private DiskNameIndex diskIndex;

    private void spillIndex() throws IOException {
        DiskNameIndex candidate = new DiskNameIndex(m_file.toPath().getParent());
        try {
            for (int i = 0; i < m_iRecords; i++) candidate.add(hashOf(i), offset(i));
        } catch (IOException | RuntimeException | Error e) {
            try { candidate.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        diskIndex = candidate;
        m_offsets = null; m_hashes = null; m_slots = null;
    }

    boolean hasDiskIndex() { return diskIndex != null; }
    int cachedIndexPages() { return diskIndex == null ? 0 : diskIndex.cachedPages(); }

    private CentralDirectorySpool(File file) throws IOException {
        m_file = file;
        m_file.deleteOnExit();
        m_raf = new RandomAccessFile(m_file, "rw");
    }

    /**
     * creates a spool next to the ZIP file (the volume chosen for the output).
     *
     * @param fileZip ZIP file being written.
     * @return new empty spool.
     * @throws IOException if the temporary file cannot be created.
     */
    static CentralDirectorySpool create(File fileZip) throws IOException {
        File folder = fileZip.getAbsoluteFile().getParentFile();
        String configured = System.getProperty(PROPERTY_SPOOL_DIRECTORY);
        if (configured != null && !configured.isBlank()) folder = new File(configured);
        File file = File.createTempFile("siard_" + fileZip.getName() + ".", ".cdspool", folder);
        try {
            return new CentralDirectorySpool(file);
        } catch (IOException failure) {
            try { java.nio.file.Files.deleteIfExists(file.toPath()); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** temporary file holding the records */
    File getFile() {
        return m_file;
    }

    /** number of records */
    int size() {
        return m_iRecords;
    }

    /** total length of the records in bytes */
    long length() {
        return m_lFlushed + m_iBuffered;
    }

    static int hash(String sName) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < sName.length(); i++) {
            h ^= sName.charAt(i);
            h *= 0x100000001b3L;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (int) h ^ (int) (h >>> 32);
    }

    private long offset(int iOrdinal) throws IOException {
        if (diskIndex != null) return diskIndex.offset(iOrdinal);
        return m_offsets[iOrdinal >>> iCHUNK_BITS][iOrdinal & (iCHUNK_SIZE - 1)];
    }

    private int hashOf(int iOrdinal) throws IOException {
        if (diskIndex != null) return diskIndex.hash(iOrdinal);
        return m_hashes[iOrdinal >>> iCHUNK_BITS][iOrdinal & (iCHUNK_SIZE - 1)];
    }

    private void insertSlot(int[] slots, int iHash, int iOrdinal) {
        int iMask = slots.length - 1;
        int i = iHash & iMask;
        while (slots[i] != 0)
            i = (i + 1) & iMask;
        slots[i] = iOrdinal + 1;
    }

    /**
     * appends a record.
     *
     * @param sName   entry name of the record.
     * @param record  central directory record.
     * @throws IOException if the spool cannot be written.
     */
    void add(String sName, byte[] record) throws IOException {
        if (m_iRecords == Integer.MAX_VALUE - 1)
            throw new IOException("Too many central directory records!");
        if (diskIndex == null && m_iRecords >= diskThreshold) spillIndex();
        if (diskIndex != null) {
            long offset = length();
            write(record);
            diskIndex.add(hash(sName), offset);
            m_iRecords++;
            return;
        }
        // Check before appending bytes or allocating a doubled array. A signed
        // int shift used to overflow at 2^30 slots, producing NegativeArraySizeException.
        int capacity = slotCapacity(m_slots.length, (long) m_iRecords + 1);
        long lOffset = length();
        write(record);
        int iChunk = m_iRecords >>> iCHUNK_BITS;
        if (iChunk == m_offsets.length) {
            m_offsets = Arrays.copyOf(m_offsets, iChunk * 2);
            m_hashes = Arrays.copyOf(m_hashes, iChunk * 2);
        }
        if (m_offsets[iChunk] == null) {
            m_offsets[iChunk] = new long[iCHUNK_SIZE];
            m_hashes[iChunk] = new int[iCHUNK_SIZE];
        }
        int iHash = hash(sName);
        m_offsets[iChunk][m_iRecords & (iCHUNK_SIZE - 1)] = lOffset;
        m_hashes[iChunk][m_iRecords & (iCHUNK_SIZE - 1)] = iHash;
        if (capacity != m_slots.length) {
            int[] slots = new int[capacity];
            for (int iOrdinal = 0; iOrdinal < m_iRecords; iOrdinal++)
                insertSlot(slots, hashOf(iOrdinal), iOrdinal);
            m_slots = slots;
        }
        insertSlot(m_slots, iHash, m_iRecords);
        m_iRecords++;
    }

    /** Index arithmetic can be checked without allocating a huge table. */
    static int slotCapacity(int current, long records) throws IOException {
        if (records * 4L <= current * 3L) return current;
        if (current >= (1 << 30))
            throw new IOException("ZIP name index capacity exceeded (805306368 spooled entries); "
                    + "a disk-backed name index is required for this archive.");
        return current * 2;
    }

    /**
     * returns the record of a named entry.
     *
     * @param sName entry name.
     * @return record or null, if the spool has no entry of this name.
     * @throws IOException if the spool cannot be read.
     */
    byte[] find(String sName) throws IOException {
        int iHash = hash(sName);
        if (diskIndex != null) {
            for (long slot = diskIndex.firstSlot(iHash);; slot = diskIndex.nextSlot(slot)) {
                int ordinal = diskIndex.ordinal(slot);
                if (ordinal < 0) return null;
                if (diskIndex.hash(ordinal) == iHash) {
                    byte[] record = record(ordinal);
                    if (sName.equals(Zip64File.getCentralDirectoryEntry(record).getName())) return record;
                }
            }
        }
        int iMask = m_slots.length - 1;
        for (int i = iHash & iMask; m_slots[i] != 0; i = (i + 1) & iMask) {
            int iOrdinal = m_slots[i] - 1;
            if (hashOf(iOrdinal) == iHash) {
                byte[] record = record(iOrdinal);
                if (sName.equals(Zip64File.getCentralDirectoryEntry(record).getName()))
                    return record;
            }
        }
        return null;
    }

    /**
     * returns a record by ordinal.
     *
     * @param iOrdinal 0-based position of the record in the spool.
     * @return record.
     * @throws IOException if the spool cannot be read.
     */
    byte[] record(int iOrdinal) throws IOException {
        long lOffset = offset(iOrdinal);
        byte[] fixed = new byte[iFIXED_SIZE];
        read(lOffset, fixed);
        int iVariable = (fixed[28] & 0xFF) | (fixed[29] & 0xFF) << 8;
        iVariable += (fixed[30] & 0xFF) | (fixed[31] & 0xFF) << 8;
        iVariable += (fixed[32] & 0xFF) | (fixed[33] & 0xFF) << 8;
        byte[] record = Arrays.copyOf(fixed, iFIXED_SIZE + iVariable);
        byte[] variable = new byte[iVariable];
        read(lOffset + iFIXED_SIZE, variable);
        System.arraycopy(variable, 0, record, iFIXED_SIZE, iVariable);
        return record;
    }

    /**
     * appends all records to the ZIP file.
     *
     * @param df ZIP file positioned at the place of the records.
     * @throws IOException if an I/O error occurred.
     */
    void copyTo(DiskFile df) throws IOException {
        flush();
        m_raf.seek(0);
        for (long lRemaining = m_lFlushed; lRemaining > 0; ) {
            int iRead = m_raf.read(m_buf, 0, (int) Math.min(m_buf.length, lRemaining));
            if (iRead < 0)
                throw new IOException("Central directory spool is truncated!");
            df.write(m_buf, 0, iRead);
            lRemaining -= iRead;
        }
    }

    private void write(byte[] buf) throws IOException {
        if (m_iBuffered + buf.length > m_buf.length)
            flush();
        if (buf.length > m_buf.length) {
            m_raf.seek(m_lFlushed);
            m_raf.write(buf);
            m_lFlushed += buf.length;
        } else {
            System.arraycopy(buf, 0, m_buf, m_iBuffered, buf.length);
            m_iBuffered += buf.length;
        }
    }

    private void flush() throws IOException {
        if (m_iBuffered > 0) {
            m_raf.seek(m_lFlushed);
            m_raf.write(m_buf, 0, m_iBuffered);
            m_lFlushed += m_iBuffered;
            m_iBuffered = 0;
        }
    }

    private void read(long lOffset, byte[] buf) throws IOException {
        if (lOffset >= m_lFlushed)
            System.arraycopy(m_buf, (int) (lOffset - m_lFlushed), buf, 0, buf.length);
        else {
            if (lOffset + buf.length > m_lFlushed)
                flush();
            m_raf.seek(lOffset);
            m_raf.readFully(buf);
        }
    }

    /** closes and deletes the temporary file. */
    @Override
    public void close() throws IOException {
        try (RandomAccessFile file = m_raf; DiskNameIndex index = diskIndex) {
            // Both resources close even if the other reports an error.
        } finally {
            if (!m_file.delete() && m_file.exists())
                System.err.println("Central directory spool " + m_file + " could not be deleted!");
        }
    }
}
