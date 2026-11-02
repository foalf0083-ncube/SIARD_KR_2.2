// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

/**
 * Oracle 의 십진 부동 소수 컬럼(정밀도 없는 NUMBER · FLOAT · FLOAT(n))의 실제 값을 재어 NUMERIC(p,s) 로 적게 한다.
 *
 * <p>왜 재는가. 원본 Oracle 래퍼(jdbcoracle.jar 2.2.1)는 이 컬럼들을 FLOAT 로 보고하고, 엔진은 FLOAT 를 배정밀도로
 * 읽는다. 그래서 유효숫자 15자리를 넘는 값은 산출물 안에서부터 바뀐다(2026-10-13 Oracle XE 21c 실측:
 * 4113510300100010001 -&gt; 4.11351030010001E18, 98765432109876.54 -&gt; 9.876543210987655E13). Oracle 의 NUMBER 와
 * FLOAT 는 모두 최대 38자리의 십진수라 배정밀도보다 넓다. SIARD 2.2 에는 정확한 십진 부동 소수 형식이 없으므로
 * 값을 재어 모든 값이 들어가는 NUMERIC(p,s) 를 정한다. 원래 선언은 {@code typeOriginal} 에 따로 남는다.
 * Tibero 래퍼의 같은 규칙({@code TiberoNumberMeasure})을 Oracle 경로에 옮긴 것이다. 래퍼의 소스가 저장소에 없어
 * 엔진에서 적용한다.
 *
 * <p>어떻게 재는가. 한 번의 집계 질의로 {@code TO_CHAR(ABS(값), 'TM9')} 의 정수부 · 소수부 자릿수 최댓값을 얻는다.
 * 정밀도는 두 최댓값의 합이다.
 *
 * <p>정하지 못하면(값이 없음 · 지수 표기가 필요한 극단값 · 38자리 초과 · 질의 실패) 정밀도 없는 NUMERIC 으로 적는다.
 * 값은 BigDecimal 로 읽혀 산출물에 그대로 남는다. 재현할 때만 소수 자리가 반올림될 수 있다. 배정밀도로 적어
 * 보존본의 값을 깎는 것보다 낫다고 보았다.
 *
 * <p>현장에서 표가 너무 커 재는 시간이 문제가 되면 {@value #PROPERTY}=false 로 끈다. 끄면 래퍼의 원래 동작(FLOAT)이다.
 */
final class OracleNumberMeasure {

    private static final Logger LOG = LoggerFactory.getLogger(OracleNumberMeasure.class);

    /** 끄개. 기본은 켜짐. */
    static final String PROPERTY = "ch.admin.bar.siard2.cmd.oracle.measureNumbers";
    /** Oracle NUMBER 의 최대 정밀도. */
    static final int MAX_PRECISION = 38;
    /** 한 컬럼을 재는 질의의 제한 시간(초). */
    static final int QUERY_TIMEOUT_SECONDS = 600;
    /** 정밀도 없는 NUMERIC. 엔진은 정밀도 0 을 「정밀도 없음」으로 적는다. */
    static final int[] UNBOUNDED = new int[]{0, 0};

    private final Connection _conn;
    private final Map<String, int[]> _mapMeasured = new HashMap<>();

    OracleNumberMeasure(Connection conn) {
        _conn = conn;
    }

    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true").trim());
    }

    /**
     * 래퍼가 보고한 형식이 Oracle 의 십진 부동 소수 선언을 배정밀도로 옮긴 것인지.
     * BINARY_FLOAT · BINARY_DOUBLE 은 원래 이진 부동 소수라 해당하지 않는다.
     */
    static boolean isDecimalFloat(int iDataType, String sTypeName) {
        boolean bFloatType = (iDataType == Types.FLOAT) || (iDataType == Types.REAL) || (iDataType == Types.DOUBLE);
        if (!bFloatType || (sTypeName == null)) {
            return false;
        }
        // 래퍼는 형식 이름을 따옴표로 감싸 보고한다("NUMBER"). 따옴표를 떼고 견준다.
        String sName = sTypeName.replace("\"", "").trim();
        return "NUMBER".equalsIgnoreCase(sName) || "FLOAT".equalsIgnoreCase(sName);
    }

    /** 이 컬럼을 적을 {정밀도, 소수 자릿수}. {0, 0} 은 정밀도 없는 NUMERIC. 꺼져 있으면 null. */
    int[] measure(String sSchema, String sTable, String sColumn) {
        if (!enabled() || (_conn == null) || (sTable == null) || (sColumn == null)) {
            return null;
        }
        String sKey = sSchema + "\u0000" + sTable + "\u0000" + sColumn;
        int[] aiKnown = _mapMeasured.get(sKey);
        if (aiKnown == null) {
            aiKnown = query(sSchema, sTable, sColumn);
            _mapMeasured.put(sKey, aiKnown);
        }
        return aiKnown;
    }

    static String sql(String sSchema, String sTable, String sColumn) {
        String sCol = identifier(sColumn);
        String sFrom = ((sSchema != null) ? identifier(sSchema) + "." : "") + identifier(sTable);
        return "SELECT COUNT(s), "
                + "MAX(CASE WHEN INSTR(s, '.') > 0 THEN LENGTH(s) - INSTR(s, '.') ELSE 0 END), "
                + "MAX(CASE WHEN INSTR(s, '.') > 0 THEN INSTR(s, '.') - 1 ELSE LENGTH(s) END), "
                + "MAX(CASE WHEN INSTR(UPPER(s), 'E') > 0 THEN 1 ELSE 0 END) "
                + "FROM (SELECT TO_CHAR(ABS(" + sCol + "), 'TM9') s FROM " + sFrom
                + " WHERE " + sCol + " IS NOT NULL)";
    }

    static String identifier(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** 잰 값으로 {정밀도, 소수 자릿수} 를 정한다. 정하지 못하면 {@link #UNBOUNDED}. */
    static int[] decide(long lValues, int iMaxScale, int iMaxIntegerDigits, int iExponent) {
        if ((lValues <= 0L) || (iExponent > 0)) {
            return UNBOUNDED;
        }
        int iPrecision = Math.max(1, iMaxIntegerDigits + iMaxScale);
        if (iPrecision > MAX_PRECISION) {
            return UNBOUNDED;
        }
        return new int[]{iPrecision, iMaxScale};
    }

    /**
     * 재는 질의를 보낼 연결. 래퍼의 문장은 SQL 을 표준 문법으로 해석해 Oracle 문법으로 다시 쓰는데,
     * 이 해석기가 {@code TO_CHAR(...) s} 같은 Oracle 질의를 거부한다(2026-10-13 실측). 그래서 벤더 연결에 바로 보낸다.
     */
    private Connection nativeConnection() throws SQLException {
        if (_conn.isWrapperFor(Connection.class)) {
            Connection connNative = _conn.unwrap(Connection.class);
            if (connNative != null) {
                return connNative;
            }
        }
        return _conn;
    }

    private int[] query(String sSchema, String sTable, String sColumn) {
        String sWhere = sSchema + "." + sTable + "." + sColumn;
        try (Statement stmt = nativeConnection().createStatement()) {
            try {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            } catch (SQLException ignore) {
                // 제한 시간을 지원하지 않는 드라이버는 그대로 둔다
            }
            try (ResultSet rs = stmt.executeQuery(sql(sSchema, sTable, sColumn))) {
                if ((rs == null) || !rs.next()) {
                    LOG.warn("Oracle number column {} could not be measured; archived as NUMERIC without precision", sWhere);
                    return UNBOUNDED;
                }
                long lValues = rs.getLong(1);
                int[] aiDecided = decide(lValues, rs.getInt(2), rs.getInt(3), rs.getInt(4));
                if (aiDecided != UNBOUNDED) {
                    LOG.debug("Oracle number column {} measured over {} values: NUMERIC({},{})",
                            sWhere, lValues, aiDecided[0], aiDecided[1]);
                } else if (lValues > 0L) {
                    LOG.warn("Oracle number column {} does not fit NUMERIC(38,s); archived as NUMERIC without precision "
                            + "(values stay exact, a reload may round them)", sWhere);
                }
                return aiDecided;
            }
        } catch (SQLException | RuntimeException e) {
            LOG.warn("Oracle number column {} could not be measured; archived as NUMERIC without precision: {}", sWhere,
                    e.toString());
            return UNBOUNDED;
        }
    }
}
