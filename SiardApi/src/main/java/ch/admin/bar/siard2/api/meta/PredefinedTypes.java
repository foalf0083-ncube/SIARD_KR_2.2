// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.meta;

import ch.enterag.sqlparser.BaseSqlFactory;
import ch.enterag.sqlparser.datatype.PredefinedType;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 형식 문자열(예: {@code VARCHAR(100)}, {@code DECIMAL(12, 2)})을 해석한 결과를 문자열마다 한 번만 만든다.
 *
 * <p>메타 객체의 {@code getPreType} · {@code getLength} · {@code getScale} 은 값 하나를 처리할 때마다 여러 번
 * 불린다. 원래는 부를 때마다 SQL 파서로 같은 문자열을 다시 해석했다. 배열 · 구조형은 요소마다 불리므로
 * 공간 자료형처럼 요소가 많은 표에서 이 해석이 행마다 수십~수백 번 되풀이된다. 해석 결과는 문자열만으로
 * 정해지므로 저장해 두고 다시 써도 값이 같다. 돌려준 객체는 읽기 함수만 부르고 바꾸지 않는다.</p>
 */
final class PredefinedTypes {
    private static final int MAX_ENTRIES = 4096;
    private static final ConcurrentHashMap<String, PredefinedType> PARSED = new ConcurrentHashMap<>();

    private PredefinedTypes() {
    }

    static PredefinedType parsed(String sType) {
        PredefinedType prt = PARSED.get(sType);
        if (prt == null) {
            prt = new BaseSqlFactory().newPredefinedType();
            prt.parse(sType);
            if (PARSED.size() >= MAX_ENTRIES) PARSED.clear();
            PredefinedType previous = PARSED.putIfAbsent(sType, prt);
            if (previous != null) prt = previous;
        }
        return prt;
    }
}
