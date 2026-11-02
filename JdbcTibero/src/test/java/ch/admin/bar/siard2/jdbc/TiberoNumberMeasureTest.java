// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 제약 없는 NUMBER 의 값을 재어 NUMERIC(p,s) 를 정하는 규칙.
 */
class TiberoNumberMeasureTest {

    @Test
    void precisionIsTheSumOfTheLargestIntegerAndFractionDigits() {
        // 87.5 와 1234.125 가 섞이면 정수부 최대 4, 소수부 최대 3 -> NUMERIC(7,3)
        assertArrayEquals(new int[]{7, 3}, TiberoNumberMeasure.decide(2, 3, 4, 0));
    }

    @Test
    void integersGetScaleZeroAndAtLeastOneDigit() {
        assertArrayEquals(new int[]{3, 0}, TiberoNumberMeasure.decide(5, 0, 3, 0));
        // 값이 0 뿐이면 TM9 가 "0" -> 정수부 1자리
        assertArrayEquals(new int[]{1, 0}, TiberoNumberMeasure.decide(1, 0, 1, 0));
        // 0.5 는 TM9 가 ".5" -> 정수부 0, 소수부 1 -> NUMERIC(1,1)
        assertArrayEquals(new int[]{1, 1}, TiberoNumberMeasure.decide(1, 1, 0, 0));
    }

    @Test
    void undecidableCasesAreLeftAsTheyAre() {
        assertNull(TiberoNumberMeasure.decide(0, 0, 0, 0), "값이 없다");
        assertNull(TiberoNumberMeasure.decide(3, 2, 5, 1), "지수 표기가 섞였다");
        assertNull(TiberoNumberMeasure.decide(3, 30, 9, 0), "정밀도가 38 을 넘는다");
        assertArrayEquals(new int[]{38, 30}, TiberoNumberMeasure.decide(3, 30, 8, 0), "38 은 된다");
    }

    @Test
    void theQueryQuotesIdentifiersAndUsesTheMinimalDecimalFormat() {
        String sql = TiberoNumberMeasure.sql("MINLAB", "한글테이블", "점\"수");
        assertTrue(sql.contains("FROM \"MINLAB\".\"한글테이블\""), sql);
        assertTrue(sql.contains("ABS(\"점\"\"수\")"), sql);
        assertTrue(sql.contains("'TM9'"), sql);
        assertTrue(sql.contains("IS NOT NULL"), sql);
    }

    @Test
    void theSwitchTurnsMeasuringOff() {
        String sOld = System.getProperty(TiberoNumberMeasure.PROPERTY);
        try {
            System.setProperty(TiberoNumberMeasure.PROPERTY, "false");
            TiberoNumberMeasure m = new TiberoNumberMeasure(null);
            assertNull(m.measure("S", "T", "C"));
        } finally {
            if (sOld == null) {
                System.clearProperty(TiberoNumberMeasure.PROPERTY);
            } else {
                System.setProperty(TiberoNumberMeasure.PROPERTY, sOld);
            }
        }
    }
}
