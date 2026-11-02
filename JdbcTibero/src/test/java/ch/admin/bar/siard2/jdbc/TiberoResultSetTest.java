// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiberoResultSetTest {

    private static String readAll(Reader r) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = r.read()) >= 0) sb.append((char) c);
        return sb.toString();
    }

    private TiberoResultSet wrap(FakeJdbc.Rows rows) {
        FakeJdbc.ConnectionSpy spy = FakeJdbc.connection(null, null);
        return new TiberoResultSet(FakeJdbc.resultSet(rows), new TiberoConnection(spy.proxy));
    }

    @Test
    void clobStreamSurvivesFreeBecauseContentIsReadUpFront() throws Exception {
        FakeJdbc.Rows rows = new FakeJdbc.Rows("C").row(FakeJdbc.clob("hello tibero", false));
        TiberoResultSet rs = wrap(rows);
        rs.next();
        Clob clob = rs.getClob(1);
        assertTrue(clob instanceof TiberoClob);
        assertFalse(clob instanceof NClob);
        Reader reader = clob.getCharacterStream();   // 엔진 순서: 스트림을 얻고
        clob.free();                                  // 곧바로 free() 한 뒤
        assertEquals("hello tibero", readAll(reader)); // 나중에 읽는다
        assertThrows(SQLException.class, clob::length); // 벤더 객체는 실제로 해제되어 있다
    }

    @Test
    void nclobKeepsNClobTypeAndIsWrapped() throws Exception {
        FakeJdbc.Rows rows = new FakeJdbc.Rows("C").row(FakeJdbc.clob("national", true));
        TiberoResultSet rs = wrap(rows);
        rs.next();
        NClob nclob = rs.getNClob(1);
        assertTrue(nclob instanceof TiberoNClob);
        Object o = rs.getObject(1);
        assertTrue(o instanceof TiberoNClob);
        assertEquals("national", readAll(nclob.getCharacterStream()));
    }

    @Test
    void blobStreamSurvivesFree() throws Exception {
        byte[] data = "binary".getBytes(StandardCharsets.UTF_8);
        FakeJdbc.Rows rows = new FakeJdbc.Rows("B").row(FakeJdbc.blob(data));
        TiberoResultSet rs = wrap(rows);
        rs.next();
        Blob blob = rs.getBlob(1);
        assertTrue(blob instanceof TiberoBlob);
        InputStream in = blob.getBinaryStream();
        blob.free();
        assertArrayEquals(data, in.readAllBytes());
    }

    @Test
    void emptyLobsProduceEmptyStreams() throws Exception {
        FakeJdbc.Rows rows = new FakeJdbc.Rows("C", "B").row(FakeJdbc.clob("", false), FakeJdbc.blob(new byte[0]));
        TiberoResultSet rs = wrap(rows);
        rs.next();
        assertEquals("", readAll(rs.getClob(1).getCharacterStream()));
        assertEquals(0, rs.getBlob(2).getBinaryStream().readAllBytes().length);
    }

    @Test
    void nullLobStaysNullAndNonLobValuesPassThrough() throws SQLException {
        FakeJdbc.Rows rows = new FakeJdbc.Rows("C", "N").row(null, 42);
        TiberoResultSet rs = wrap(rows);
        rs.next();
        assertNull(rs.getClob(1));
        assertTrue(rs.wasNull());
        assertNull(rs.getObject("C"));
        assertEquals(42, rs.getObject("N"));
        assertEquals(42, rs.getInt(2));
    }

    @Test
    void closeIsTrackedLocally() throws SQLException {
        TiberoResultSet rs = wrap(new FakeJdbc.Rows("A").row(1));
        assertFalse(rs.isClosed());
        rs.close();
        assertTrue(rs.isClosed());
        rs.close();
        assertTrue(rs.isClosed());
    }

    @Test
    void isClosedFallsBackToLocalFlagWhenDriverRejectsTheCall() throws SQLException {
        // Tibero 6 실측: 갱신 가능 결과집합(TbRSUpdatable)의 isClosed 가 JDBC-90201 을 던진다.
        ResultSet inner = FakeJdbc.resultSet(new FakeJdbc.Rows("A").row(1));
        ResultSet rejecting = (ResultSet) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class}, (p, m, a) -> {
                    if (m.getName().equals("isClosed")) {
                        throw new SQLException("JDBC-90201:Unsupported operation.");
                    }
                    try {
                        return m.invoke(inner, a);
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        throw ite.getCause();
                    }
                });
        TiberoResultSet rs = new TiberoResultSet(rejecting, new TiberoConnection(FakeJdbc.connection(null, null).proxy));
        assertFalse(rs.isClosed());
        rs.close();
        assertTrue(rs.isClosed());
    }

    @Test
    void resultSetWithoutStatementReturnsNullStatement() throws SQLException {
        ResultSet rs = wrap(new FakeJdbc.Rows("A").row(1));
        assertNull(rs.getStatement());
    }
}
