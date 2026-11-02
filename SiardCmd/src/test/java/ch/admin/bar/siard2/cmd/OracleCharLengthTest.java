// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle 문자 의미 컬럼의 선언 길이를 사전에서 읽는 규칙.
 */
class OracleCharLengthTest {

    @Test
    void characterTypesAreRecognisedWithOrWithoutQuotes() {
        // 래퍼 2.2.1 은 형식 이름을 따옴표로 감싸 보고한다.
        assertTrue(OracleCharLength.isCharacterType("\"VARCHAR2\""));
        assertTrue(OracleCharLength.isCharacterType("\"CHAR\""));
        assertTrue(OracleCharLength.isCharacterType("\"NVARCHAR2\""));
        assertTrue(OracleCharLength.isCharacterType("nchar"));
        assertFalse(OracleCharLength.isCharacterType("\"NUMBER\""));
        assertFalse(OracleCharLength.isCharacterType("\"CLOB\""));
        assertFalse(OracleCharLength.isCharacterType(null));
    }

    @Test
    void theDictionaryQueryQuotesNamesAndKeepsOnlyCharacterSemantics() {
        String sql = OracleCharLength.sql("MIN'LAB", "한글테이블");
        assertTrue(sql.contains("OWNER = 'MIN''LAB'"), sql);
        assertTrue(sql.contains("TABLE_NAME = '한글테이블'"), sql);
        assertTrue(sql.contains("CHAR_USED = 'C'"), sql);
    }

    @Test
    void withoutAConnectionTheReportedSizeIsKept() {
        assertNull(new OracleCharLength(null).of("S", "T", "C"));
    }
}
