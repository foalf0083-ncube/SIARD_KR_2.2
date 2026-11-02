// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.api.MetaData;
import ch.admin.bar.siard2.cmd.utils.db.AbstractSkippableDb;
import ch.enterag.sqlparser.identifier.QualifiedId;
import ch.enterag.utils.jdbc.BaseDatabaseMetaData;
import lombok.Getter;

import java.sql.*;
import java.text.ParseException;
import java.util.*;

import static ch.admin.bar.dbexception.DatabaseExceptionHandlerHelper.doHandleSqlException;

public abstract class MetaDataBase extends AbstractSkippableDb {

    protected DatabaseMetaData _dmd = null;
    protected MetaData _md = null;
    protected int _iQueryTimeoutSeconds = 30;
    private boolean _bSupportsArrays = false;
    private boolean _bSupportsDistincts = false;
    private boolean _bSupportsUdts = false;
    private Set<QualifiedId> _setUsedTypes = null;

    public void setQueryTimeout(int iQueryTimeoutSeconds) {
        this._iQueryTimeoutSeconds = iQueryTimeoutSeconds;
    }

    public boolean supportsArrays() {
        return this._bSupportsArrays;
    }

    public boolean supportsDistincts() {
        return this._bSupportsDistincts;
    }

    public boolean supportsUdts() {
        return this._bSupportsUdts;
    }

    private boolean isUsedInColumn(QualifiedId qiType) throws SQLException {
        if (this._setUsedTypes == null) {
            this._setUsedTypes = new HashSet();
            ResultSet rs = this._dmd.getColumns(null, "%", "%", "%");

            while (true) {
                int iDataType;
                do {
                    if (!rs.next()) {
                        rs.close();
                        return this._setUsedTypes.contains(qiType);
                    }

                    iDataType = rs.getInt("DATA_TYPE");
                } while (iDataType != 2001 && iDataType != 2002);

                String sTypeName = rs.getString("TYPE_NAME");
                QualifiedId qi = null;

                try {
                    qi = new QualifiedId(sTypeName);
                } catch (ParseException var7) {
                    ParseException pe = var7;
                    throw new SQLException(sTypeName + " could not be parsed!", pe);
                }

                if (qi != null) {
                    this._setUsedTypes.add(qi);
                }
            }
        } else {
            return this._setUsedTypes.contains(qiType);
        }
    }

    protected MetaDataBase(DatabaseMetaData dmd, MetaData md) throws SQLException {
        this(dmd, md, true);
    }

    /**
     * @param bDetectTypeSupport 형식 지원 여부(UDT·DISTINCT)를 미리 잴지. 이 값은 재현(MetaDataToDb)에서만 쓰인다.
     *                           재려면 DB 의 모든 컬럼을 한 번 훑어야 해 테이블이 많으면 수십 초에서 수 분이 걸리므로
     *                           추출(MetaDataFromDb)은 재지 않는다.
     */
    protected MetaDataBase(DatabaseMetaData dmd, MetaData md, boolean bDetectTypeSupport) throws SQLException {
        super(dmd.getDatabaseProductName());
        this._dmd = dmd;
        this._md = md;
        if (bDetectTypeSupport) detectTypeSupport();
        detectArraySupport();
    }

    private void detectTypeSupport() throws SQLException {
        ResultSet rs = this._dmd.getUDTs(null, "%", "%", null);

        while (rs.next() && (!this._bSupportsUdts || !this._bSupportsDistincts)) {
            String sTypeSchema = rs.getString("TYPE_SCHEM");
            String sTypeName = rs.getString("TYPE_NAME");
            QualifiedId qiType = new QualifiedId(null, sTypeSchema, sTypeName);
            if (this.isUsedInColumn(qiType)) {
                int iDataType = rs.getInt("DATA_TYPE");
                if (iDataType == 2002) {
                    this._bSupportsUdts = true;
                } else if (iDataType == 2001) {
                    this._bSupportsDistincts = true;
                }
            }
        }

        rs.close();
    }

    private void detectArraySupport() throws SQLException {
        String databaseProductName = null;
        try {
            Connection connection = this._dmd.getConnection();
            databaseProductName = connection.getMetaData().getDatabaseProductName();
            String typeName = "INTEGER";
            Array array = connection.createArrayOf(typeName, new Integer[]{1, 2});
            array.free();
            this._bSupportsArrays = true;
        } catch (SQLFeatureNotSupportedException var8) {
            this._bSupportsArrays = false;
        } catch (SQLException var9) {
            doHandleSqlException(databaseProductName, null, var9);
        }
    }

    @Getter
    public enum DataBase {
        MYSQL("mysql", "mariadb"),          // MariaDB 는 MySQL 계열로 처리(JdbcMysql 래퍼·information_schema 공용)
        ORACLE("oracle"),                    // "Oracle Database" 등은 findByName 의 contains(name) 로 매칭
        POSTGRESQL("postgresql", "postgres"),
        MSSQL("mssql", "microsoft sql server", "sql server"),
        CUBRID("cubrid"),
        TIBERO("tibero"),
        // DB2 는 플랫폼을 붙인 이름을 보고한다(DB2/LINUXX8664, DB2/NT64 등) → contains 로 매칭
        DB2("db2"),

        ;

        final String name;
        final List<String> aliases;

        DataBase(String name, String... aliases) {
            this.name = name;
            this.aliases = Arrays.asList(aliases);
        }

        public static DataBase findByName(String name) {
            if (name == null || name.isBlank()) throw new NoSuchElementException("No enum value is found");
            String lower = name.toLowerCase();
            return Arrays.stream(DataBase.values())
                    .filter(dataBase -> dataBase.getName().equalsIgnoreCase(name)
                            || lower.contains(dataBase.getName())
                            || dataBase.getAliases().stream().anyMatch(lower::contains))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("No enum value is found for: " + name));
        }

        public static boolean isCubrid(String databaseProductName){
            return DataBase.CUBRID.equals(findByName(databaseProductName));
        }

        public static boolean isMySqlFamily(String databaseProductName) {
            return DataBase.MYSQL.equals(findByName(databaseProductName));
        }

    }

    protected String getPatternedName(String name) throws SQLException {
        return isCubridDb() ?
                name :
                ((BaseDatabaseMetaData) this._dmd).toPattern(name);
    }

}
