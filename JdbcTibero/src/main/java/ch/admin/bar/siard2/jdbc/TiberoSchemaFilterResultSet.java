// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 시스템 스키마 행을 걸러내는 메타데이터 결과집합 래퍼. {@code getTables}와 {@code getSchemas}에 쓴다.
 * Tibero 드라이버는 시스템 스키마의 객체도 함께 돌려주므로 현행 엔진이 제외하던 규칙을 옮겼다.
 */
public class TiberoSchemaFilterResultSet extends TiberoResultSet {

    private final int _iIndex;

    public TiberoSchemaFilterResultSet(ResultSet rsWrapped, TiberoConnection conn, String sSchemaLabel) {
        super(rsWrapped, conn);
        _iIndex = TiberoOrdinalResultSet.findColumnSafe(rsWrapped, sSchemaLabel);
    }

    @Override
    public boolean next() throws SQLException {
        while (super.next()) {
            if (_iIndex <= 0) {
                return true;
            }
            if (!TiberoDatabaseMetaData.isSystemSchema(getString(_iIndex))) {
                return true;
            }
        }
        return false;
    }
}
