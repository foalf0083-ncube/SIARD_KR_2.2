// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.utils.logging.IndentLogger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * 소수 자릿수가 비어 있는 NUMBER 컬럼(부동 소수 선언)의 실제 값을 재어, 모든 값이 들어가는 NUMERIC(p,s) 를 정한다.
 *
 * <p>왜 재는가. Tibero 는 제약 없는 NUMBER 와 FLOAT 를 사전에 똑같이 정밀도 · 소수 자릿수 없이 기록한다
 * (Tibero 7 의 FLOAT(n) 은 정밀도만 십진 자릿수로 온다). 엔진은
 * 이를 정밀도 없는 {@code NUMERIC} 으로 적는데, SQL 에서 그 형식은 소수 자릿수 0 이다. 값은 BigDecimal 로 읽혀
 * 산출물에 정확히 남지만 형식 표기가 소수를 부정하고, 되돌리면 소수 없는 NUMBER 가 만들어져 87.5 가 88 이 된다.
 * SIARD 2.2 에는 정확한 십진 부동소수 형식(DECFLOAT)이 없다. FLOAT 로 적으면 엔진이 값을 배정밀도로 읽어
 * 산출물 안의 값이 깎이므로 더 나쁘다(원본 SIARD Suite 의 Oracle 래퍼가 그렇게 한다). 그래서 값을 재어
 * 정확한 십진 형식을 정한다. 원래 선언은 {@code typeOriginal} 에 따로 남는다.
 *
 * <p>어떻게 재는가. 한 번의 집계 질의로 {@code TO_CHAR(ABS(값), 'TM9')} 의 정수부 · 소수부 자릿수 최댓값을 얻는다.
 * TM9 는 꼬리 0 없는 최소 십진 표기다(Tibero 5 SP1 실측: 87.5 -&gt; "87.5", 0.125 -&gt; ".125",
 * 27자리 원주율 그대로). 정밀도는 두 최댓값의 합이다. 서로 다른 행의 최댓값을 더하므로 모든 값이 들어간다.
 *
 * <p>정하지 못하면 지금처럼 둔다(값은 여전히 정확하다). 값이 없을 때, 지수 표기가 섞였을 때(64자를 넘는 극단값),
 * 정밀도가 38 을 넘을 때, 질의가 실패했을 때다. 재는 일이 추출을 멈추게 해서는 안 되므로 어떤 실패도 삼킨다.
 * 기록은 다른 래퍼 사건과 같은 FINER 수준이라 기본 설정에서는 보이지 않는다. 정하지 못한 컬럼은 산출물
 * {@code metadata.xml} 에서 {@code typeOriginal} 이 NUMBER 인데 형식이 괄호 없는 {@code NUMERIC} 인 것으로 알아본다.
 * Tibero 7 의 FLOAT(n) 은 {@code NUMERIC(11)} 처럼 남아 산출물만으로는 NUMBER(11) 과 가려지지 않는다.
 *
 * <p>같은 컬럼을 두 번 재지 않도록 연결 단위로 기억한다. 현장에서 표가 너무 커 재는 시간이 문제가 되면
 * {@value #PROPERTY}=false 로 끈다.
 */
final class TiberoNumberMeasure {

    /** 끄개. 기본은 켜짐. */
    static final String PROPERTY = "ch.admin.bar.siard2.jdbc.tibero.measureNumbers";
    /** Tibero NUMBER 의 최대 정밀도. */
    static final int MAX_PRECISION = 38;
    /** 한 컬럼을 재는 질의의 제한 시간(초). */
    static final int QUERY_TIMEOUT_SECONDS = 600;

    private static final IndentLogger _il = IndentLogger.getIndentLogger(TiberoNumberMeasure.class.getName());
    private static final int[] NOT_DECIDED = new int[0];

    private final Connection _conn;
    private final Map<String, int[]> _mapMeasured = new HashMap<>();

    TiberoNumberMeasure(Connection conn) {
        _conn = conn;
    }

    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true").trim());
    }

    /** 이 컬럼을 적을 {정밀도, 소수 자릿수}. 정하지 못하면 null. */
    int[] measure(String sSchema, String sTable, String sColumn) {
        if (!enabled() || (_conn == null) || (sTable == null) || (sColumn == null)) {
            return null;
        }
        String sKey = sSchema + "\u0000" + sTable + "\u0000" + sColumn;
        int[] aiKnown = _mapMeasured.get(sKey);
        if (aiKnown == null) {
            aiKnown = query(sSchema, sTable, sColumn);
            _mapMeasured.put(sKey, (aiKnown != null) ? aiKnown : NOT_DECIDED);
        }
        return (aiKnown == NOT_DECIDED) ? null : aiKnown;
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

    /** 잰 값으로 {정밀도, 소수 자릿수} 를 정한다. 정하지 못하면 null. */
    static int[] decide(long lValues, int iMaxScale, int iMaxIntegerDigits, int iExponent) {
        if ((lValues <= 0L) || (iExponent > 0)) {
            return null;
        }
        int iPrecision = Math.max(1, iMaxIntegerDigits + iMaxScale);
        if (iPrecision > MAX_PRECISION) {
            return null;
        }
        return new int[]{iPrecision, iMaxScale};
    }

    private int[] query(String sSchema, String sTable, String sColumn) {
        String sWhere = sSchema + "." + sTable + "." + sColumn;
        try (Statement stmt = _conn.createStatement()) {
            try {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            } catch (SQLException ignore) {
                // 제한 시간을 지원하지 않는 드라이버는 그대로 둔다
            }
            try (ResultSet rs = stmt.executeQuery(sql(sSchema, sTable, sColumn))) {
                if ((rs == null) || !rs.next()) {
                    return null;
                }
                long lValues = rs.getLong(1);
                int[] aiDecided = decide(lValues, rs.getInt(2), rs.getInt(3), rs.getInt(4));
                if (aiDecided != null) {
                    _il.event("Tibero NUMBER " + sWhere + " has no declared precision; measured "
                            + lValues + " values, archived as NUMERIC(" + aiDecided[0] + "," + aiDecided[1] + ")");
                } else if (lValues > 0L) {
                    _il.event("Tibero NUMBER " + sWhere + " has no declared precision and could not be fitted "
                            + "into NUMERIC(38,s); kept without precision (values stay exact)");
                }
                return aiDecided;
            }
        } catch (SQLException | RuntimeException e) {
            _il.event("Tibero NUMBER " + sWhere + " could not be measured; kept without precision: " + e);
            return null;
        }
    }
}
