// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FetchSizeChoiceTest {
    @Test
    void automaticFetchFitsWideRowsAndSmallHeaps() {
        assertEquals(2000, PrimaryDataTransfer.automaticFetchSize(4000, 2000, 1L << 30));
        assertEquals(409, PrimaryDataTransfer.automaticFetchSize(81920, 2000, 1L << 30));
        assertEquals(256, PrimaryDataTransfer.automaticFetchSize(8192, 2000, 32L << 20));
        assertEquals(1, PrimaryDataTransfer.automaticFetchSize(64L << 20, 2000, 1L << 30));
    }
    @Test
    void configuredValueIsAppliedEvenWhenLowerThanCurrent() {
        // 현재 1,000(예: -Doracle.jdbc.defaultRowPrefetch=1000)이어도 명시값 100 을 쓴다
        assertEquals(100, PrimaryDataTransfer.chooseFetchSize(100, 1000, 500));
        assertEquals(2000, PrimaryDataTransfer.chooseFetchSize(2000, 10, 500));
    }

    @Test
    void nonPositiveConfiguredValueLeavesStatementUnchanged() {
        assertEquals(-1, PrimaryDataTransfer.chooseFetchSize(0, 10, 500));
        assertEquals(-1, PrimaryDataTransfer.chooseFetchSize(-5, 1000, 500));
    }

    @Test
    void automaticValueOnlyRaisesTheCurrentValue() {
        assertEquals(500, PrimaryDataTransfer.chooseFetchSize(null, 10, 500));
        assertEquals(-1, PrimaryDataTransfer.chooseFetchSize(null, 1000, 500));
        assertEquals(-1, PrimaryDataTransfer.chooseFetchSize(null, 500, 500));
    }
}
