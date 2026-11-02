// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiberoDatabaseMetaDataTest {

    private static final String[] COLUMN_LABELS = {
            "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "ORDINAL_POSITION"};

    private TiberoDatabaseMetaData wrap(java.util.Map<String, ResultSet> byMethod, int major) {
        return wrap(byMethod, major, null);
    }

    private TiberoDatabaseMetaData wrap(java.util.Map<String, ResultSet> byMethod, int major, ResultSet queryResult) {
        FakeJdbc.ConnectionSpy spy = FakeJdbc.connection(null, queryResult);
        TiberoConnection conn = new TiberoConnection(spy.proxy);
        DatabaseMetaData dmd = FakeJdbc.databaseMetaData(byMethod, major, spy.proxy);
        return new TiberoDatabaseMetaData(dmd, conn);
    }

    @Test
    void viewRowsExposeQueryTextFromAllViews() throws SQLException {
        // Tibero 6 실측: getTables 에 QUERY_TEXT 가 없어 뷰가 정의 없이 보존됐다. 뷰 행에서만 ALL_VIEWS 를 읽는다.
        FakeJdbc.Rows tables = new FakeJdbc.Rows("TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE")
                .row("APP", "T1", "TABLE")
                .row("SYSTEM", "X", "TABLE")
                .row("APP", "V1", "VIEW");
        ResultSet viewText = FakeJdbc.resultSet(new FakeJdbc.Rows("TEXT").row("SELECT 1 FROM DUAL"));
        ResultSet rs = wrap(FakeJdbc.byMethod("getTables", FakeJdbc.resultSet(tables)), 6, viewText)
                .getTables(null, "APP", "%", null);
        assertTrue(rs.next());
        assertEquals("T1", rs.getString("TABLE_NAME"));
        assertThrows(SQLException.class, () -> rs.getString("QUERY_TEXT"));
        assertTrue(rs.next());
        assertEquals("V1", rs.getString("TABLE_NAME"));
        assertEquals("SELECT 1 FROM DUAL", rs.getString("QUERY_TEXT"));
        assertEquals("SELECT 1 FROM DUAL", rs.getObject("QUERY_TEXT"));
        assertFalse(rs.wasNull());
        assertFalse(rs.next());
    }

    @Test
    void columnOrdinalIsShiftedToOneBased() throws SQLException {
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_LABELS)
                .row("APP", "T", "A", Types.VARCHAR, "VARCHAR2", 10, 0)
                .row("APP", "T", "B", Types.INTEGER, "NUMBER", 10, 1)
                .row("APP", "T", "C", Types.DATE, "DATE", 7, 2);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6)
                .getColumns(null, "APP", "T", "%");
        List<Integer> positions = new ArrayList<>();
        while (rs.next()) {
            positions.add(rs.getInt("ORDINAL_POSITION"));
            assertEquals(Integer.valueOf(positions.get(positions.size() - 1)), rs.getObject("ORDINAL_POSITION"));
        }
        assertEquals(List.of(1, 2, 3), positions);
    }

    @Test
    void duplicateColumnRowsOfMaterializedViewsAreSkipped() throws SQLException {
        // Tibero 6 실측: 구체화 뷰의 컬럼이 뷰 객체와 저장 테이블 몫으로 두 번 나온다.
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_LABELS)
                .row("APP", "MV_STAT", "TYPE_CD", Types.VARCHAR, "VARCHAR2", 10, 0)
                .row("APP", "MV_STAT", "CNT", Types.NUMERIC, "NUMBER", 10, 1)
                .row("APP", "MV_STAT", "TYPE_CD", Types.VARCHAR, "VARCHAR2", 10, 0)
                .row("APP", "MV_STAT", "CNT", Types.NUMERIC, "NUMBER", 10, 1)
                .row("APP", "T2", "CNT", Types.NUMERIC, "NUMBER", 10, 0);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6)
                .getColumns(null, "APP", "%", "%");
        List<String> seen = new ArrayList<>();
        while (rs.next()) seen.add(rs.getString("TABLE_NAME") + "." + rs.getString("COLUMN_NAME") + "#" + rs.getInt("ORDINAL_POSITION"));
        assertEquals(List.of("MV_STAT.TYPE_CD#1", "MV_STAT.CNT#2", "T2.CNT#1"), seen);
    }

    private static final String[] PROCEDURE_COLUMN_LABELS = {
            "PROCEDURE_SCHEM", "PROCEDURE_NAME", "COLUMN_NAME", "COLUMN_TYPE", "DATA_TYPE", "TYPE_NAME",
            "ORDINAL_POSITION", "SPECIFIC_NAME"};

    @Test
    void procedureParameterOrdinalsAreCountedWhenDriverGivesNone() throws SQLException {
        // Tibero 6 실측: ORDINAL_POSITION 이 모두 NULL, 반환값 행은 COLUMN_TYPE 5, TIMESTAMP 매개변수는 OTHER.
        FakeJdbc.Rows rows = new FakeJdbc.Rows(PROCEDURE_COLUMN_LABELS)
                .row("APP", "FN", null, 5, Types.NUMERIC, "NUMBER", null, null)
                .row("APP", "FN", "P_RECV", 1, Types.OTHER, "TIMESTAMP(6)", null, null)
                .row("APP", "FN", "P_DUE", 1, Types.TIMESTAMP, "DATE", null, null)
                .row("APP", "SP", "P_DT", 1, Types.TIMESTAMP, "DATE", null, null)
                .row("APP", "SP2", "A", 1, Types.INTEGER, "NUMBER", 7, null);
        ResultSet rs = wrap(FakeJdbc.byMethod("getProcedureColumns", FakeJdbc.resultSet(rows)), 6)
                .getProcedureColumns(null, "APP", "%", "%");
        List<String> seen = new ArrayList<>();
        while (rs.next()) {
            seen.add(rs.getString("PROCEDURE_NAME") + "." + rs.getString("COLUMN_NAME")
                    + "#" + rs.getInt("ORDINAL_POSITION") + ":" + rs.getInt("DATA_TYPE"));
            assertEquals(Integer.valueOf(rs.getInt("ORDINAL_POSITION")), rs.getObject("ORDINAL_POSITION"));
        }
        assertEquals(List.of("FN.null#0:" + Types.NUMERIC, "FN.P_RECV#1:" + Types.TIMESTAMP,
                "FN.P_DUE#2:" + Types.TIMESTAMP, "SP.P_DT#1:" + Types.TIMESTAMP, "SP2.A#7:" + Types.INTEGER), seen);
    }

    @Test
    void functionColumnTypeLabelTypoIsAliased() throws SQLException {
        // Tibero 6 실측: getFunctionColumns 가 COLUMN_TYPE 을 COLUMN_TPYE 로 적는다.
        FakeJdbc.Rows rows = new FakeJdbc.Rows("FUNCTION_SCHEM", "FUNCTION_NAME", "COLUMN_NAME", "COLUMN_TPYE",
                "DATA_TYPE", "TYPE_NAME", "ORDINAL_POSITION", "SPECIFIC_NAME")
                .row("APP", "FN", null, 5, Types.NUMERIC, "NUMBER", null, null)
                .row("APP", "FN", "P1", 1, Types.VARCHAR, "VARCHAR2", null, null);
        ResultSet rs = wrap(FakeJdbc.byMethod("getFunctionColumns", FakeJdbc.resultSet(rows)), 6)
                .getFunctionColumns(null, "APP", "FN", "%");
        assertTrue(rs.next());
        assertEquals(5, rs.getInt("COLUMN_TYPE"));
        assertEquals(0, rs.getInt("ORDINAL_POSITION"));
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("COLUMN_TYPE"));
        assertEquals(1, rs.getInt("ORDINAL_POSITION"));
        assertThrows(SQLException.class, () -> rs.findColumn("NO_SUCH_LABEL"));
    }

    @Test
    void expressionIndexRowsAreDropped() throws SQLException {
        // Tibero 6 실측: 구체화 뷰의 스냅숏 인덱스가 COLUMN_NAME 자리에 자리표시자를 돌려준다.
        FakeJdbc.Rows rows = new FakeJdbc.Rows("TABLE_SCHEM", "TABLE_NAME", "INDEX_NAME", "TYPE", "ORDINAL_POSITION", "COLUMN_NAME")
                .row("APP", "T", null, (short) 0, 0, null)
                .row("APP", "T", "UQ_T_1", (short) 3, 1, "A")
                .row("APP", "MV_STAT", "I_SNAP$_MV_STAT", (short) 1, 1, "- EXPRESSION COLUMN -")
                .row("APP", "T", "UQ_T_1", (short) 3, 2, "B");
        ResultSet rs = wrap(FakeJdbc.byMethod("getIndexInfo", FakeJdbc.resultSet(rows)), 6)
                .getIndexInfo(null, "APP", "%", true, false);
        List<String> seen = new ArrayList<>();
        while (rs.next()) seen.add(rs.getString("TABLE_NAME") + "." + rs.getString("COLUMN_NAME"));
        assertEquals(List.of("T.null", "T.A", "T.B"), seen);
    }

    @Test
    void knownTypeCodesPassThroughUnchanged() throws SQLException {
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_LABELS)
                .row("APP", "T", "A", Types.VARCHAR, "VARCHAR2", 10, 0)
                .row("APP", "T", "B", Types.ARRAY, "ARR", 0, 1)
                .row("APP", "T", "C", Types.OTHER, "INTERVAL", 0, 2);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6)
                .getColumns(null, "APP", "T", "%");
        rs.next();
        assertEquals(Types.VARCHAR, rs.getInt("DATA_TYPE"));
        rs.next();
        assertEquals(Types.ARRAY, rs.getInt("DATA_TYPE"));
        rs.next();
        assertEquals(Types.OTHER, rs.getInt("DATA_TYPE"));
    }

    @Test
    void unknownTypeCodesAreMappedByCodeThenByName() throws SQLException {
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_LABELS)
                .row("APP", "T", "L", Types.LONGVARCHAR, "LONG", 0, 0)
                .row("APP", "T", "R", Types.LONGVARBINARY, "LONG RAW", 0, 1)
                .row("APP", "T", "X", 9999, "BFILE", 0, 2)
                .row("APP", "T", "F", 100, "BINARY_FLOAT", 0, 3);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6)
                .getColumns(null, "APP", "T", "%");
        rs.next();
        assertEquals(Types.CLOB, rs.getInt("DATA_TYPE"));
        rs.next();
        assertEquals(Types.BLOB, rs.getInt("DATA_TYPE"));
        rs.next();
        assertEquals(Types.BLOB, rs.getInt("DATA_TYPE"));
        rs.next();
        assertEquals(Types.REAL, rs.getInt("DATA_TYPE"));
    }

    @Test
    void unresolvableTypeCodeFailsWithLocation() throws SQLException {
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_LABELS)
                .row("APP", "T", "Z", 9999, "MYSTERY", 0, 0);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6)
                .getColumns(null, "APP", "T", "%");
        rs.next();
        SQLException se = assertThrows(SQLException.class, () -> rs.getInt("DATA_TYPE"));
        assertTrue(se.getMessage().contains("APP.T.Z"), se.getMessage());
        assertTrue(se.getMessage().contains("9999"), se.getMessage());
    }

    @Test
    void primaryKeySequenceIsShiftedToOneBased() throws SQLException {
        FakeJdbc.Rows rows = new FakeJdbc.Rows("TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ", "PK_NAME")
                .row("APP", "T", "B", 1, "PK_T")
                .row("APP", "T", "A", 0, "PK_T");
        ResultSet rs = wrap(FakeJdbc.byMethod("getPrimaryKeys", FakeJdbc.resultSet(rows)), 6)
                .getPrimaryKeys(null, "APP", "T");
        rs.next();
        assertEquals(2, rs.getInt("KEY_SEQ"));
        rs.next();
        assertEquals(1, rs.getInt("KEY_SEQ"));
    }

    @Test
    void foreignKeySequencesAreShiftedToOneBased() throws SQLException {
        // Tibero 6 실측(2026-09-08): getImportedKeys · getExportedKeys 의 KEY_SEQ 는 0부터 시작한다.
        FakeJdbc.Rows imported = new FakeJdbc.Rows("FKTABLE_NAME", "FKCOLUMN_NAME", "KEY_SEQ", "FK_NAME")
                .row("T", "A", 0, "FK1")
                .row("T", "B", 1, "FK1");
        FakeJdbc.Rows exported = new FakeJdbc.Rows("FKTABLE_NAME", "FKCOLUMN_NAME", "KEY_SEQ", "FK_NAME")
                .row("U", "X", 0, "FK9");
        TiberoDatabaseMetaData dmd = wrap(FakeJdbc.byMethod(
                "getImportedKeys", FakeJdbc.resultSet(imported), "getExportedKeys", FakeJdbc.resultSet(exported)), 6);
        ResultSet rs = dmd.getImportedKeys(null, "APP", "T");
        rs.next();
        assertEquals(1, rs.getInt("KEY_SEQ"));
        rs.next();
        assertEquals(2, rs.getInt("KEY_SEQ"));
        rs = dmd.getExportedKeys(null, "APP", "T");
        rs.next();
        assertEquals(1, rs.getInt("KEY_SEQ"));
    }

    @Test
    void indexOrdinalIsNotShifted() throws SQLException {
        // Tibero 6 실측: getIndexInfo 는 통계 행(TYPE 0)만 0 이고 실제 컬럼 순번은 1부터다. 보정하지 않는다.
        FakeJdbc.Rows rows = new FakeJdbc.Rows("INDEX_NAME", "COLUMN_NAME", "ORDINAL_POSITION", "TYPE")
                .row(null, null, 0, 0)
                .row("T_PK", "A", 1, 1);
        ResultSet rs = wrap(FakeJdbc.byMethod("getIndexInfo", FakeJdbc.resultSet(rows)), 6)
                .getIndexInfo(null, "APP", "T", true, false);
        rs.next();
        assertEquals(0, rs.getInt("ORDINAL_POSITION"));
        rs.next();
        assertEquals(1, rs.getInt("ORDINAL_POSITION"));
    }

    @Test
    void systemSchemasAreFilteredFromTablesAndSchemas() throws SQLException {
        FakeJdbc.Rows tables = new FakeJdbc.Rows("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE")
                .row(null, "SYS", "DUAL", "TABLE")
                .row(null, "SYSCAT", "X", "TABLE")
                .row(null, "OUTLN", "OL$", "TABLE")
                .row(null, "LBACSYS", "ALL_SA_LABELS", "VIEW")   // Tibero 7 레이블 보안 스키마
                .row(null, "APP", "T1", "TABLE")
                .row(null, null, "T2", "TABLE")
                .row(null, "APP", "V1", "VIEW");
        FakeJdbc.Rows schemas = new FakeJdbc.Rows("TABLE_SCHEM", "TABLE_CATALOG")
                .row("SYS", null).row("APP", null).row("OUTLN", null).row("LBACSYS", null).row("USR", null);
        TiberoDatabaseMetaData dmd = wrap(FakeJdbc.byMethod(
                "getTables", FakeJdbc.resultSet(tables), "getSchemas", FakeJdbc.resultSet(schemas)), 6);

        List<String> tableNames = new ArrayList<>();
        ResultSet rs = dmd.getTables(null, "%", "%", new String[]{"TABLE", "VIEW"});
        while (rs.next()) tableNames.add(rs.getString("TABLE_NAME"));
        assertEquals(List.of("T1", "T2", "V1"), tableNames);

        List<String> schemaNames = new ArrayList<>();
        rs = dmd.getSchemas();
        while (rs.next()) schemaNames.add(rs.getString("TABLE_SCHEM"));
        assertEquals(List.of("APP", "USR"), schemaNames);
    }

    @Test
    void patternUsesStandardEscapingAndConnectionIsTheWrapper() throws SQLException {
        // 실측: 드라이버가 이스케이프 문자를 지원하므로 기반 클래스의 표준 변환을 쓴다(가짜는 역슬래시를 돌려준다).
        TiberoDatabaseMetaData dmd = wrap(FakeJdbc.byMethod(), 7);
        assertEquals("A\\_B\\%", dmd.toPattern("A_B%"));
        assertNull(dmd.toPattern(null));
        assertTrue(dmd.getConnection() instanceof TiberoConnection);
        assertEquals(7, dmd.getTibeMajorVersion());
        assertSame(dmd.getConnection(), dmd.getConnection());
    }

    @Test
    void nullOrdinalStaysNull() throws SQLException {
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_LABELS)
                .row("APP", "T", "A", Types.VARCHAR, "VARCHAR2", 10, null);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6)
                .getColumns(null, "APP", "T", "%");
        rs.next();
        assertEquals(0, rs.getInt("ORDINAL_POSITION"));
        assertTrue(rs.wasNull());
        assertFalse(rs.isClosed());
        rs.close();
        assertTrue(rs.isClosed());
    }

    private static final String[] COLUMN_SIZE_LABELS = {
            "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "DECIMAL_DIGITS",
            "ORDINAL_POSITION"};

    @Test
    void numberDeclaredWithMaxPrecisionIsReportedWithPrecision38() throws SQLException {
        // Tibero 5 SP1 · 6.7 드라이버 실측(2026-09-29): 선언 정밀도가 38 이면 정밀도를 비워 보고한다.
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_SIZE_LABELS)
                .row("APP", "T", "D38", Types.NUMERIC, "NUMBER", null, 10, 0)    // NUMBER(38,10)
                .row("APP", "T", "I38", Types.NUMERIC, "NUMBER", null, 0, 1)     // NUMBER(38)
                .row("APP", "T", "D37", Types.NUMERIC, "NUMBER", 37, 2, 2)       // NUMBER(37,2)
                .row("APP", "T", "ANY", Types.NUMERIC, "NUMBER", null, null, 3)  // NUMBER (제약 없음)
                .row("APP", "T", "F", Types.FLOAT, "FLOAT", null, null, 4)       // FLOAT
                .row("APP", "T", "S", Types.VARCHAR, "VARCHAR", 20, 0, 5);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6)
                .getColumns(null, "APP", "T", "%");
        List<String> seen = new ArrayList<>();
        while (rs.next()) {
            long lSize = rs.getLong("COLUMN_SIZE");
            String sSize = rs.wasNull() ? "NULL" : String.valueOf(lSize);
            seen.add(rs.getString("COLUMN_NAME") + "=" + sSize + "/" + rs.getObject("COLUMN_SIZE")
                    + "/" + rs.getString("COLUMN_SIZE") + "/" + rs.getInt("DECIMAL_DIGITS"));
        }
        assertEquals(List.of(
                "D38=38/38/38/10",
                "I38=38/38/38/0",
                "D37=37/37/37/2",
                "ANY=NULL/null/null/0",
                "F=NULL/null/null/0",
                "S=20/20/20/0"), seen);
    }

    @Test
    void unconstrainedNumberIsReportedWithMeasuredPrecisionAndScale() throws SQLException {
        // 제약 없는 NUMBER(RMES 의 FLOAT · NUMBER 점수 컬럼)는 값을 재어 보고한다.
        // 측정 질의 결과: 값 2개, 소수부 최대 3, 정수부 최대 2, 지수 표기 없음 -> NUMERIC(5,3)
        FakeJdbc.Rows measured = new FakeJdbc.Rows("N", "MAX_SCALE", "MAX_INT", "EXP").row(2L, 3, 2, 0);
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_SIZE_LABELS)
                .row("APP", "SCORE", "EVL_SCORE", Types.NUMERIC, "NUMBER", null, null, 0);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6, FakeJdbc.resultSet(measured))
                .getColumns(null, "APP", "SCORE", "%");
        rs.next();
        long lSize = rs.getLong("COLUMN_SIZE");
        boolean bSizeNull = rs.wasNull();
        int iDigits = rs.getInt("DECIMAL_DIGITS");
        boolean bDigitsNull = rs.wasNull();
        // 두 칸을 읽었어도 측정 질의 결과는 한 번만 소비된다(두 번째는 기억한 값을 쓴다).
        assertEquals(5L, lSize);
        assertFalse(bSizeNull);
        assertEquals(3, iDigits);
        assertFalse(bDigitsNull);
        assertEquals(Integer.valueOf(5), rs.getObject("COLUMN_SIZE"));
        assertEquals("3", rs.getString("DECIMAL_DIGITS"));
    }

    @Test
    void floatWithDecimalPrecisionButNoScaleIsMeasured() throws SQLException {
        // Tibero 7.2.5 실측(2026-09-30): FLOAT(38) 은 정밀도 11, 소수 자릿수 NULL 로 온다.
        // 그대로 두면 엔진이 NUMERIC(11) 로 적어 재적재에서 3.5 가 4 가 된다. 값을 재어 NUMERIC(2,1) 로 보고한다.
        FakeJdbc.Rows measured = new FakeJdbc.Rows("N", "MAX_SCALE", "MAX_INT", "EXP").row(1L, 1, 1, 0);
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_SIZE_LABELS)
                .row("APP", "T", "F38", Types.NUMERIC, "NUMBER", 11, null, 0);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6, FakeJdbc.resultSet(measured))
                .getColumns(null, "APP", "T", "%");
        rs.next();
        assertEquals(2, rs.getInt("COLUMN_SIZE"));
        assertFalse(rs.wasNull());
        assertEquals(1, rs.getInt("DECIMAL_DIGITS"));
        assertFalse(rs.wasNull());
    }

    @Test
    void floatWithDecimalPrecisionKeepsItsReportWhenItCannotBeMeasured() throws SQLException {
        FakeJdbc.Rows empty = new FakeJdbc.Rows("N", "MAX_SCALE", "MAX_INT", "EXP");
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_SIZE_LABELS)
                .row("APP", "T", "F38", Types.NUMERIC, "NUMBER", 11, null, 0);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6, FakeJdbc.resultSet(empty))
                .getColumns(null, "APP", "T", "%");
        rs.next();
        assertEquals(11, rs.getInt("COLUMN_SIZE"));
        assertFalse(rs.wasNull(), "보고된 정밀도는 NULL 이 아니다");
        assertNull(rs.getObject("DECIMAL_DIGITS"));
        assertTrue(rs.wasNull());
    }

    @Test
    void characterSemanticColumnsAreReportedWithDeclaredCharacters() throws SQLException {
        // Tibero 7.2.5 실측(2026-09-30): 문자 의미 컬럼의 COLUMN_SIZE 가 바이트 길이로 온다(UTF8, 한 글자 최대 4바이트).
        // 사전(ALL_TAB_COLUMNS, CHAR_USED = 'C')의 CHAR_LENGTH 로 바꾼다. 바이트 의미 컬럼은 사전 결과에 없어 그대로다.
        FakeJdbc.Rows dictionary = new FakeJdbc.Rows("COLUMN_NAME", "CHAR_LENGTH")
                .row("VC_C", 300).row("CH_C", 1).row("NVC", 100);
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_SIZE_LABELS)
                .row("APP", "T", "VC_C", Types.VARCHAR, "VARCHAR", 1200, 0, 0)   // VARCHAR2(300 CHAR)
                .row("APP", "T", "VC_B", Types.VARCHAR, "VARCHAR", 300, 0, 1)    // VARCHAR2(300 BYTE)
                .row("APP", "T", "CH_C", Types.CHAR, "CHAR", 4, 0, 2)            // CHAR(1 CHAR)
                .row("APP", "T", "NVC", Types.NVARCHAR, "NVARCHAR", 200, 0, 3)   // NVARCHAR2(100)
                .row("APP", "T", "N", Types.NUMERIC, "NUMBER", 10, 2, 4);        // NUMBER(10,2)
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 7, FakeJdbc.resultSet(dictionary))
                .getColumns(null, "APP", "T", "%");
        List<String> seen = new ArrayList<>();
        while (rs.next()) {
            seen.add(rs.getString("COLUMN_NAME") + "=" + rs.getInt("COLUMN_SIZE") + "/" + rs.getObject("COLUMN_SIZE")
                    + "/" + rs.getString("COLUMN_SIZE"));
        }
        assertEquals(List.of("VC_C=300/300/300", "VC_B=300/300/300", "CH_C=1/1/1", "NVC=100/100/100", "N=10/10/10"),
                seen);
    }

    @Test
    void characterSizesStayAsReportedWhenTheDictionaryCannotBeRead() throws SQLException {
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_SIZE_LABELS)
                .row("APP", "T", "VC_C", Types.VARCHAR, "VARCHAR", 1200, 0, 0);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 7)
                .getColumns(null, "APP", "T", "%");
        rs.next();
        assertEquals(1200, rs.getInt("COLUMN_SIZE"));
        assertFalse(rs.wasNull());
    }

    @Test
    void theCharacterLengthQueryQuotesNames() {
        String sql = TiberoCharLength.sql("MIN'LAB", "한글테이블");
        assertTrue(sql.contains("OWNER = 'MIN''LAB'"), sql);
        assertTrue(sql.contains("TABLE_NAME = '한글테이블'"), sql);
        assertTrue(sql.contains("CHAR_USED = 'C'"), sql);
    }

    @Test
    void unconstrainedNumberStaysUnchangedWhenItCannotBeMeasured() throws SQLException {
        // 측정 질의가 값을 돌려주지 못하면(빈 결과) 지금처럼 비워 둔다. 추출은 멈추지 않는다.
        FakeJdbc.Rows empty = new FakeJdbc.Rows("N", "MAX_SCALE", "MAX_INT", "EXP");
        FakeJdbc.Rows rows = new FakeJdbc.Rows(COLUMN_SIZE_LABELS)
                .row("APP", "T", "ANY", Types.NUMERIC, "NUMBER", null, null, 0);
        ResultSet rs = wrap(FakeJdbc.byMethod("getColumns", FakeJdbc.resultSet(rows)), 6, FakeJdbc.resultSet(empty))
                .getColumns(null, "APP", "T", "%");
        rs.next();
        rs.getLong("COLUMN_SIZE");
        assertTrue(rs.wasNull());
        assertNull(rs.getObject("DECIMAL_DIGITS"));
    }
}
