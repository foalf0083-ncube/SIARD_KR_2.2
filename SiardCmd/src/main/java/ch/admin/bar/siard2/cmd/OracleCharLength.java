// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Oracle 에서 문자 의미로 선언된 문자 컬럼의 선언 길이(문자 수)를 사전에서 읽는다.
 *
 * <p>왜 읽는가. 원본 Oracle 래퍼(jdbcoracle.jar 2.2.1)는 문자 컬럼의 길이로 바이트 길이를 보고한다.
 * AL32UTF8 데이터베이스에서 VARCHAR2(300 CHAR) 가 VARCHAR(1200), CHAR(1 CHAR) 가 CHAR(4) 로 적힌다
 * (2026-08-14 MINLAB 산출물). 값은 온전하지만 형식 기술이 선언의 네 배가 되고, 재현하면 CHAR 값 뒤에 공백이
 * 채워진다(Tibero 7 에서 같은 결함을 실측했다). 바이트 의미 컬럼은 바이트 길이가 곧 선언 길이라 맞게 온다.
 * Tibero 래퍼의 같은 규칙({@code TiberoCharLength})을 Oracle 경로에 옮긴 것이다.
 *
 * <p>테이블마다 한 번 {@code ALL_TAB_COLUMNS} 에서 {@code CHAR_USED = 'C'} 인 컬럼의 {@code CHAR_LENGTH} 를 읽어
 * 기억한다. 읽지 못하면 보고값을 그대로 둔다.
 */
final class OracleCharLength {

    private static final Logger LOG = LoggerFactory.getLogger(OracleCharLength.class);

    private final Connection _conn;
    private final Map<String, Map<String, Integer>> _mapTables = new HashMap<>();

    OracleCharLength(Connection conn) {
        _conn = conn;
    }

    /** 길이를 문자 수로 선언하는 형식. 래퍼는 형식 이름을 따옴표로 감싸 보고한다("VARCHAR2"). */
    static boolean isCharacterType(String sTypeName) {
        if (sTypeName == null) {
            return false;
        }
        switch (sTypeName.replace("\"", "").trim().toUpperCase()) {
            case "CHAR":
            case "VARCHAR2":
            case "VARCHAR":
            case "NCHAR":
            case "NVARCHAR2":
                return true;
            default:
                return false;
        }
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

    /** 래퍼의 문장은 SQL 을 다시 쓰므로 벤더 연결에 바로 보낸다({@link OracleNumberMeasure} 와 같다). */
    private Connection nativeConnection() throws SQLException {
        if (_conn.isWrapperFor(Connection.class)) {
            Connection connNative = _conn.unwrap(Connection.class);
            if (connNative != null) {
                return connNative;
            }
        }
        return _conn;
    }

    private Map<String, Integer> query(String sSchema, String sTable) {
        Map<String, Integer> mapColumns = new HashMap<>();
        try (Statement stmt = nativeConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql(sSchema, sTable))) {
            while ((rs != null) && rs.next()) {
                int iChars = rs.getInt(2);
                if (!rs.wasNull() && (iChars > 0)) {
                    mapColumns.put(rs.getString(1), Integer.valueOf(iChars));
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOG.warn("Oracle character lengths of {}.{} could not be read; reported sizes are kept: {}", sSchema, sTable,
                    e.toString());
            return Collections.emptyMap();
        }
        return mapColumns;
    }
}
