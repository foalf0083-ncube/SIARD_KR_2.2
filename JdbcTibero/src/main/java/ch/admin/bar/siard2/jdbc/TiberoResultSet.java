// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.utils.jdbc.BaseResultSet;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Tibero 결과집합 래퍼.
 *
 * <p>대용량 값(CLOB · NCLOB · BLOB)은 {@link TiberoClob} · {@link TiberoNClob} · {@link TiberoBlob}으로 감싸
 * 스트림을 만들 때 내용을 미리 읽어 둔다. 엔진은 스트림을 얻은 직후 {@code free()}를 부르고 나중에 스트림을
 * 읽는데, Tibero 드라이버는 {@code free()} 뒤에 로케이터를 더 쓰지 못하게 하므로 지연 스트림이 실패한다.
 * 현행 엔진이 Tibero에서만 통째로 읽던 우회를 래퍼 안으로 옮긴 것이다.
 *
 * <p>닫기는 자체 상태로 판정한다. 현행 엔진은 Tibero 갱신 결과집합을 {@code isClosed()} 검사 없이 닫았다.
 */
public class TiberoResultSet extends BaseResultSet {

    private final TiberoConnection _conn;
    private TiberoStatement _stmt;
    private boolean _bClosed = false;

    public TiberoResultSet(ResultSet rsWrapped, TiberoConnection conn) {
        this(rsWrapped, conn, null);
    }

    public TiberoResultSet(ResultSet rsWrapped, TiberoConnection conn, TiberoStatement stmt) {
        super(rsWrapped);
        _conn = conn;
        _stmt = stmt;
    }

    /** {@inheritDoc} 이 결과집합을 만든 래퍼 문장을 돌려준다. 없으면 벤더 문장을 감싼다. */
    @Override
    public Statement getStatement() throws SQLException {
        if (_stmt == null) {
            Statement stmtNative = super.getStatement();
            if (stmtNative == null) {
                return null;
            }
            _stmt = new TiberoStatement(stmtNative, _conn);
        }
        return _stmt;
    }

    static Object wrapLob(Object o) {
        if (o instanceof NClob) {
            return new TiberoNClob((NClob) o);
        }
        if (o instanceof Clob) {
            return new TiberoClob((Clob) o);
        }
        if (o instanceof Blob) {
            return new TiberoBlob((Blob) o);
        }
        return o;
    }

    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        Clob clob = super.getClob(columnIndex);
        return (clob == null) ? null : (Clob) wrapLob(clob);
    }

    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        NClob nclob = super.getNClob(columnIndex);
        return (nclob == null) ? null : new TiberoNClob(nclob);
    }

    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        Blob blob = super.getBlob(columnIndex);
        return (blob == null) ? null : new TiberoBlob(blob);
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        return wrapLob(super.getObject(columnIndex));
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        return wrapLob(super.getObject(columnIndex, map));
    }

    @Override
    public void close() throws SQLException {
        _bClosed = true;
        super.close();
    }

    @Override
    public boolean isClosed() throws SQLException {
        if (_bClosed) {
            return true;
        }
        try {
            return super.isClosed();
        } catch (AbstractMethodError ame) {
            // JDBC 4.0 이전 드라이버 대비. 닫힘 여부를 모르면 열린 것으로 본다.
            return false;
        } catch (SQLException se) {
            // Tibero 6 실측(2026-09-08): 갱신 가능 결과집합(TbRSUpdatable)의 isClosed 가 JDBC-90201 을 던진다.
            // 재적재는 이 결과집합으로 행을 넣으므로 닫힘 여부는 자체 표지로만 판단한다.
            return false;
        }
    }
}
