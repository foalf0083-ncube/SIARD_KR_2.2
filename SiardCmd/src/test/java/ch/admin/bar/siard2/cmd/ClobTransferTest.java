// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.api.Cell;
import ch.admin.bar.siard2.api.MetaColumn;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClobTransferTest {
    private static final Tika TIKA = new Tika();

    @Test void configuredReadBufferHasBoundedCharacterCount() {
        try {
            System.setProperty(ClobTransfer.PROPERTY_READ_BUFFER,"131072");
            assertEquals(131072,ClobTransfer.readBufferChars());
            System.setProperty(ClobTransfer.PROPERTY_READ_BUFFER,"-1");
            assertEquals(8192,ClobTransfer.readBufferChars());
            System.setProperty(ClobTransfer.PROPERTY_READ_BUFFER,"2147483647");
            assertEquals(1048576,ClobTransfer.readBufferChars());
        } finally { System.clearProperty(ClobTransfer.PROPERTY_READ_BUFFER); }
    }

    @Test
    void skipsFurtherDetectionAfterColumnMimeIsPermanentlyMixed() throws Exception {
        int[] detections = {0};
        MimeTypeHandler handler = new MimeTypeHandler(new Tika() {
            @Override public String detect(InputStream input) throws IOException {
                if (++detections[0] > 2) throw new IOException("unnecessary detection");
                return detections[0] == 1 ? "text/plain" : "application/xml";
            }
        });
        for (int i=0; i<4; i++) {
            OracleClob clob = new OracleClob("content " + i);
            Sink sink = new Sink();
            ClobTransfer.copy(sink.cell, clob, handler);
            handler.applyMimeType(sink.cell);
            assertEquals("content " + i, sink.text);
            assertEquals(i == 0 ? "text/plain" : "", sink.mime);
            assertTrue(clob.freed);
            assertEquals(1, clob.reader.closeCount);
            if (i > 1) assertEquals(0, clob.asciiOpenCount);
        }
        assertEquals(2, detections[0]);
    }

    @Test
    void oracleReadsOnceAndPreservesTextAndLegacyMime() throws Exception {
        for (String text : new String[]{"", "plain text", "한글 조례 😀 \\u0000\r\n",
                "<?xml version=\"1.0\"?><law>한글</law>", "<html><body>law</body></html>",
                "x".repeat(4000), "한".repeat(4001), "<law>" + "a".repeat(200000) + "</law>"}) {
            OracleClob clob = new OracleClob(text);
            Sink sink = new Sink();
            MimeTypeHandler handler = new MimeTypeHandler(TIKA);
            ClobTransfer.copy(sink.cell, clob, handler);
            handler.applyMimeType(sink.cell);
            byte[] legacyAscii = new byte[text.length()];
            for (int i = 0; i < text.length(); i++) legacyAscii[i] = (byte)text.charAt(i);
            assertEquals(text, sink.text);
            assertEquals(TIKA.detect(new ByteArrayInputStream(legacyAscii)), sink.mime);
            assertEquals(text.length(), clob.reader.charactersRead);
            assertEquals(1, clob.reader.closeCount);
            assertEquals(1, clob.openCount);
            boolean legacy = oracle.jdbc.OracleDriver.getDriverVersion().startsWith("11.2.");
            assertEquals(legacy ? 0 : 1, clob.asciiOpenCount);
            assertEquals(clob.asciiOpenCount, clob.asciiCloseCount);
            assertTrue(clob.freed);
        }
    }

    @Test
    void closesReaderAndFreesClobWhenWritingFailsAndKeepsOriginalError() {
        OracleClob clob = new OracleClob("sample");
        clob.failFree = true;
        Sink sink = new Sink();
        sink.failWrite = true;
        IOException error = assertThrows(IOException.class,
                () -> ClobTransfer.copy(sink.cell, clob, new MimeTypeHandler(TIKA)));
        assertEquals("write failed", error.getMessage());
        assertEquals(1, error.getSuppressed().length);
        assertEquals("free failed", error.getSuppressed()[0].getMessage());
        assertEquals(1, clob.reader.closeCount);
        assertTrue(clob.freed);
    }

    @Test
    void preservesOracleInLocatorAsciiConversionWithoutReReadingDatabase() throws Exception {
        String text = "한글 설명 및 고시\r\n① 가상 조항 😀";
        byte[] ascii = oracle.sql.CharacterSet.make(1).convertWithReplacement(text);
        int[] closes = {0, 0};
        OracleClob clob = new OracleClob(text) {
            @Override public Reader getCharacterStream() {
                return new CharArrayReader(text.toCharArray()) {
                    @Override public void close() { closes[0]++; super.close(); }
                };
            }
            @Override public InputStream getAsciiStream() {
                return new ByteArrayInputStream(ascii) {
                    @Override public void close() { closes[1]++; }
                };
            }
        };
        Sink sink = new Sink();
        MimeTypeHandler handler = new MimeTypeHandler(TIKA);
        ClobTransfer.copy(sink.cell, clob, handler);
        handler.applyMimeType(sink.cell);
        assertEquals(text, sink.text);
        assertEquals(TIKA.detect(new ByteArrayInputStream(ascii)), sink.mime);
        assertArrayEquals(new int[]{1, 1}, closes);
        assertTrue(clob.freed);
    }

    @Test
    void optionalTimingsPreserveTextMimeAndResourceCleanup() throws Exception {
        String text = "법률 조항 😀 & < >\r\n".repeat(1000);
        OracleClob clob = new OracleClob(text);
        Sink sink = new Sink();
        TransferTimings timings = new TransferTimings(true);
        MimeTypeHandler handler = new MimeTypeHandler(TIKA, timings);
        ClobTransfer.copy(sink.cell, clob, handler);
        handler.applyMimeType(sink.cell);
        assertEquals(text, sink.text);
        assertTrue(timings.sourceReadCalls > 0);
        assertTrue(timings.sourceReadNanos > 0);
        assertTrue(timings.mimeNanos >= 0 && timings.lobWriteNanos >= 0);
        assertEquals(1, clob.reader.closeCount);
        assertTrue(clob.freed);
    }

    @Test
    void closesReaderAndLocatorWhenMimeDetectionFails() {
        OracleClob clob = new OracleClob("sample");
        Tika broken = new Tika() {
            @Override public String detect(InputStream input) throws IOException {
                input.read();
                throw new IOException("detect failed");
            }
        };
        assertThrows(IOException.class,
                () -> ClobTransfer.copy(new Sink().cell, clob, new MimeTypeHandler(broken)));
        assertEquals(1, clob.reader.closeCount);
        assertTrue(clob.freed);
    }

    @Test
    void freesLocatorWhenOpeningReaderFails() {
        OracleClob clob = new OracleClob("sample");
        clob.failOpen = true;
        assertThrows(SQLException.class,
                () -> ClobTransfer.copy(new Sink().cell, clob, new MimeTypeHandler(TIKA)));
        assertTrue(clob.freed);
    }

    @Test
    void preservesOtherDriverAndLongFallbackAndClosesAsciiStream() throws Exception {
        for (boolean unsupported : new boolean[]{false, true}) {
            CountingReader reader = new CountingReader("<law>sample</law>");
            boolean[] freed = {false};
            boolean[] asciiClosed = {false};
            Clob clob = (Clob)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Clob.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getCharacterStream": return reader;
                            case "getAsciiStream":
                                if (unsupported) throw new SQLFeatureNotSupportedException();
                                return new ByteArrayInputStream("<law>sample</law>".getBytes(StandardCharsets.US_ASCII)) {
                                    @Override public void close() { asciiClosed[0] = true; }
                                };
                            case "free": freed[0] = true; return null;
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    });
            Sink sink = new Sink();
            MimeTypeHandler handler = new MimeTypeHandler(TIKA);
            ClobTransfer.copy(sink.cell, clob, handler);
            handler.applyMimeType(sink.cell);
            assertEquals("<law>sample</law>", sink.text);
            assertEquals(unsupported ? "text/plain" : TIKA.detect(
                    new ByteArrayInputStream("<law>sample</law>".getBytes(StandardCharsets.US_ASCII))), sink.mime);
            assertEquals(!unsupported, asciiClosed[0]);
            assertEquals(1, reader.closeCount);
            assertTrue(freed[0]);
        }
    }

    private static class OracleClob extends oracle.sql.CLOB {
        final CountingReader reader;
        int openCount, asciiOpenCount, asciiCloseCount;
        final String text;
        boolean freed, failFree, failOpen;
        OracleClob(String text) { this.text = text; reader = new CountingReader(text); }
        @Override public Reader getCharacterStream() throws SQLException {
            openCount++;
            if (failOpen) throw new SQLException("open failed");
            return reader;
        }
        @Override public InputStream getAsciiStream() throws SQLException {
            asciiOpenCount++;
            byte[] ascii = new byte[text.length()];
            for (int i = 0; i < text.length(); i++) ascii[i] = (byte)text.charAt(i);
            return new ByteArrayInputStream(ascii) {
                @Override public void close() { asciiCloseCount++; }
            };
        }
        @Override public void free() throws SQLException {
            freed = true;
            if (failFree) throw new SQLException("free failed");
        }
    }

    private static class CountingReader extends StringReader {
        int charactersRead, closeCount;
        CountingReader(String text) { super(text); }
        @Override public int read(char[] chars, int offset, int length) throws IOException {
            int n = super.read(chars, offset, length);
            if (n > 0) charactersRead += n;
            return n;
        }
        @Override public void close() { closeCount++; super.close(); }
    }

    private static class Sink {
        String text, mime;
        boolean failWrite;
        final MetaColumn column = (MetaColumn)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{MetaColumn.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "BODY";
                    if (method.getName().equals("setMimeType")) { mime = (String)args[0]; return null; }
                    throw new UnsupportedOperationException(method.getName());
                });
        final Cell cell = (Cell)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Cell.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getMetaColumn")) return column;
                    if (method.getName().equals("setReader")) {
                        if (failWrite) throw new IOException("write failed");
                        try (Reader reader = (Reader)args[0]) {
                            StringWriter out = new StringWriter();
                            reader.transferTo(out);
                            text = out.toString();
                        }
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
