// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.utils.jdbc.BaseDatabaseMetaData;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Tibero 메타데이터 래퍼. 현행 엔진이 Tibero 전용 분기로 처리하던 것을 이 안에서 처리한다.
 *
 * <ul>
 *   <li>컬럼 순번(getColumns)과 키 순번(getPrimaryKeys · getImportedKeys · getExportedKeys · getCrossReference)을
 *       1 기준으로 보정한다. 인덱스 순번(getIndexInfo)은 드라이버가 이미 1 기준이라 손대지 않는다.</li>
 *   <li>타입 코드를 파서가 아는 코드로 대응한다.</li>
 *   <li>시스템 스키마의 테이블과 스키마를 걸러낸다.</li>
 *   <li>구체화 뷰 컬럼 중복(getColumns)과 루틴 매개변수 순번 NULL · 라벨 오타(getProcedureColumns ·
 *       getFunctionColumns)를 바로잡는다. {@link TiberoMetaColumns} · {@link TiberoProcedureColumns} 참고.</li>
 *   <li>뷰 행에 정의 문장 컬럼 {@code QUERY_TEXT}를 덧붙인다({@link TiberoMetaTables}).</li>
 *   <li>식에 걸린 인덱스 행을 걸러낸다({@link TiberoIndexInfo}).</li>
 *   <li>식별자 패턴 변환은 기반 클래스의 표준 동작을 쓴다. 드라이버가 이스케이프 문자 {@code /}를 지원한다.</li>
 * </ul>
 *
 * <p>순번 기준 · 인덱스 순번 · 이스케이프 지원은 2026-09-08 Tibero 6(빌드 203675) 실기동으로 확인한 것이다.
 * 서버 판({@link #getTibeMajorVersion()})은 7판 · 5판 차이가 확인되면 분기하기 위해 읽어 둔다.
 */
public class TiberoDatabaseMetaData extends BaseDatabaseMetaData {

    private static final int iVERSION_UNKNOWN = -1;

    private final TiberoConnection _conn;
    private Integer _iMajorVersion = null;

    public TiberoDatabaseMetaData(DatabaseMetaData dmdWrapped, TiberoConnection conn) {
        super(dmdWrapped);
        _conn = conn;
    }

    @Override
    public Connection getConnection() {
        return _conn;
    }

    /** 서버 주 버전. 읽을 수 없으면 -1. */
    public int getTibeMajorVersion() {
        if (_iMajorVersion == null) {
            try {
                _iMajorVersion = Integer.valueOf(getDatabaseMajorVersion());
            } catch (Throwable t) {
                _iMajorVersion = Integer.valueOf(iVERSION_UNKNOWN);
            }
        }
        return _iMajorVersion.intValue();
    }

    /**
     * 현행 엔진의 제외 규칙(SYS로 시작하는 스키마와 OUTLN)에 Tibero 7 의 LBACSYS 를 더했다.
     * LBACSYS 는 7판 설치 때 만들어지는 레이블 보안 스키마로, 일반 사용자에게 뷰 19개와 루틴 51개가 보인다
     * (2026-09-30, 7.2.5 실측). 뷰를 테이블로 담는 {@code -v} 추출에서 이 스키마가 딸려 들어와 오버로드된 루틴
     * 이름(DOMINATES)이 겹쳐 추출이 멈췄다.
     */
    static boolean isSystemSchema(String sSchema) {
        return (sSchema != null)
                && (sSchema.startsWith("SYS") || sSchema.equals("OUTLN") || sSchema.equals("LBACSYS"));
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern,
                                String columnNamePattern) throws SQLException {
        return new TiberoMetaColumns(
                super.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern), _conn);
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        return new TiberoOrdinalResultSet(super.getPrimaryKeys(catalog, schema, table), _conn, "KEY_SEQ", 1);
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        return new TiberoOrdinalResultSet(super.getImportedKeys(catalog, schema, table), _conn, "KEY_SEQ", 1);
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        return new TiberoOrdinalResultSet(super.getExportedKeys(catalog, schema, table), _conn, "KEY_SEQ", 1);
    }

    @Override
    public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable,
                                       String foreignCatalog, String foreignSchema, String foreignTable)
            throws SQLException {
        return new TiberoOrdinalResultSet(super.getCrossReference(parentCatalog, parentSchema, parentTable,
                foreignCatalog, foreignSchema, foreignTable), _conn, "KEY_SEQ", 1);
    }

    @Override
    public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern,
                                         String columnNamePattern) throws SQLException {
        return new TiberoProcedureColumns(
                super.getProcedureColumns(catalog, schemaPattern, procedureNamePattern, columnNamePattern), _conn);
    }

    @Override
    public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern,
                                        String columnNamePattern) throws SQLException {
        return new TiberoProcedureColumns(
                super.getFunctionColumns(catalog, schemaPattern, functionNamePattern, columnNamePattern), _conn);
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate)
            throws SQLException {
        return new TiberoIndexInfo(super.getIndexInfo(catalog, schema, table, unique, approximate), _conn);
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
            throws SQLException {
        return new TiberoMetaTables(super.getTables(catalog, schemaPattern, tableNamePattern, types), _conn);
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        return new TiberoSchemaFilterResultSet(super.getSchemas(), _conn, "TABLE_SCHEM");
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        return new TiberoSchemaFilterResultSet(super.getSchemas(catalog, schemaPattern), _conn, "TABLE_SCHEM");
    }
}
