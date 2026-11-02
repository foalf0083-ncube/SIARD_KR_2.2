// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.utils.logging.IndentLogger;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 순번 컬럼(ORDINAL_POSITION · KEY_SEQ)을 보정하는 메타데이터 결과집합 래퍼.
 *
 * <p>Tibero 드라이버는 컬럼 순번과 기본키 순번을 0부터 센다. JDBC 규격과 엔진은 1부터 센다.
 * 보정 폭이 0이면 값을 바꾸지 않고, 0이 관찰되면 한 번만 기록한다. 외래키 · 인덱스 순번은 현행 엔진이
 * 보정하지 않았으므로 관찰만 하고, 실기동 검증(WP-06)에서 0 기준 여부를 확정한다.
 */
public class TiberoOrdinalResultSet extends TiberoResultSet {

    private static final IndentLogger _il = IndentLogger.getIndentLogger(TiberoOrdinalResultSet.class.getName());

    private final String _sLabel;
    private final int _iOffset;
    private final int _iIndex;
    private boolean _bZeroReported = false;

    public TiberoOrdinalResultSet(ResultSet rsWrapped, TiberoConnection conn, String sLabel, int iOffset) {
        super(rsWrapped, conn);
        _sLabel = sLabel;
        _iOffset = iOffset;
        _iIndex = findColumnSafe(rsWrapped, sLabel);
    }

    static int findColumnSafe(ResultSet rs, String sLabel) {
        try {
            return rs.findColumn(sLabel);
        } catch (SQLException se) {
            return -1;
        }
    }

    protected boolean isOrdinalColumn(int columnIndex) {
        return (_iIndex > 0) && (columnIndex == _iIndex);
    }

    private int adjust(int iValue) {
        if ((_iOffset == 0) && (iValue == 0) && !_bZeroReported) {
            _bZeroReported = true;
            _il.event("Tibero driver returned 0 for " + _sLabel
                    + ". The driver may count from 0 here as well; check WP-06 결과 and extend the offset.");
        }
        return iValue + _iOffset;
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        int i = super.getInt(columnIndex);
        return isOrdinalColumn(columnIndex) && !wasNull() ? adjust(i) : i;
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        long l = super.getLong(columnIndex);
        return isOrdinalColumn(columnIndex) && !wasNull() ? adjust((int) l) : l;
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        short s = super.getShort(columnIndex);
        return isOrdinalColumn(columnIndex) && !wasNull() ? (short) adjust(s) : s;
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        Object o = super.getObject(columnIndex);
        if (isOrdinalColumn(columnIndex) && (o instanceof Number)) {
            return Integer.valueOf(adjust(((Number) o).intValue()));
        }
        return o;
    }
}
