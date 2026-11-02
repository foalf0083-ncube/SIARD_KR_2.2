// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.api.Value;
import java.io.CharArrayReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.Clob;
import java.sql.SQLException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/** Reads an Oracle CLOB once, replaying the MIME detector's prefix from memory. */
final class ClobTransfer {
    // Restrict driver-specific shortcuts to the binaries whose read/free paths
    // have been inspected and exercised. Other drivers retain JDBC cleanup.
    private static final String ORACLE_VERSION = oracle.jdbc.OracleDriver.getDriverVersion();
    private static final boolean LEGACY_ORACLE_ASCII =
            ORACLE_VERSION.equals("11.2.0.4.0");
    private static final boolean KNOWN_ORACLE = LEGACY_ORACLE_ASCII || ORACLE_VERSION.equals("12.1.0.1.0");
    private static final Method DATA_IN_LOCATOR = dataInLocatorMethod();
    private static final Field STRICT_ASCII = strictAsciiField();
    static final String PROPERTY_READ_BUFFER = "siard.oracle.clobReadChars";
    private static final int ORACLE_READ_BUFFER = readBufferChars();

    static int readBufferChars() {
        // Characters, not bytes. Bound the allocation even for an invalid deployment setting.
        return Math.max(8192, Math.min(1024 * 1024,
                Integer.getInteger(PROPERTY_READ_BUFFER, 64 * 1024)));
    }
    private ClobTransfer() {}

    static boolean supportsDirectOracleNClob() { return KNOWN_ORACLE; }

    static void copy(Value value, Clob clob, MimeTypeHandler mimeTypes)
            throws IOException, SQLException {
        copy(value, clob, mimeTypes, false);
    }

    static void copy(Value value, Clob clob, MimeTypeHandler mimeTypes, boolean copiedNClobAscii)
            throws IOException, SQLException {
        // Resource order closes the reader before freeing the locator, also on failure.
        // try-with-resources preserves the original error if cleanup fails as well.
        try (ClobResource resource = new ClobResource(clob);
             ClobReplayReader reader = replayReader(clob, copiedNClobAscii, mimeTypes, value)) {
            if (!mimeTypes.needsDetection(value)) {
                reader.replayPrefix();
            } else if (reader.capturing) {
                if (LEGACY_ORACLE_ASCII) {
                    try (InputStream ascii = new OracleAsciiStream(reader, mimeTypes)) {
                        mimeTypes.add(value, ascii);
                    }
                } else {
                    // Opening this stream does not read content. Its conversion
                    // flag is the effective CONNECTION setting, including system
                    // defaults; guessing from System properties alone is unsafe.
                    try (InputStream nativeAscii = openAsciiStream(clob)) {
                        Boolean strict = strictAscii(nativeAscii);
                        if (Boolean.FALSE.equals(strict)) {
                            try (InputStream ascii = new OracleAsciiStream(reader, mimeTypes)) {
                                mimeTypes.add(value, ascii);
                            }
                        } else {
                            // Strict conversion / no reflective access / unfamiliar
                            // stream: preserve Oracle's charset replacement rules.
                            // The outer scope owns/closes the underlying stream.
                            mimeTypes.add(value, new BufferedInputStream(mimeTypes.timings.source(nativeAscii)));
                        }
                    }
                }
                reader.replayPrefix();
            } else {
                // Oracle's in-locator CharArrayReader uses a different ASCII conversion.
                // Its ASCII stream is already in memory, so retain it along with the
                // other drivers' conversion and the LONG fallback.
                mimeTypes.add(value, clob);
            }
            long readsBefore = mimeTypes.timings.sourceReadNanos, start = mimeTypes.timings.now();
            try { value.setReader(reader); }
            finally { mimeTypes.timings.finishLobWrite(start, readsBefore); }
        }
    }

    private static ClobReplayReader replayReader(Clob clob, boolean copiedNClobAscii,
                                                MimeTypeHandler mimeTypes, Value value) throws SQLException {
        Reader source = openReader(clob);
        boolean capture = mimeTypes.needsDetection(value) && (nativeOracle(clob)
                || LEGACY_ORACLE_ASCII && clob instanceof oracle.sql.CLOB)
                // OracleResultSet.getNClob formerly copied the value into a
                // temporary remote NCLOB. Preserve that stream's MIME conversion
                // when bypassing the copy, even if the original is in-locator.
                && (copiedNClobAscii || !(source instanceof CharArrayReader));
        return new ClobReplayReader(mimeTypes.timings.source(source), capture);
    }

    private static boolean nativeOracle(Clob clob) {
        // Do not change ownership rules of user subclasses or wrapper CLOBs.
        return KNOWN_ORACLE && (clob.getClass() == oracle.sql.CLOB.class
                || clob.getClass() == oracle.sql.NCLOB.class);
    }

    private static Reader openReader(Clob clob) throws SQLException {
        if (nativeOracle(clob) && DATA_IN_LOCATOR != null) {
            try {
                if (!((Boolean) DATA_IN_LOCATOR.invoke(clob))) {
                    oracle.sql.CLOB oracle = (oracle.sql.CLOB) clob;
                    // Same native streaming reader, with a fixed bounded buffer.
                    // Avoid getBufferSize(), which queries each locator's chunk
                    // size on 10g. In-locator data keeps its zero-RPC reader.
                    return oracle.getDBAccess().newReader(oracle, ORACLE_READ_BUFFER, 0);
                }
            } catch (IllegalAccessException ignored) {
                // Optional optimization: ordinary JDBC remains functional.
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof SQLException) throw (SQLException)e.getCause();
                throw new SQLException("Unable to inspect Oracle CLOB cache", e.getCause());
            }
        }
        return clob.getCharacterStream();
    }

    private static InputStream openAsciiStream(Clob clob) throws SQLException {
        if (nativeOracle(clob)) {
            oracle.sql.CLOB oracle = (oracle.sql.CLOB)clob;
            return oracle.getDBAccess().newInputStream(oracle, 8192, 0);
        }
        return clob.getAsciiStream();
    }

    private static Method dataInLocatorMethod() {
        if (!KNOWN_ORACLE) return null;
        try {
            Method method = oracle.sql.CLOB.class.getDeclaredMethod("canReadBasicLobDataInLocator");
            return method.trySetAccessible() ? method : null;
        } catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }

    private static Field strictAsciiField() {
        if (!KNOWN_ORACLE || LEGACY_ORACLE_ASCII) return null;
        try {
            Class<?> stream = Class.forName("oracle.jdbc.driver.OracleClobInputStream", false,
                    oracle.sql.CLOB.class.getClassLoader());
            Field field = stream.getDeclaredField("asciiStrictConversion");
            return field.trySetAccessible() ? field : null;
        } catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }

    private static Boolean strictAscii(InputStream stream) {
        if (STRICT_ASCII == null || !STRICT_ASCII.getDeclaringClass().isInstance(stream)) return null;
        try { return STRICT_ASCII.getBoolean(stream); }
        catch (IllegalAccessException | RuntimeException e) { return null; }
    }

    private static final class ClobResource implements AutoCloseable {
        private final Clob clob;

        private ClobResource(Clob clob) { this.clob = clob; }

        @Override
        public void close() throws SQLException {
            // We never call CLOB.open(). Persistent read locators have no server
            // temporary allocation to free; the reader's close returns its buffers.
            // CLOB.free() otherwise calls isOpen(), costing one RPC per value.
            // isTemporary() examines locator flags locally in these two drivers.
            // SQL expressions/views can return temporary LOBs: ALWAYS free those.
            if (nativeOracle(clob)) {
                try {
                    if (!((oracle.sql.CLOB)clob).isTemporary()) return;
                } catch (SQLException classificationFailure) {
                    try { clob.free(); }
                    catch (SQLException cleanupFailure) { classificationFailure.addSuppressed(cleanupFailure); }
                    throw classificationFailure;
                }
            }
            clob.free();
        }
    }

    /** Matches OracleClobInputStream's low-byte conversion, including non-ASCII text. */
    private static final class OracleAsciiStream extends InputStream {
        private final Reader reader;
        private final char[] buffer;
        private final MimeTypeHandler mimeTypes;

        private OracleAsciiStream(Reader reader, MimeTypeHandler mimeTypes) {
            this.reader = reader;
            this.mimeTypes = mimeTypes;
            this.buffer = mimeTypes.borrowAsciiBuffer();
        }

        @Override
        public int read() throws IOException {
            int c = reader.read();
            return c < 0 ? -1 : c & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) return 0;
            int count = reader.read(buffer, 0, Math.min(length, buffer.length));
            for (int i = 0; i < count; i++) bytes[offset + i] = (byte) buffer[i];
            return count;
        }

        // This adapter borrows the reader; the enclosing resource scope owns it.
        @Override public void close() { mimeTypes.releaseAsciiBuffer(buffer); }
    }
}
