// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd.utils.db;

import lombok.Getter;

import static ch.admin.bar.siard2.cmd.MetaDataBase.DataBase.isCubrid;
import static ch.admin.bar.siard2.cmd.MetaDataBase.DataBase.isMySqlFamily;
import static ch.admin.bar.siard2.cmd.utils.StringUtils.*;

public abstract class AbstractSkippableDb {

    @Getter
    private final String databaseProductName;

    public AbstractSkippableDb(String databaseProductName) {
        this.databaseProductName = databaseProductName;
    }

    protected boolean isCubridSkippable(String tableName) {
        return isCubridDb() && isCubridSystable(tableName);
    }

    protected boolean isMySqlSkippable(String schemaName) {
        return isMySqlDb() && isMySqlSystemSchema(schemaName);
    }

    protected boolean isMySqlDb() {
        return isMySqlFamily(this.databaseProductName);
    }

    private boolean isMySqlSystemSchema(String schemaName) {
        if (schemaName == null) return false;
        String lower = schemaName.toLowerCase();
        return lower.equals("mysql") || lower.equals("sys")
                || lower.equals("information_schema") || lower.equals("performance_schema");
    }

    protected String trimTableNameIfCubrid(String tableName) {
        return isCubridDb() ? trimTableName(tableName) : tableName;
    }

    private String trimTableName(String trailingTableName) {
        return removeFirstDot(trailingTableName);
    }

    protected boolean isCubridDb() {
        return isCubrid(this.databaseProductName);
    }

    private boolean isCubridSystable(String tableName) {
        String upper = tableName.toUpperCase();
        return upper.startsWith("_DB_") || upper.startsWith("DB_");
    }
}
