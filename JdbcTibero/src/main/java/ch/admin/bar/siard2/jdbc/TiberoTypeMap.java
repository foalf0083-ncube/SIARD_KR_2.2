// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.sqlparser.datatype.enums.PreType;
import ch.enterag.utils.logging.IndentLogger;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tibero 드라이버가 내는 JDBC 타입 코드를 SQL 파서가 아는 코드로 대응한다.
 *
 * <p>파서({@link PreType})가 아는 코드는 24종이고 Tibero 드라이버는 39종을 내보낸다. 파서가 모르는 코드가
 * 들어오면 {@code PredefinedType.format()}이 null을 돌려주고 그 null이 SQL 파서까지 흘러가 실패한다.
 * 이 대응은 파서가 이미 아는 코드에는 손대지 않는다. 사용자 정의 타입 계열(DISTINCT · STRUCT · ARRAY)과
 * OTHER는 엔진이 따로 다루므로 그대로 둔다.
 */
public final class TiberoTypeMap {

    private static final IndentLogger _il = IndentLogger.getIndentLogger(TiberoTypeMap.class.getName());

    private TiberoTypeMap() {
    }

    /** 파서가 아는 코드. */
    private static final Set<Integer> KNOWN;
    /** 엔진이 별도 경로로 다루는 코드. 대응하지 않고 통과시킨다. */
    private static final Set<Integer> PASS_THROUGH = Set.of(
            java.sql.Types.DISTINCT, java.sql.Types.STRUCT, java.sql.Types.ARRAY, java.sql.Types.OTHER);
    /** 파서가 모르는 코드를 옮겨 담을 자리. */
    private static final Map<Integer, PreType> FALLBACK;
    /** 코드로 못 가릴 때 타입 이름으로 한 번 더 본다. */
    private static final Map<String, PreType> BY_NAME;

    static {
        Set<Integer> known = new java.util.HashSet<>();
        for (PreType pt : PreType.values()) {
            known.add(pt.getSqlType());
        }
        KNOWN = Collections.unmodifiableSet(known);

        Map<Integer, PreType> fb = new HashMap<>();
        fb.put(java.sql.Types.LONGVARCHAR, PreType.CLOB);        // -1   Tibero LONG
        fb.put(java.sql.Types.LONGVARBINARY, PreType.BLOB);      // -4   Tibero LONG RAW
        fb.put(java.sql.Types.LONGNVARCHAR, PreType.NCLOB);      // -16
        fb.put(java.sql.Types.TINYINT, PreType.SMALLINT);        // -6
        fb.put(java.sql.Types.BIT, PreType.BOOLEAN);             // -7
        fb.put(java.sql.Types.ROWID, PreType.VARCHAR);           // -8
        fb.put(26, PreType.CLOB);                                // GEOMETRY
        fb.put(100, PreType.REAL);                               // BINARY_FLOAT
        fb.put(101, PreType.DOUBLE);                             // BINARY_DOUBLE
        FALLBACK = Collections.unmodifiableMap(fb);

        Map<String, PreType> byName = new HashMap<>();
        byName.put("LONG", PreType.CLOB);
        byName.put("LONG RAW", PreType.BLOB);
        byName.put("LONGRAW", PreType.BLOB);
        byName.put("ROWID", PreType.VARCHAR);
        byName.put("UROWID", PreType.VARCHAR);
        byName.put("RAW", PreType.VARBINARY);
        byName.put("BFILE", PreType.BLOB);
        byName.put("XMLTYPE", PreType.XML);
        byName.put("BINARY_FLOAT", PreType.REAL);
        byName.put("BINARY_DOUBLE", PreType.DOUBLE);
        BY_NAME = Collections.unmodifiableMap(byName);
    }

    /**
     * 파서가 쓸 수 있는 타입 코드를 돌려준다.
     *
     * @param iDataType 드라이버가 준 {@link java.sql.Types} 코드
     * @param sTypeName 드라이버가 준 타입 이름. 없으면 null
     * @param sWhere    오류를 알릴 때 쓸 위치. 보기: {@code "SCHEMA.TABLE.COLUMN"}
     * @return 파서가 아는 타입 코드
     * @throws SQLException 코드로도 이름으로도 옮길 자리를 못 찾은 경우
     */
    public static int toParserType(int iDataType, String sTypeName, String sWhere) throws SQLException {
        if (KNOWN.contains(iDataType) || PASS_THROUGH.contains(iDataType)) {
            return iDataType;
        }
        PreType pt = FALLBACK.get(iDataType);
        if ((pt == null) && (sTypeName != null)) {
            pt = BY_NAME.get(sTypeName.trim().toUpperCase());
        }
        if (pt == null) {
            throw new SQLException(String.format(
                    "지원하지 않는 컬럼 타입입니다. 위치=%s 타입코드=%d 타입이름=%s",
                    sWhere, iDataType, String.valueOf(sTypeName)));
        }
        _il.event("타입 대응: " + sWhere + " 타입코드 " + iDataType + "(" + sTypeName + ") -> " + pt.name());
        return pt.getSqlType();
    }
}
