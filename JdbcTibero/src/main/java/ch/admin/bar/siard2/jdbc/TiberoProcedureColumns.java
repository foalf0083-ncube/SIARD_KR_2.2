// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code getProcedureColumns} · {@code getFunctionColumns} 결과 래퍼.
 *
 * <p>Tibero 6(빌드 203675) 실측(2026-09-08):
 * <ul>
 *   <li>ORDINAL_POSITION 이 모든 행에서 NULL 이다. 엔진은 매개변수 순번이 1부터 이어지길 요구한다.
 *       행은 선언 순서로 오므로 루틴별로 세어 채운다. 반환값 행은 0 이다.</li>
 *   <li>{@code getFunctionColumns} 의 COLUMN_TYPE 라벨이 {@code COLUMN_TPYE} 로 잘못 나온다.
 *       라벨로 찾을 때 대신 찾아 준다.</li>
 *   <li>TIMESTAMP 매개변수의 DATA_TYPE 이 OTHER(1111) 로 온다. 이름으로 TIMESTAMP(93) 에 대응한다.
 *       그 밖의 코드는 {@link TiberoTypeMap} 규칙을 따른다.</li>
 * </ul>
 * 드라이버가 양수 순번을 주면 그 값을 쓰고, 없을 때만 센 값을 쓴다.
 */
public class TiberoProcedureColumns extends TiberoResultSet {

    private static final String sLABEL_COLUMN_TYPE = "COLUMN_TYPE";
    private static final String sLABEL_COLUMN_TYPE_TYPO = "COLUMN_TPYE";

    private final int _iSchem;
    private final int _iName;
    private final int _iSpecific;
    private final int _iColumnType;
    private final int _iColumnName;
    private final int _iDataType;
    private final int _iTypeName;
    private final int _iOrdinal;
    private final Map<String, Integer> _mapCount = new HashMap<>();
    private int _iComputedOrdinal = 0;

    public TiberoProcedureColumns(ResultSet rsWrapped, TiberoConnection conn) {
        super(rsWrapped, conn);
        _iSchem = firstColumn(rsWrapped, "PROCEDURE_SCHEM", "FUNCTION_SCHEM");
        _iName = firstColumn(rsWrapped, "PROCEDURE_NAME", "FUNCTION_NAME");
        _iSpecific = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "SPECIFIC_NAME");
        _iColumnType = firstColumn(rsWrapped, sLABEL_COLUMN_TYPE, sLABEL_COLUMN_TYPE_TYPO);
        _iColumnName = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "COLUMN_NAME");
        _iDataType = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "DATA_TYPE");
        _iTypeName = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "TYPE_NAME");
        _iOrdinal = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "ORDINAL_POSITION");
    }

    private static int firstColumn(ResultSet rs, String sLabel, String sAlternative) {
        int i = TiberoOrdinalResultSet.findColumnSafe(rs, sLabel);
        return (i > 0) ? i : TiberoOrdinalResultSet.findColumnSafe(rs, sAlternative);
    }

    private static boolean isReturnRow(int iColumnType) {
        // 엔진은 5(procedureColumnReturn) 와 3(procedureColumnResult) 을 반환값 행으로 본다.
        return (iColumnType == DatabaseMetaData.procedureColumnReturn)
                || (iColumnType == DatabaseMetaData.procedureColumnResult);
    }

    private String rawString(int columnIndex) throws SQLException {
        return (columnIndex > 0) ? super.getString(columnIndex) : null;
    }

    /** {@inheritDoc} 행마다 루틴별 매개변수 순번을 센다. */
    @Override
    public boolean next() throws SQLException {
        if (!super.next()) {
            return false;
        }
        if ((_iColumnType > 0) && (_iName > 0)) {
            int iColumnType = super.getInt(_iColumnType);
            if (isReturnRow(iColumnType)) {
                _iComputedOrdinal = 0;
            } else {
                String sKey = rawString(_iSchem) + "." + rawString(_iName) + "." + rawString(_iSpecific);
                int iCount = _mapCount.getOrDefault(sKey, Integer.valueOf(0)).intValue() + 1;
                _mapCount.put(sKey, Integer.valueOf(iCount));
                _iComputedOrdinal = iCount;
            }
        }
        return true;
    }

    /** {@inheritDoc} 드라이버가 COLUMN_TYPE 라벨을 잘못 적은 경우 그 라벨로 대신 찾는다. */
    @Override
    public int findColumn(String columnLabel) throws SQLException {
        try {
            return super.findColumn(columnLabel);
        } catch (SQLException se) {
            if (sLABEL_COLUMN_TYPE.equalsIgnoreCase(columnLabel)) {
                return super.findColumn(sLABEL_COLUMN_TYPE_TYPO);
            }
            throw se;
        }
    }

    private boolean isOrdinalColumn(int columnIndex) {
        return (_iOrdinal > 0) && (columnIndex == _iOrdinal);
    }

    private boolean isDataTypeColumn(int columnIndex) {
        return (_iDataType > 0) && (columnIndex == _iDataType);
    }

    private int ordinal(int iDriverValue) {
        return (iDriverValue > 0) ? iDriverValue : _iComputedOrdinal;
    }

    private int mapDataType(int iDataType) throws SQLException {
        String sTypeName = rawString(_iTypeName);
        String sWhere = rawString(_iSchem) + "." + rawString(_iName) + "." + rawString(_iColumnName);
        if ((iDataType == java.sql.Types.OTHER) && (sTypeName != null)) {
            String sUpper = sTypeName.trim().toUpperCase();
            if (sUpper.startsWith("TIMESTAMP") && !sUpper.contains("ZONE")) {
                return java.sql.Types.TIMESTAMP;
            }
        }
        return TiberoTypeMap.toParserType(iDataType, sTypeName, sWhere);
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        int i = super.getInt(columnIndex);
        if (isOrdinalColumn(columnIndex)) {
            return ordinal(i);
        }
        return isDataTypeColumn(columnIndex) && !wasNull() ? mapDataType(i) : i;
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        long l = super.getLong(columnIndex);
        if (isOrdinalColumn(columnIndex)) {
            return ordinal((int) l);
        }
        return isDataTypeColumn(columnIndex) && !wasNull() ? mapDataType((int) l) : l;
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        short s = super.getShort(columnIndex);
        if (isOrdinalColumn(columnIndex)) {
            return (short) ordinal(s);
        }
        return isDataTypeColumn(columnIndex) && !wasNull() ? (short) mapDataType(s) : s;
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        Object o = super.getObject(columnIndex);
        if (isOrdinalColumn(columnIndex)) {
            return Integer.valueOf(ordinal((o instanceof Number) ? ((Number) o).intValue() : 0));
        }
        if (isDataTypeColumn(columnIndex) && (o instanceof Number)) {
            return Integer.valueOf(mapDataType(((Number) o).intValue()));
        }
        return o;
    }
}
