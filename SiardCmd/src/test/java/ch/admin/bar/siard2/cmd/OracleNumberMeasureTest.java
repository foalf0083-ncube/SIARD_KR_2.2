// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import org.junit.jupiter.api.Test;

import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle 의 십진 부동 소수 컬럼을 재어 NUMERIC(p,s) 를 정하는 규칙.
 */
class OracleNumberMeasureTest {

    @Test
    void onlyDecimalFloatsReportedAsFloatAreMeasured() {
        // 래퍼 2.2.1 실측(2026-10-13): 정밀도 없는 NUMBER 와 FLOAT 는 DATA_TYPE FLOAT(6) 로 오고,
        // 형식 이름은 따옴표로 감싸여 온다.
        assertTrue(OracleNumberMeasure.isDecimalFloat(Types.FLOAT, "\"NUMBER\""));
        assertTrue(OracleNumberMeasure.isDecimalFloat(Types.FLOAT, "\"FLOAT\""));
        assertFalse(OracleNumberMeasure.isDecimalFloat(Types.DOUBLE, "\"BINARY_DOUBLE\""));
        assertFalse(OracleNumberMeasure.isDecimalFloat(Types.FLOAT, null));
        assertTrue(OracleNumberMeasure.isDecimalFloat(Types.FLOAT, "NUMBER"));
        assertTrue(OracleNumberMeasure.isDecimalFloat(Types.FLOAT, "FLOAT"));
        assertTrue(OracleNumberMeasure.isDecimalFloat(Types.DOUBLE, "number"));
        // BINARY_DOUBLE 은 원래 배정밀도라 그대로 둔다.
        assertFalse(OracleNumberMeasure.isDecimalFloat(Types.DOUBLE, "BINARY_DOUBLE"));
        assertFalse(OracleNumberMeasure.isDecimalFloat(Types.REAL, "BINARY_FLOAT"));
        // 고정 소수 NUMBER 는 래퍼가 이미 정확한 형식으로 보고한다.
        assertFalse(OracleNumberMeasure.isDecimalFloat(Types.DECIMAL, "NUMBER"));
        assertFalse(OracleNumberMeasure.isDecimalFloat(Types.BIGINT, "NUMBER"));
    }

    @Test
    void precisionIsTheSumOfTheLargestIntegerAndFractionDigits() {
        // 4113510300100010001(19자리) 과 87.5 가 섞이면 정수부 최대 19, 소수부 최대 1 -> NUMERIC(20,1)
        assertArrayEquals(new int[]{20, 1}, OracleNumberMeasure.decide(2, 1, 19, 0));
        // 0.125 는 TM9 가 ".125" -> 정수부 0, 소수부 3 -> NUMERIC(3,3)
        assertArrayEquals(new int[]{3, 3}, OracleNumberMeasure.decide(1, 3, 0, 0));
        // 0 뿐이면 "0" -> NUMERIC(1,0)
        assertArrayEquals(new int[]{1, 0}, OracleNumberMeasure.decide(1, 0, 1, 0));
        assertArrayEquals(new int[]{38, 2}, OracleNumberMeasure.decide(1, 2, 36, 0), "38 은 된다");
    }

    @Test
    void undecidableColumnsBecomeNumericWithoutPrecision() {
        // 배정밀도로 깎지 않고 정밀도 없는 NUMERIC 으로 적어 값을 그대로 남긴다.
        assertSame(OracleNumberMeasure.UNBOUNDED, OracleNumberMeasure.decide(0, 0, 0, 0), "값이 없다");
        assertSame(OracleNumberMeasure.UNBOUNDED, OracleNumberMeasure.decide(3, 0, 1, 1), "지수 표기(1E-100)");
        assertSame(OracleNumberMeasure.UNBOUNDED, OracleNumberMeasure.decide(3, 30, 9, 0), "38자리를 넘는다");
    }

    @Test
    void theQueryQuotesIdentifiersAndUsesTheMinimalDecimalFormat() {
        String sql = OracleNumberMeasure.sql("CASSCM", "TBA_SLRM_AOCR_H", "AOCR\"QN");
        assertTrue(sql.contains("FROM \"CASSCM\".\"TBA_SLRM_AOCR_H\""), sql);
        assertTrue(sql.contains("ABS(\"AOCR\"\"QN\")"), sql);
        assertTrue(sql.contains("'TM9'"), sql);
        assertTrue(sql.contains("IS NOT NULL"), sql);
    }

    @Test
    void theSwitchRestoresTheWrapperBehaviour() {
        String sOld = System.getProperty(OracleNumberMeasure.PROPERTY);
        try {
            System.setProperty(OracleNumberMeasure.PROPERTY, "false");
            assertNull(new OracleNumberMeasure(null).measure("S", "T", "C"));
        } finally {
            if (sOld == null) {
                System.clearProperty(OracleNumberMeasure.PROPERTY);
            } else {
                System.setProperty(OracleNumberMeasure.PROPERTY, sOld);
            }
        }
    }
}
