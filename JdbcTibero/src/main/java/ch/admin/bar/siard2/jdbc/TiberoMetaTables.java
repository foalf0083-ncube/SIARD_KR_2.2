// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.utils.jdbc.BaseDatabaseMetaData;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * {@code getTables} 결과 래퍼. 시스템 스키마 행을 걸러내고, 뷰 행에는 정의 문장을 담은 가상 컬럼
 * {@code QUERY_TEXT}를 덧붙인다.
 *
 * <p>엔진({@code MetaDataFromDb.getViews})은 다른 제품의 래퍼가 {@code getTables} 결과에 넣어 주는
 * {@code QUERY_TEXT} 컬럼에서 뷰 정의를 읽는다. Tibero 드라이버의 {@code getTables}에는 그 컬럼이 없어
 * 뷰가 정의 없이 보존됐다. 뷰 행에서 {@code QUERY_TEXT}를 읽으면 {@code ALL_VIEWS.TEXT}를 조회해 돌려준다.
 * 정의를 못 찾으면 값 대신 {@link SQLException}을 던진다. 엔진은 이 예외를 잡아 정의 없이 진행하고,
 * null 을 돌려주면 엔진이 멈추기 때문이다.
 */
public class TiberoMetaTables extends TiberoSchemaFilterResultSet {

    private static final String sVIEW = "VIEW";

    private final TiberoConnection _conn;
    private final int _iSchem;
    private final int _iName;
    private final int _iType;
    /** 가상 컬럼 위치. 드라이버 컬럼 수 + 1. 0 이면 쓰지 않는다. */
    private final int _iQueryText;
    private String _sQueryText = null;
    private boolean _bQueryTextLoaded = false;

    public TiberoMetaTables(ResultSet rsWrapped, TiberoConnection conn) {
        super(rsWrapped, conn, "TABLE_SCHEM");
        _conn = conn;
        _iSchem = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "TABLE_SCHEM");
        _iName = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "TABLE_NAME");
        _iType = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "TABLE_TYPE");
        int iCount = 0;
        try {
            iCount = rsWrapped.getMetaData().getColumnCount();
        } catch (Throwable t) {
            iCount = 0;
        }
        _iQueryText = ((iCount > 0) && (_iSchem > 0) && (_iName > 0) && (_iType > 0)) ? iCount + 1 : 0;
    }

    @Override
    public boolean next() throws SQLException {
        _sQueryText = null;
        _bQueryTextLoaded = false;
        return super.next();
    }

    /** {@inheritDoc} {@code QUERY_TEXT}는 가상 컬럼 위치로 답한다. */
    @Override
    public int findColumn(String columnLabel) throws SQLException {
        if ((_iQueryText > 0) && BaseDatabaseMetaData._sQUERY_TEXT.equalsIgnoreCase(columnLabel)) {
            return _iQueryText;
        }
        return super.findColumn(columnLabel);
    }

    private boolean isQueryTextColumn(int columnIndex) {
        return (_iQueryText > 0) && (columnIndex == _iQueryText);
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    /** 현재 행이 뷰이면 정의 문장을, 아니면 예외. */
    private String queryText() throws SQLException {
        if (!_bQueryTextLoaded) {
            _bQueryTextLoaded = true;
            String sType = super.getString(_iType);
            if (sVIEW.equals(sType)) {
                String sSql = "SELECT TEXT FROM ALL_VIEWS WHERE OWNER = " + quote(super.getString(_iSchem))
                        + " AND VIEW_NAME = " + quote(super.getString(_iName));
                try (Statement stmt = _conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sSql)) {
                    if (rs.next()) {
                        _sQueryText = rs.getString(1);
                    }
                }
            }
        }
        if (_sQueryText == null) {
            throw new SQLException("뷰 정의를 찾지 못했습니다. 위치=" + super.getString(_iSchem) + "."
                    + super.getString(_iName));
        }
        return _sQueryText;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        if (isQueryTextColumn(columnIndex)) {
            String s = queryText();
            _bWasNull = false;
            return s;
        }
        return super.getString(columnIndex);
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        if (isQueryTextColumn(columnIndex)) {
            return getString(columnIndex);
        }
        return super.getObject(columnIndex);
    }
}
