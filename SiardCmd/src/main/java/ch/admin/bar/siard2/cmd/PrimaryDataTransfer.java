// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.api.Archive;
import ch.admin.bar.siard2.api.MetaTable;
import ch.admin.bar.siard2.api.Table;
import ch.admin.bar.siard2.cmd.utils.db.AbstractSkippableDb;
import ch.enterag.sqlparser.SqlLiterals;
import ch.enterag.sqlparser.identifier.QualifiedId;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PrimaryDataTransfer extends AbstractSkippableDb {
    private static final Logger LOG = LoggerFactory.getLogger(PrimaryDataTransfer.class);


    protected Connection _conn = null;
    protected Archive _archive = null;
    protected ArchiveMapping _am = null;
    protected int _iQueryTimeoutSeconds = 30;
    protected boolean _bSupportsArrays = false;

    public void setQueryTimeout(int iQueryTimeoutSeconds) {
        this._iQueryTimeoutSeconds = iQueryTimeoutSeconds;
    }

    protected boolean _bSupportsDistincts = false;

    public boolean supportsArrays() {
        return this._bSupportsArrays;
    }

    public boolean supportsDistincts() {
        return this._bSupportsDistincts;
    }

    protected boolean _bSupportsUdts = false;

    public boolean supportsUdts() {
        return this._bSupportsUdts;
    }


    /** 대상 테이블의 컬럼 이름을 인용 · 매핑까지 마친 형태로 돌려준다. 조회문과 삽입문이 같은 표기를 쓰게 한다. */
    protected List<String> targetColumnNames(MetaTable mt, TableMapping tm) throws IOException {
        List<String> listNames = new java.util.ArrayList<>();
        List<List<String>> llColumnNames = mt.getColumnNames(supportsArrays(), supportsUdts());
        for (int iColumn = 0; iColumn < llColumnNames.size(); iColumn++) {
            List<String> listColumnName = llColumnNames.get(iColumn);
            StringBuilder sbColumnName = new StringBuilder();
            for (int i = 0; i < listColumnName.size(); i++) {
                if (i > 0)
                    sbColumnName.append(".");
                sbColumnName.append(listColumnName.get(i));
            }
            String sExtendedColumnName = sbColumnName.toString();
            if (tm != null)
                sExtendedColumnName = tm.getMappedExtendedColumnName(sExtendedColumnName);
            listNames.add(ch.admin.bar.siard2.cmd.utils.IdQuoting.safeId(
                    SqlLiterals.formatId(sExtendedColumnName)));
        }
        return listNames;
    }

    /** 대상 테이블의 스키마 · 이름을 매핑까지 마친 형태로 돌려준다. */
    protected String targetQualifiedName(MetaTable mt, SchemaMapping sm, TableMapping tm) {
        String sSchemaName = mt.getParentMetaSchema().getName();
        if (sm != null)
            sSchemaName = sm.getMappedSchemaName();
        String sTableName = mt.getName();
        if (tm != null)
            sTableName = tm.getMappedTableName();
        return ch.admin.bar.siard2.cmd.utils.IdQuoting.safeQualified(sSchemaName, sTableName);
    }

    /**
     * 대상 테이블에 행을 넣는 {@code INSERT} 문을 준비한다.
     *
     * <p>갱신 가능 결과집합으로 행을 넣지 못하는 경우에 쓴다. 기본키가 없는 테이블(구체화 뷰를 테이블로
     * 보존한 것 등)은 드라이버가 결과집합 삽입을 거부한다. 컬럼 표기는 {@link #openTable}과 같다.
     */
    protected PreparedStatement prepareInsert(Table table, SchemaMapping sm) throws IOException, SQLException {
        MetaTable mt = table.getMetaTable();
        TableMapping tm = (sm != null) ? sm.getTableMapping(mt.getName()) : null;
        List<String> listColumns = targetColumnNames(mt, tm);
        StringBuilder sbSql = new StringBuilder("INSERT INTO ");
        sbSql.append(targetQualifiedName(mt, sm, tm));
        sbSql.append(" (");
        for (int i = 0; i < listColumns.size(); i++) {
            if (i > 0)
                sbSql.append(", ");
            sbSql.append(listColumns.get(i));
        }
        sbSql.append(") VALUES (");
        for (int i = 0; i < listColumns.size(); i++) {
            if (i > 0)
                sbSql.append(", ");
            sbSql.append("?");
        }
        sbSql.append(")");
        LOG.debug("Insert statement: '{}'", sbSql);
        PreparedStatement ps = this._conn.prepareStatement(sbSql.toString());
        ps.setQueryTimeout(this._iQueryTimeoutSeconds);
        return ps;
    }

    protected ResultSet openTable(Table table, SchemaMapping sm) throws IOException, SQLException {
        MetaTable mt = table.getMetaTable();

        TableMapping tm = null;
        if (sm != null) {
            tm = sm.getTableMapping(mt.getName());
        }
        StringBuilder sbSql = new StringBuilder("SELECT\r\n");
        List<String> listColumns = targetColumnNames(mt, tm);
        for (int iColumn = 0; iColumn < listColumns.size(); iColumn++) {

            if (iColumn > 0)
                sbSql.append("," + "\r\n");
            sbSql.append("  ");
            sbSql.append(listColumns.get(iColumn));
        }
        String sQualifiedName = targetQualifiedName(mt, sm, tm);
        sbSql.append("\r\n FROM " + sQualifiedName);

        String sqlStatement = sbSql.toString();
        LOG.trace("SQL statement: '{}'", sqlStatement);

        int iHoldability = 1;
        if (sm == null)
            iHoldability = 1;
        int iConcurrency = 1008;
        if (sm == null)
            iConcurrency = 1007;
        int iType = 1003;
        if (sm == null)
            iType = 1003;
        this._conn.setHoldability(iHoldability);
        Statement stmt = this._conn.createStatement(iType, iConcurrency, iHoldability);
        try {
            stmt.setQueryTimeout(this._iQueryTimeoutSeconds);
            if (sm == null)
                applyFetchSize(stmt, mt);
            ResultSet rs = stmt.executeQuery(sbSql.toString());
            LOG.debug("Data from table '{}' successfully loaded", sQualifiedName);
            return rs; // The caller owns the result set and its statement after success.
        } catch (IOException | SQLException | RuntimeException | Error failure) {
            try { stmt.close(); }
            catch (SQLException | RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }


    /**
     * 추출할 때 한 번에 가져오는 행 수(표준 JDBC fetch size). 행 순서와 값은 바뀌지 않는다.
     *
     * <p>원래는 지정하지 않아 드라이버 기본값으로 읽었다. Oracle 은 한 번에 10행이라 일반 컬럼만 있는 표도
     * 10행마다 DB 왕복이 생겼다.</p>
     *
     * <p>명시값: {@link #PROPERTY_FETCH_SIZE} 를 양수로 주면 문장의 현재 값(드라이버 기본값이나
     * -Doracle.jdbc.defaultRowPrefetch)보다 작아도 그 값을 쓴다. 0 이하이면 지정하지 않는다.</p>
     *
     * <p>자동값: 속성이 없으면 행 너비를 메타데이터로 어림해 가져오기 버퍼가 {@link #FETCH_BUFFER_BYTES} 를
     * 넘지 않게 10~{@link #DEFAULT_FETCH_SIZE} 사이에서 정한다(Oracle 드라이버는 행 수 × 컬럼 최대 길이만큼
     * 버퍼를 잡는다). 문장의 현재 값이 더 크면 낮추지 않는다.</p>
     */
    static final String PROPERTY_FETCH_SIZE = "ch.admin.bar.siard2.cmd.fetchSize";
    static final int DEFAULT_FETCH_SIZE = 2000;
    static final String PROPERTY_AUTO_FETCH_MAX = "siard.fetch.maxRows";
    static final long FETCH_BUFFER_BYTES = 32L << 20;

    /** 행 너비로 정한 자동값. */
    static int automaticFetchSize(MetaTable mt) throws IOException {
        long lRowBytes = 0;
        for (int iColumn = 0; iColumn < mt.getMetaColumns(); iColumn++)
            lRowBytes += columnBytes(mt.getMetaColumn(iColumn));
        int maximum = Math.max(10, Math.min(10000,
                Integer.getInteger(PROPERTY_AUTO_FETCH_MAX, DEFAULT_FETCH_SIZE)));
        return automaticFetchSize(lRowBytes, maximum, Runtime.getRuntime().maxMemory());
    }

    static int automaticFetchSize(long rowBytes, int maximum, long heapBytes) {
        long budget = Math.min(FETCH_BUFFER_BYTES, Math.max(1L << 20, heapBytes / 16));
        long rows = rowBytes > 0 ? budget / rowBytes : maximum;
        return (int) Math.max(1, Math.min(maximum, rows));
    }

    /**
     * 문장에 지정할 값. 지정하지 않으면 -1.
     *
     * @param iConfigured 속성으로 준 명시값, 없으면 null
     * @param iCurrent    문장의 현재 값
     * @param iAutomatic  {@link #automaticFetchSize} 값
     */
    static int chooseFetchSize(Integer iConfigured, int iCurrent, int iAutomatic) {
        if (iConfigured != null)
            return iConfigured > 0 ? iConfigured : -1;
        return iCurrent < iAutomatic ? iAutomatic : -1;
    }

    /** 가져오기 버퍼에서 한 컬럼이 차지할 크기의 어림값(문자는 Java 문자 2바이트). */
    private static long columnBytes(ch.admin.bar.siard2.api.MetaColumn mc) throws IOException {
        switch (mc.getPreType()) {
            case java.sql.Types.CLOB:
            case java.sql.Types.NCLOB:
                // Account for common 4K-character inline/prefetched values too.
                // Locator-only accounting underestimates wide multi-LOB tables.
                return 8192;
            case java.sql.Types.BLOB:
                return 4096;
            case java.sql.Types.CHAR:
            case java.sql.Types.VARCHAR:
            case java.sql.Types.NCHAR:
            case java.sql.Types.NVARCHAR:
            case java.sql.Types.BINARY:
            case java.sql.Types.VARBINARY: {
                long lLength = mc.getLength();
                return lLength > 0 ? 2 * Math.min(lLength, 32767) : 8000;
            }
            default:
                return 64;
        }
    }

    private static void applyFetchSize(Statement stmt, MetaTable mt) throws IOException {
        Integer iConfigured = Integer.getInteger(PROPERTY_FETCH_SIZE);
        try {
            int iChosen = chooseFetchSize(iConfigured, stmt.getFetchSize(),
                    iConfigured == null ? automaticFetchSize(mt) : 0);
            if (iChosen > 0)
                stmt.setFetchSize(iChosen);
            LOG.debug("Fetch size for '{}': {} ({})", mt.getName(), stmt.getFetchSize(),
                    iConfigured != null ? "configured" : (iChosen > 0 ? "automatic" : "statement default kept"));
        } catch (SQLException se) {
            LOG.debug("Fetch size not applied, driver default kept: {}", se.getMessage());
        }
    }

    protected PrimaryDataTransfer(Connection conn, Archive archive, ArchiveMapping am, boolean bSupportsArrays, boolean bSupportsDistincts, boolean bSupportsUdts) throws SQLException {
        super(conn.getMetaData().getDatabaseProductName());
        this._conn = conn;
        this._archive = archive;
        this._am = am;
        this._bSupportsArrays = bSupportsArrays;
        this._bSupportsDistincts = bSupportsDistincts;
        this._bSupportsUdts = bSupportsUdts;
    }
}
