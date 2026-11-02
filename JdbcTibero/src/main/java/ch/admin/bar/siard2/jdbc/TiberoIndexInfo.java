// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@code getIndexInfo} 결과 래퍼. 실제 컬럼이 아닌 행을 걸러낸다.
 *
 * <p>Tibero 6 실측(2026-09-08): 구체화 뷰의 스냅숏 인덱스처럼 식(expression)에 걸린 인덱스는 COLUMN_NAME 을
 * 자리표시자 {@code - EXPRESSION COLUMN -} 로 돌려준다. 엔진은 이 값을 그대로 후보키 컬럼으로 삼아
 * 존재하지 않는 컬럼을 가리키는 후보키를 만들고, 그 산출물을 재적재하면 이름이 풀리지 않아 멈춘다.
 * 통계 행(TYPE 0)은 컬럼 이름이 없는 것이 정상이므로 걸러내지 않는다.
 */
public class TiberoIndexInfo extends TiberoResultSet {

    private static final short sTYPE_STATISTIC = 0;
    private static final String sEXPRESSION_PLACEHOLDER = "- EXPRESSION COLUMN -";

    private final int _iColumnName;
    private final int _iType;

    public TiberoIndexInfo(ResultSet rsWrapped, TiberoConnection conn) {
        super(rsWrapped, conn);
        _iColumnName = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "COLUMN_NAME");
        _iType = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, "TYPE");
    }

    /** 컬럼 이름 자리에 자리표시자가 온 인덱스 행인지. */
    static boolean isExpressionPlaceholder(String sColumnName) {
        if (sColumnName == null) {
            return false;
        }
        String s = sColumnName.trim();
        return s.startsWith("-") && s.endsWith("-") && s.toUpperCase().contains("EXPRESSION");
    }

    @Override
    public boolean next() throws SQLException {
        while (super.next()) {
            if ((_iColumnName <= 0) || (_iType > 0 && super.getShort(_iType) == sTYPE_STATISTIC)) {
                return true;
            }
            if (!isExpressionPlaceholder(super.getString(_iColumnName))) {
                return true;
            }
        }
        return false;
    }
}
