// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.api.*;
import ch.admin.bar.siard2.api.primary.ArchiveImpl;
import ch.enterag.utils.background.Progress;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StreamingExtractionFailureTest {
    @TempDir Path directory;

    @Test void unknownPreflightCountIsReplacedByActualWrittenRows() throws Exception {
        FakeJdbc jdbc = new FakeJdbc(false);
        Archive archive = archive("uncounted.siard");
        Table table = archive.getSchema(0).getTable(0);
        table.getMetaTable().setRows(0);
        table.getMetaTable().getMetaColumn(0).setType("VARCHAR(40)");
        PrimaryDataFromDb.newInstance(jdbc.connection,archive).download(null);
        assertEquals(2,table.getMetaTable().getRows());
        File file=archive.getFile(); archive.close();
        Archive reopened=ArchiveImpl.newInstance(); reopened.open(file);
        assertEquals(2,reopened.getSchema(0).getTable(0).getMetaTable().getRows());
        reopened.close();
    }

    @Test
    void tableMetadataIsReadOnceAndScalarValuesSurviveReaderReuse() throws Exception {
        FakeJdbc jdbc = new FakeJdbc(false);
        Archive archive = archive("scalar-success.siard");
        Table table = archive.getSchema(0).getTable(0);
        table.getMetaTable().getMetaColumn(0).setType("VARCHAR(40)");
        PrimaryDataFromDb.newInstance(jdbc.connection, archive).download(null);
        assertEquals(1, jdbc.metadataReads);
        assertTrue(jdbc.rowsClosed && jdbc.statementClosed && jdbc.rolledBack);
        File file = archive.getFile();
        archive.close();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
            String xml = new String(zip.getInputStream(zip.getEntry("content/schema0/table0/table0.xml"))
                    .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(xml.contains("행 1"));
            assertTrue(xml.contains("행 2"));
        }
    }

    @Test
    void queryFailureClosesStatementAndPreservesCleanupFailure() throws Exception {
        FakeJdbc jdbc = new FakeJdbc(false);
        jdbc.queryFailure = new SQLException("query failed");
        jdbc.statementCloseFailure = new SQLException("statement close failed");
        Archive archive = archive("query-failed.siard");
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> PrimaryDataFromDb.newInstance(jdbc.connection, archive).download(null));
        assertSame(jdbc.queryFailure, error.getCause());
        assertTrue(jdbc.statementClosed);
        assertArrayEquals(new Throwable[]{jdbc.statementCloseFailure}, jdbc.queryFailure.getSuppressed());
        archive.close();
    }

    @Test
    void scalarCancellationDoesNotCommitPartialRowCount() throws Exception {
        FakeJdbc jdbc = new FakeJdbc(false);
        Archive archive = archive("scalar-cancelled.siard");
        Table table = archive.getSchema(0).getTable(0);
        table.getMetaTable().getMetaColumn(0).setType("VARCHAR(40)");
        Progress progress = new Progress() {
            public boolean cancelRequested() { return jdbc.row > 1; }
            public void notifyProgress(int percent) {}
        };
        assertThrows(RuntimeException.class,
                () -> PrimaryDataFromDb.newInstance(jdbc.connection, archive).download(progress));
        assertEquals(2, table.getMetaTable().getRows(), "Cancelled table must not commit a partial row count");
        assertTrue(jdbc.rowsClosed && jdbc.statementClosed);
        archive.close();
    }

    @Test
    void readFailureFreesBlobClosesJdbcAndDiscardsScratchXml() throws Exception {
        Set<Path> before = spools();
        FakeJdbc jdbc = new FakeJdbc(true);
        Archive archive = archive("failed.siard");
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> PrimaryDataFromDb.newInstance(jdbc.connection, archive).download(null));
        Throwable source = error;
        while (source.getCause() != null) source = source.getCause();
        assertEquals("source failed", source.getMessage());
        assertEquals(1, source.getSuppressed().length);
        assertEquals("free failed", source.getSuppressed()[0].getMessage());
        assertEquals(1, jdbc.frees);
        assertEquals(2, jdbc.closedInputs); // MIME input and body input
        assertTrue(jdbc.rowsClosed && jdbc.statementClosed);
        assertFalse(jdbc.rolledBack);
        assertEquals(before, spools());
        archive.close();
    }

    @Test
    void cancellationAfterFirstLobDoesNotFinalizePartialTable() throws Exception {
        Set<Path> before = spools();
        FakeJdbc jdbc = new FakeJdbc(false);
        Archive archive = archive("cancelled.siard");
        Progress progress = new Progress() {
            public boolean cancelRequested() { return jdbc.frees > 0; }
            public void notifyProgress(int percent) {}
        };
        assertThrows(RuntimeException.class,
                () -> PrimaryDataFromDb.newInstance(jdbc.connection, archive).download(progress));
        assertEquals(1, jdbc.frees);
        assertTrue(jdbc.rowsClosed && jdbc.statementClosed);
        assertFalse(jdbc.rolledBack);
        assertEquals(before, spools());
        assertFalse(((ArchiveImpl)archive).existsFileEntry("content/schema0/table0/table0.xml"));
        archive.close();
    }

    private Archive archive(String name) throws Exception {
        Archive archive = ArchiveImpl.newInstance();
        archive.create(directory.resolve(name).toFile());
        archive.getMetaData().setDbName("LOB_FAILURE_TEST");
        archive.getMetaData().setDataOwner("Synthetic test");
        archive.getMetaData().setDataOriginTimespan("2026-10-22");
        archive.getMetaData().createMetaUser("TEST");
        Table table = archive.createSchema("S").createTable("T");
        table.getMetaTable().createMetaColumn("BODY").setType("BLOB");
        table.getMetaTable().setRows(2);
        return archive;
    }

    private Set<Path> spools() throws IOException {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("siard_table_")).collect(Collectors.toSet());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object unused(Method method) {
        if (method.getReturnType() == boolean.class) return false;
        if (method.getReturnType() == int.class) return 0;
        if (method.getReturnType() == long.class) return 0L;
        return null;
    }

    private static class FakeJdbc {
        int streams, closedInputs, frees, row, metadataReads;
        boolean rowsClosed, statementClosed, rolledBack;
        SQLException queryFailure, statementCloseFailure;
        Connection connection;
        Statement statement;
        ResultSet rows;

        FakeJdbc(boolean fail) {
            Blob blob = proxy(Blob.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getBinaryStream":
                        boolean body = ++streams % 2 == 0;
                        return new FilterInputStream(new ByteArrayInputStream(new byte[9000])) {
                            int chunks;
                            @Override public int read(byte[] b, int o, int n) throws IOException {
                                if (body && fail && chunks++ > 0) throw new IOException("source failed");
                                return in.read(b, o, Math.min(n, 4096));
                            }
                            @Override public void close() throws IOException { closedInputs++; super.close(); }
                        };
                    case "free":
                        frees++;
                        if (fail) throw new SQLException("free failed");
                        return null;
                    default: return unused(method);
                }
            });
            ResultSetMetaData columns = proxy(ResultSetMetaData.class, (p, method, args) ->
                    method.getName().equals("getColumnCount") ? 1 : unused(method));
            rows = proxy(ResultSet.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "next": return ++row <= 2;
                    case "getMetaData": metadataReads++; return columns;
                    case "getBlob": return blob;
                    case "getString": return "행 " + row;
                    case "getStatement": return statement;
                    case "close": rowsClosed = true; return null;
                    case "isClosed": return rowsClosed;
                    default: return unused(method);
                }
            });
            statement = proxy(Statement.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "executeQuery":
                        if (queryFailure != null) throw queryFailure;
                        return rows;
                    case "close":
                        statementClosed = true;
                        if (statementCloseFailure != null) throw statementCloseFailure;
                        return null;
                    default: return unused(method);
                }
            });
            DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (p, method, args) ->
                    method.getName().equals("getDatabaseProductName") ? "Oracle" : unused(method));
            connection = proxy(Connection.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getMetaData": return metadata;
                    case "createStatement": return statement;
                    case "rollback": rolledBack = true; return null;
                    default: return unused(method);
                }
            });
        }
    }
}
