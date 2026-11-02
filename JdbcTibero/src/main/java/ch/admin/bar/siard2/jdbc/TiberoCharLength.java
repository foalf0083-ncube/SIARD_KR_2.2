// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.utils.logging.IndentLogger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 문자 의미로 선언된 문자 컬럼의 선언 길이(문자 수)를 사전에서 읽는다.
 *
 * <p>왜 읽는가. JDBC 의 {@code getColumns} COLUMN_SIZE 는 문자형이면 문자 수다. Tibero 7 서버는 문자 의미 컬럼의
 * 바이트 길이를 돌려준다(2026-09-30, Tibero 7.2.5 · 6.7 과 7.2 드라이버 같은 값). UTF8 데이터베이스에서
 * VARCHAR2(300 CHAR) 가 1200, CHAR(1 CHAR) 가 4, NVARCHAR2(100) 이 200 으로 온다. 엔진은 이를 그대로 적어
 * 산출물의 형식이 VARCHAR(1200) 이 된다. 값은 온전하지만 형식 기술이 선언의 네 배가 된다. 바이트 의미 컬럼은
 * 바이트 길이가 곧 선언 길이라 맞게 온다. Tibero 5 · 6 서버는 문자 수를 돌려준다.
 *
 * <p>어떻게 읽는가. 테이블마다 한 번 {@code ALL_TAB_COLUMNS} 에서 {@code CHAR_USED = 'C'} 인 컬럼의
 * {@code CHAR_LENGTH} 를 읽어 연결 단위로 기억한다. 읽지 못하면 보고값을 그대로 둔다.
 */
final class TiberoCharLength {

    private static final IndentLogger _il = IndentLogger.getIndentLogger(TiberoCharLength.class.getName());

    private final Connection _conn;
    private final Map<String, Map<String, Integer>> _mapTables = new HashMap<>();

    TiberoCharLength(Connection conn) {
        _conn = conn;
    }

    /** 문자 의미 컬럼의 선언 길이(문자 수). 문자 의미가 아니거나 읽지 못하면 null. */
    Integer of(String sSchema, String sTable, String sColumn) {
        if ((_conn == null) || (sSchema == null) || (sTable == null) || (sColumn == null)) {
            return null;
        }
        String sKey = sSchema + "\u0000" + sTable;
        Map<String, Integer> mapColumns = _mapTables.get(sKey);
        if (mapColumns == null) {
            mapColumns = query(sSchema, sTable);
            _mapTables.put(sKey, mapColumns);
        }
        return mapColumns.get(sColumn);
    }

    static String sql(String sSchema, String sTable) {
        return "SELECT COLUMN_NAME, CHAR_LENGTH FROM ALL_TAB_COLUMNS WHERE OWNER = " + literal(sSchema)
                + " AND TABLE_NAME = " + literal(sTable) + " AND CHAR_USED = 'C'";
    }

    static String literal(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private Map<String, Integer> query(String sSchema, String sTable) {
        Map<String, Integer> mapColumns = new HashMap<>();
        try (Statement stmt = _conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql(sSchema, sTable))) {
            while ((rs != null) && rs.next()) {
                int iChars = rs.getInt(2);
                if (!rs.wasNull() && (iChars > 0)) {
                    mapColumns.put(rs.getString(1), Integer.valueOf(iChars));
                }
            }
        } catch (SQLException | RuntimeException e) {
            _il.event("Tibero character lengths of " + sSchema + "." + sTable
                    + " could not be read; reported sizes are kept: " + e);
            return Collections.emptyMap();
        }
        return mapColumns;
    }
}
