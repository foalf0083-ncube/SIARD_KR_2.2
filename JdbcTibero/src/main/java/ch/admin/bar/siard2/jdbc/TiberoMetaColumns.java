// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@code getColumns} 결과 래퍼. 컬럼 순번을 1 기준으로 보정하고 타입 코드를 파서가 아는 코드로 대응한다.
 * 다른 컬럼은 손대지 않는다.
 *
 * <p>Tibero 6 실측(2026-09-08): 구체화 뷰(materialized view)는 {@code getTables}에 TABLE 로 한 번 나오지만
 * {@code getColumns}는 뷰 객체와 그 저장 테이블의 컬럼을 같은 이름으로 두 번 돌려준다. 엔진은 한 테이블에
 * 같은 이름의 컬럼을 허용하지 않으므로, 스키마 · 테이블 · 컬럼 이름이 같은 뒤 행은 건너뛴다.
 *
 * <p>Tibero 는 선언 정밀도가 최댓값 38 인 NUMBER 의 정밀도를 비워 보고한다. 소수 자릿수는 보고한다.
 * 엔진은 빈 정밀도를 0 으로 읽어 형식을 정밀도 없는 {@code NUMERIC} 으로 적고, 그 산출물을 재적재하면
 * 소수 자리가 없는 NUMBER 가 만들어져 값이 반올림된다. 산출물 안의 값은 온전하지만 형식 기술이 틀린다.
 * 그래서 정밀도가 비고 소수 자릿수가 있는 NUMBER 는 정밀도를 38 로 채운다. Oracle 래퍼가 같은 컬럼을
 * {@code DEC(38, s)} 로 적는 것과 맞춘 것이다. 소수 자릿수까지 빈 NUMBER 는 아래 규칙을 따른다.
 * Tibero 7 은 선언 정밀도 38 도 그대로 보고하므로 이 보정이 일어나지 않는다.
 * <pre>
 * 실측(2026-09-29, Tibero 5 SP1 서버 · 6.7 드라이버)  선언 -&gt; COLUMN_SIZE, DECIMAL_DIGITS
 *   NUMBER(10,2) -&gt; 10, 2      NUMBER(37,2) -&gt; 37, 2     NUMBER(5) -&gt; 5, 0
 *   NUMBER(38,2) -&gt; NULL, 2    NUMBER(38)   -&gt; NULL, 0   NUMBER(38,0) -&gt; NULL, 0
 *   NUMBER       -&gt; NULL, NULL FLOAT        -&gt; NULL, NULL
 * </pre>
 * MINLAB 재적재 실측에서 NUMBER(38,10) 의 {@code 0.0000000001} 이 {@code 0} 으로,
 * {@code 9999999999999999999999999999.999999999} 가 {@code 10000000000000000000000000000} 으로 바뀌었다.
 *
 * <p>소수 자릿수가 빈 NUMBER 는 부동 소수 선언이다. 제약 없는 NUMBER 와 FLOAT 는 정밀도도 비어 오고,
 * Tibero 7 은 FLOAT(n) 의 정밀도를 십진 자릿수로 바꿔 보고한다(FLOAT(38) -&gt; 11, 소수 자릿수 비움).
 * 엔진은 빈 소수 자릿수를 0 으로 읽어 정수 형식으로 적으므로 재적재하면 3.5 가 4 가 된다.
 * 이런 컬럼은 실제 값을 재어 모든 값이 들어가는 정밀도와 소수 자릿수를 보고한다({@link TiberoNumberMeasure}).
 * 정하지 못하면 보고값 그대로 둔다. 재는 일은 정밀도나 소수 자릿수 칸을 실제로 읽을 때만 일어난다. 엔진이
 * 컬럼 전체를 훑으며 자료형만 볼 때(사용자 정의 형식 탐색)에는 일어나지 않는다.
 * <pre>
 * 실측(2026-09-30, Tibero 7.2.5 서버 · 6.7 과 7.2 드라이버 같은 값)
 *   NUMBER(38,2) -&gt; 38, 2     NUMBER(38) · INTEGER -&gt; 38, 0
 *   NUMBER · FLOAT -&gt; NULL, NULL                FLOAT(38) -&gt; 11, NULL
 * </pre>
 *
 * <p>Tibero 7 서버는 문자 의미로 선언된 문자 컬럼(VARCHAR2(n CHAR) · CHAR(n CHAR) · NCHAR · NVARCHAR2)의
 * COLUMN_SIZE 를 문자 수가 아니라 바이트 길이로 보고한다(UTF8 데이터베이스에서 VARCHAR2(300 CHAR) -&gt; 1200).
 * 이런 컬럼은 사전의 선언 길이로 바꿔 보고한다({@link TiberoCharLength}).
 */
public class TiberoMetaColumns extends TiberoOrdinalResultSet {

    /** Tibero NUMBER 의 최대 정밀도. 드라이버가 정밀도를 비워 보고할 때 채운다. */
    static final int NUMBER_MAX_PRECISION = 38;

    private final int _iDataType;
    private final int _iTypeName;
    private final int _iTableSchem;
    private final int _iTableName;
    private final int _iColumnName;
    private final int _iColumnSize;
    private final int _iDecimalDigits;
    private final TiberoConnection _conn;
    private final java.util.Set<String> _setSeen = new java.util.HashSet<>();

    public TiberoMetaColumns(ResultSet rsWrapped, TiberoConnection conn) {
        super(rsWrapped, conn, "ORDINAL_POSITION", 1);
        _conn = conn;
        _iDataType = findColumnSafe(rsWrapped, "DATA_TYPE");
        _iTypeName = findColumnSafe(rsWrapped, "TYPE_NAME");
        _iTableSchem = findColumnSafe(rsWrapped, "TABLE_SCHEM");
        _iTableName = findColumnSafe(rsWrapped, "TABLE_NAME");
        _iColumnName = findColumnSafe(rsWrapped, "COLUMN_NAME");
        _iColumnSize = findColumnSafe(rsWrapped, "COLUMN_SIZE");
        _iDecimalDigits = findColumnSafe(rsWrapped, "DECIMAL_DIGITS");
    }

    private boolean isDataTypeColumn(int columnIndex) {
        return (_iDataType > 0) && (columnIndex == _iDataType);
    }

    private boolean isColumnSizeColumn(int columnIndex) {
        return (_iColumnSize > 0) && (columnIndex == _iColumnSize);
    }

    private boolean isDecimalDigitsColumn(int columnIndex) {
        return (_iDecimalDigits > 0) && (columnIndex == _iDecimalDigits);
    }

    /** {@inheritDoc} 같은 스키마 · 테이블 · 컬럼 이름의 행이 다시 나오면 건너뛴다(구체화 뷰 중복). */
    @Override
    public boolean next() throws SQLException {
        while (super.next()) {
            if ((_iTableName <= 0) || (_iColumnName <= 0)) {
                return true;
            }
            String sKey = rawString(_iTableSchem) + " " + rawString(_iTableName) + " " + rawString(_iColumnName);
            if (_setSeen.add(sKey)) {
                return true;
            }
        }
        return false;
    }

    private String rawString(int columnIndex) throws SQLException {
        return (columnIndex > 0) ? super.getString(columnIndex) : null;
    }

    private Object rawObject(int columnIndex) throws SQLException {
        return (columnIndex > 0) ? super.getObject(columnIndex) : null;
    }

    private int mapDataType(int iDataType) throws SQLException {
        String sTypeName = rawString(_iTypeName);
        String sWhere = rawString(_iTableSchem) + "." + rawString(_iTableName) + "." + rawString(_iColumnName);
        return TiberoTypeMap.toParserType(iDataType, sTypeName, sWhere);
    }

    /**
     * COLUMN_SIZE 나 DECIMAL_DIGITS 를 읽을 때 바꿔 보고할 값을 돌려준다. 바꾸지 않으면 null 이다.
     * <ul>
     * <li>소수 자릿수가 있고 정밀도만 빈 NUMBER(선언 정밀도 38, Tibero 5): 정밀도를 38 로 채운다.</li>
     * <li>소수 자릿수가 빈 NUMBER(제약 없는 NUMBER · FLOAT · FLOAT(n)): 값을 재어 둘 다 바꾼다.
     *     정하지 못하면 보고값 그대로 둔다.</li>
     * <li>문자 의미로 선언된 문자 컬럼의 COLUMN_SIZE: 사전의 선언 길이(문자 수)로 바꾼다({@link TiberoCharLength}).</li>
     * </ul>
     * 판정하느라 다른 칸을 읽으므로 {@link #wasNull()} 을 읽은 칸 기준으로 되돌려 둔다.
     */
    private Integer filled(int columnIndex, boolean bWasNull) throws SQLException {
        boolean bSize = isColumnSizeColumn(columnIndex);
        boolean bDigits = isDecimalDigitsColumn(columnIndex);
        if (!bSize && !bDigits) {
            return null;
        }
        Integer iFilled = null;
        String sTypeName = (_iTypeName > 0) ? rawString(_iTypeName) : null;
        if (bSize && !bWasNull && (_conn != null) && isCharacterType(sTypeName)) {
            iFilled = _conn.charLength().of(
                    rawString(_iTableSchem), rawString(_iTableName), rawString(_iColumnName));
        } else if ("NUMBER".equalsIgnoreCase(sTypeName)) {
            boolean bSizeNull = (_iColumnSize <= 0) || (rawObject(_iColumnSize) == null);
            boolean bDigitsNull = (_iDecimalDigits <= 0) || (rawObject(_iDecimalDigits) == null);
            if (!bDigitsNull) {
                if (bSize && bSizeNull) {
                    iFilled = Integer.valueOf(NUMBER_MAX_PRECISION);
                }
            } else if (_conn != null) {
                int[] aiMeasured = _conn.numberMeasure().measure(
                        rawString(_iTableSchem), rawString(_iTableName), rawString(_iColumnName));
                if (aiMeasured != null) {
                    iFilled = Integer.valueOf(bSize ? aiMeasured[0] : aiMeasured[1]);
                }
            }
        }
        _bWasNull = (iFilled == null) && bWasNull;
        return iFilled;
    }

    /** 길이를 문자 수로 선언하는 형식. 드라이버는 VARCHAR2 · NVARCHAR2 를 VARCHAR · NVARCHAR 로 보고한다. */
    static boolean isCharacterType(String sTypeName) {
        if (sTypeName == null) {
            return false;
        }
        switch (sTypeName.toUpperCase()) {
            case "CHAR":
            case "VARCHAR":
            case "VARCHAR2":
            case "NCHAR":
            case "NVARCHAR":
            case "NVARCHAR2":
                return true;
            default:
                return false;
        }
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        int i = super.getInt(columnIndex);
        if (isDataTypeColumn(columnIndex)) {
            return !wasNull() ? mapDataType(i) : i;
        }
        Integer iFilled = filled(columnIndex, wasNull());
        return (iFilled != null) ? iFilled.intValue() : i;
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        long l = super.getLong(columnIndex);
        if (isDataTypeColumn(columnIndex)) {
            return !wasNull() ? mapDataType((int) l) : l;
        }
        Integer iFilled = filled(columnIndex, wasNull());
        return (iFilled != null) ? iFilled.longValue() : l;
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        short s = super.getShort(columnIndex);
        if (isDataTypeColumn(columnIndex)) {
            return !wasNull() ? (short) mapDataType(s) : s;
        }
        Integer iFilled = filled(columnIndex, wasNull());
        return (iFilled != null) ? iFilled.shortValue() : s;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        String s = super.getString(columnIndex);
        Integer iFilled = filled(columnIndex, s == null);
        return (iFilled != null) ? String.valueOf(iFilled) : s;
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        Object o = super.getObject(columnIndex);
        if (isDataTypeColumn(columnIndex) && (o instanceof Number)) {
            return Integer.valueOf(mapDataType(((Number) o).intValue()));
        }
        Integer iFilled = filled(columnIndex, o == null);
        return (iFilled != null) ? iFilled : o;
    }
}
