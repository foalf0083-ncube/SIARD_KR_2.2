// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Oracle 10 에서 테이블 · 뷰 목록을 원본 래퍼(jdbcoracle) 대신 직접 조회한다.
 *
 * <p>래퍼의 getTables 는 ALL_OBJECTS 에 ALL_TABLES · ALL_VIEWS · ALL_TAB_COMMENTS 를 상수 조건이 든 ANSI 외부
 * 조인으로 붙인다. Oracle 10.2.0.1 은 이 조인을 잘못 풀어 테이블 하나를 DB 전체 테이블 수만큼(시험 DB 1,386배)
 * 되풀이해 돌려주고, 결과에 LONG(뷰 정의문)이 있어 드라이버가 한 행씩 받는다. 그래서 목록 읽기가 매우 느리다.
 * 11g 이상은 래퍼 결과가 맞으므로 그대로 쓰고, 이 클래스는 10 에서만 쓴다({@link #PROPERTY} 로 강제 가능).</p>
 *
 * <p>걸러내는 기준은 래퍼 SQL 과 같다: 시스템 계정 제외(아래 목록, 래퍼와 같은 이름), {@code GENERATED = 'N'},
 * {@code TEMPORARY = 'N'}, 테이블은 {@code ALL_TABLES.NESTED = 'NO'} 인 것, 뷰는 {@code ALL_VIEWS} 에 있는 것.
 * 설명은 {@code ALL_TAB_COMMENTS}. 순서는 종류 · 스키마 · 이름. 10g · 11g · 19c 에서 래퍼 결과와 맞대어 같음을 확인했다.</p>
 */
final class OracleTableList {

    /** auto(기본, Oracle 10 에서만) · true(늘 직접 조회) · false(늘 래퍼). */
    static final String PROPERTY = "ch.admin.bar.siard2.cmd.oracle.ownTableList";

    private static final Logger LOG = LoggerFactory.getLogger(OracleTableList.class);

    /** 래퍼 getTables 가 시스템 계정으로 보고 거르는 이름(래퍼 SQL 에서 옮김). 이 밖에 APEX_ 로 시작하는 계정. */
    static final String[] SYSTEM_USERS = {
            "ANONYMOUS", "APEX_PUBLIC_USER", "APPQOSSYS", "AUDSYS", "CTXSYS", "DBSNMP", "DIP", "DVF", "DVSYS",
            "FLOWS_FILES", "GSMADMIN_INTERNAL", "GSMCATUSER", "GSMUSER", "LBACSYS", "MDDATA", "MDSYS", "OJVMSYS",
            "OLAPSYS", "ORACLE_OCM", "ORDDATA", "ORDPLUGINS", "ORDSYS", "OUTLN", "PUBLIC", "SI_INFORMTN_SCHEMA",
            "SPATIAL_CSW_ADMIN_USR", "SPATIAL_WFS_ADMIN_USR", "SYS", "SYSBACKUP", "SYSDG", "SYSKM", "SYSTEM",
            "WMSYS", "XDB", "XS$NULL"};

    private final Connection _conn;

    OracleTableList(Connection conn) {
        _conn = conn;
    }

    /** 이 DB 에서 직접 조회할지. 속성이 없으면 Oracle 10 일 때만. */
    static boolean isEnabled(DatabaseMetaData dmd) {
        String s = System.getProperty(PROPERTY, "auto").trim().toLowerCase();
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        try {
            return dmd.getDatabaseMajorVersion() == 10;
        } catch (SQLException e) {
            LOG.warn("Oracle 판을 알 수 없어 테이블 목록은 래퍼로 읽음: {}", e.getMessage());
            return false;
        }
    }

    private static String notSystemUser(String sUserColumn) {
        StringBuilder sb = new StringBuilder("SUBSTR(").append(sUserColumn).append(", 1, 5) <> 'APEX_' AND ")
                .append(sUserColumn).append(" NOT IN (");
        for (int i = 0; i < SYSTEM_USERS.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append('\'').append(SYSTEM_USERS[i]).append('\'');
        }
        return sb.append(')').toString();
    }

    /*
     * 테이블은 ALL_TABLES, 뷰는 ALL_VIEWS 를 기준으로 ALL_OBJECTS 와 ALL_TAB_COMMENTS 를 내부 조인한다.
     * ALL_OBJECTS 를 기준으로 행마다 EXISTS 와 설명 부분질의를 돌리면 사전이 큰 DB 에서 매우 느리다
     * (19c 시험: 21.6초 → 0.5초). 외부 조인은 쓰지 않는다(10.2.0.1 이 잘못 푸는 형태).
     * ALL_TAB_COMMENTS 는 보이는 테이블 · 뷰마다 한 행씩 있다(설명이 없으면 NULL).
     */
    private static String common() {
        return " AND o.generated = 'N' AND o.temporary = 'N'"
                + " AND tc.owner = o.owner AND tc.table_name = o.object_name AND tc.table_type = o.object_type"
                + " AND " + notSystemUser("o.owner");
    }

    private static final String TABLES_PART = "SELECT o.owner, o.object_name, o.object_type, tc.comments"
            + " FROM all_tables t, all_objects o, all_tab_comments tc"
            + " WHERE o.owner = t.owner AND o.object_name = t.table_name AND o.object_type = 'TABLE' AND t.nested = 'NO'";

    private static final String VIEWS_PART = "SELECT o.owner, o.object_name, o.object_type, tc.comments"
            + " FROM all_views v, all_objects o, all_tab_comments tc"
            + " WHERE o.owner = v.owner AND o.object_name = v.view_name AND o.object_type = 'VIEW'";

    static String tablesSql(boolean bTables, boolean bViews) {
        List<String> listParts = new ArrayList<>();
        if (bTables) listParts.add(TABLES_PART + common());
        if (bViews) listParts.add(VIEWS_PART + common());
        // 래퍼와 같은 순서: 종류, 스키마, 이름
        return String.join(" UNION ALL ", listParts) + " ORDER BY 3, 1, 2";
    }

    static String viewsSql() {
        return "SELECT o.owner, o.object_name, o.object_type, tc.comments, v.text"
                + " FROM all_views v, all_objects o, all_tab_comments tc"
                + " WHERE o.owner = v.owner AND o.object_name = v.view_name AND o.object_type = 'VIEW'"
                + common()
                + " AND o.owner = ?"
                + " ORDER BY 1, 2";
    }

    /** 테이블(·뷰) 목록. 행은 {스키마, 이름, 종류, 설명}. */
    List<String[]> tables(boolean bTables, boolean bViews) throws SQLException {
        List<String[]> listRows = new ArrayList<>();
        if (!bTables && !bViews) return listRows;
        try (PreparedStatement ps = _conn.prepareStatement(tablesSql(bTables, bViews))) {
            ps.setFetchSize(1000);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    listRows.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
            }
        }
        LOG.debug("Oracle 테이블 목록 직접 조회: {}행", listRows.size());
        return listRows;
    }

    /** 한 스키마의 뷰 목록. 행은 {스키마, 이름, 종류, 설명, 정의문}. 정의문이 LONG 이라 한 행씩 받힌다. */
    List<String[]> views(String sSchema) throws SQLException {
        List<String[]> listRows = new ArrayList<>();
        try (PreparedStatement ps = _conn.prepareStatement(viewsSql())) {
            ps.setString(1, sSchema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    listRows.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5)});
            }
        }
        return listRows;
    }

    /** 저장된 이름 그대로의 {스키마, 이름}. 없으면 null. 종류를 가리지 않는다(래퍼에 종류 없이 묻던 자리). */
    String[] resolve(String sSchema, String sName) throws SQLException {
        // 이름이 없는 자리는 래퍼처럼 조건 없이 둔다.
        String sSql = "SELECT owner, object_name FROM all_objects WHERE object_type IN ('TABLE', 'VIEW', 'SYNONYM')"
                + (sSchema != null ? " AND owner = ?" : "") + (sName != null ? " AND object_name = ?" : "")
                + " ORDER BY object_type, owner, object_name";
        try (PreparedStatement ps = _conn.prepareStatement(sSql)) {
            int i = 1;
            if (sSchema != null) ps.setString(i++, sSchema);
            if (sName != null) ps.setString(i, sName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new String[]{rs.getString(1), rs.getString(2)};
            }
        }
        return null;
    }
}
