// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.api.*;
import ch.admin.bar.siard2.api.primary.ArchiveImpl;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MetadataCountTest {
    @TempDir Path dir;
    private Archive archive(String name) throws IOException {
        Archive archive=ArchiveImpl.newInstance();archive.create(dir.resolve(name).toFile());
        archive.getMetaData().setDbName("COUNT_TEST");
        archive.getMetaData().setDataOwner("Synthetic count test");
        archive.getMetaData().setDataOriginTimespan("2026-10-27");
        return archive;
    }
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);
    }
    static class Database {
        int queries;
        boolean statementClosed, rowsClosed, fail;
        Connection conn;
        DatabaseMetaData metadata;
        Database() {
            ResultSet result=proxy(ResultSet.class,(p,m,a)->{
                switch(m.getName()) {
                    case "next":return true;
                    case "getLong":return 7L;
                    case "getMetaData":return null;
                    case "close":rowsClosed=true;return null;
                    default:throw new UnsupportedOperationException(m.getName());
                }
            });
            Statement statement=proxy(Statement.class,(p,m,a)->{
                switch(m.getName()) {
                    case "setQueryTimeout":return null;
                    case "executeQuery":
                        assertTrue(((String)a[0]).startsWith("SELECT COUNT(*) AS RECORDS"));
                        queries++;
                        if(fail)throw new SQLException("COUNT failed");
                        return result;
                    case "close":statementClosed=true;return null;
                    default:throw new UnsupportedOperationException(m.getName());
                }
            });
            conn=proxy(Connection.class,(p,m,a)->{
                switch(m.getName()) {
                    case "getMetaData":return metadata;
                    case "createArrayOf":throw new SQLFeatureNotSupportedException();
                    case "createStatement":return statement;
                    default:throw new UnsupportedOperationException(m.getName());
                }
            });
            metadata=proxy(DatabaseMetaData.class,(p,m,a)->{
                switch(m.getName()) {
                    case "getDatabaseProductName":return "Oracle";
                    case "getConnection":return conn;
                    default:throw new UnsupportedOperationException(m.getName());
                }
            });
        }
    }
    @Test void optionalCountSkipsSqlButRequiredLobScanStillRuns() throws Exception {
        Archive archive=archive("count.siard");
        try (Closeable close=archive::close) {
            MetaTable table=archive.createSchema("S").createTable("T").getMetaTable();
            table.createMetaColumn("ID").setType("INTEGER");
            table.setRows(0);
            Database db=new Database();
            MetaDataFromDb metadata=MetaDataFromDb.newInstance(db.metadata,archive.getMetaData());
            metadata.setCountRows(false);metadata.collectRows(table);
            assertEquals(0,db.queries);assertEquals(0,table.getRows());
            java.lang.reflect.Field maximum=MetaDataFromDb.class.getDeclaredField("_bMaxLobNeeded");
            maximum.setAccessible(true);maximum.setBoolean(metadata,true);
            metadata.collectRows(table);
            assertEquals(1,db.queries);assertEquals(7,table.getRows());
            assertTrue(db.statementClosed && db.rowsClosed);
            maximum.setBoolean(metadata,false);metadata.setCountRows(true);
            metadata.collectRows(table);assertEquals(2,db.queries);
        }
    }
    @Test void failedCountReleasesStatementAndRetainsOriginalRowMetadata() throws Exception {
        Archive archive=archive("failure.siard");
        try (Closeable close=archive::close) {
            MetaTable table=archive.createSchema("S").createTable("T").getMetaTable();
            table.createMetaColumn("ID").setType("INTEGER");table.setRows(12);
            Database db=new Database();db.fail=true;
            MetaDataFromDb metadata=MetaDataFromDb.newInstance(db.metadata,archive.getMetaData());
            metadata.setCountRows(true);
            assertEquals("COUNT failed",assertThrows(SQLException.class,()->metadata.collectRows(table)).getMessage());
            assertTrue(db.statementClosed);assertEquals(12,table.getRows());
        }
    }
}
