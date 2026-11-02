// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiberoConnectionTest {

    @Test
    void arrayElementTypeIntegerBecomesTiberoName() throws SQLException {
        FakeJdbc.ConnectionSpy spy = FakeJdbc.connection(null, null);
        TiberoConnection conn = new TiberoConnection(spy.proxy);
        conn.createArrayOf("INTEGER", new Integer[]{1, 2});
        conn.createArrayOf("integer", new Integer[]{1});
        conn.createArrayOf("VARCHAR", new String[]{"a"});
        assertEquals(List.of("INTEGER_OBJ", "INTEGER_OBJ", "VARCHAR"), spy.arrayTypes);
    }

    @Test
    void nativeSqlPassesThroughWithoutVendorTranslation() throws SQLException {
        FakeJdbc.ConnectionSpy spy = FakeJdbc.connection(null, null);
        TiberoConnection conn = new TiberoConnection(spy.proxy);
        assertEquals("SELECT 1 FROM DUAL", conn.nativeSQL("SELECT 1 FROM DUAL"));
    }

    @Test
    void nativeSqlRewritesOnlyOctetLengthCalls() throws SQLException {
        // Tibero 6 실측: OCTET_LENGTH 미지원(JDBC-8086). 엔진의 -x 크기 질의만 DBMS_LOB.GETLENGTH 로 바꾼다.
        FakeJdbc.ConnectionSpy spy = FakeJdbc.connection(null, null);
        TiberoConnection conn = new TiberoConnection(spy.proxy);
        String sEngine = "SELECT COUNT(*) AS RECORDS, SUM(OCTET_LENGTH(CONTENT)) AS CONTENT_SIZE FROM MINLAB.ATT_FILE_BLOB";
        assertEquals(sEngine.replace("OCTET_LENGTH(", "DBMS_LOB.GETLENGTH("), conn.nativeSQL(sEngine));
        assertEquals("select DBMS_LOB.GETLENGTH(c) from t", conn.nativeSQL("select octet_length (c) from t"));
        assertEquals("SELECT OCTET_LENGTH_X, OCTET_LENGTHS FROM T", conn.nativeSQL("SELECT OCTET_LENGTH_X, OCTET_LENGTHS FROM T"));
        assertEquals(null, conn.nativeSQL(null));
    }

    @Test
    void metaDataStatementAndResultSetAreWrapped() throws SQLException {
        FakeJdbc.Rows rows = new FakeJdbc.Rows("A").row(1);
        FakeJdbc.ConnectionSpy spy = FakeJdbc.connection(null, FakeJdbc.resultSet(rows));
        spy.dmd = FakeJdbc.databaseMetaData(FakeJdbc.byMethod(), 6, spy.proxy);
        TiberoConnection conn = new TiberoConnection(spy.proxy);

        assertTrue(conn.getMetaData() instanceof TiberoDatabaseMetaData);
        Statement stmt = conn.createStatement();
        assertTrue(stmt instanceof TiberoStatement);
        assertSame(conn, stmt.getConnection());

        ResultSet rs = stmt.executeQuery("SELECT A FROM T");
        assertTrue(rs instanceof TiberoResultSet);
        assertSame(stmt, rs.getStatement());
        assertSame(conn, rs.getStatement().getConnection());
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("A"));
    }

    @Test
    void unwrapReturnsVendorConnection() throws SQLException {
        FakeJdbc.ConnectionSpy spy = FakeJdbc.connection(null, null);
        TiberoConnection conn = new TiberoConnection(spy.proxy);
        assertTrue(conn.isWrapperFor(Connection.class));
        assertSame(spy.proxy, conn.unwrap(Connection.class));
    }
}
