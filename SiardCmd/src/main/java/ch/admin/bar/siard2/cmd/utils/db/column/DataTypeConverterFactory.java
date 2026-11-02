// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd.utils.db.column;

import ch.admin.bar.siard2.api.MetaColumn;
import ch.admin.bar.siard2.cmd.MetaDataBase.DataBase;
import ch.admin.bar.siard2.cmd.utils.ThrowingBiFunction;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class DataTypeConverterFactory {

    private static final Map<DataBase, ThrowingBiFunction<String, MetaColumn, DataTypeConverter>> CONVERTER_MAP =
            new EnumMap<>(DataBase.class);

    static {
        CONVERTER_MAP.put(DataBase.TIBERO, TiberoDataTypeConverter::new);
        CONVERTER_MAP.put(DataBase.CUBRID, CubridDataTypeConverter::new);
        CONVERTER_MAP.put(DataBase.MYSQL, MySQLDataTypeConverter::new);
        CONVERTER_MAP.put(DataBase.ORACLE, OracleDataTypeConverter::new);
        CONVERTER_MAP.put(DataBase.MSSQL, MsSqlDataTypeConverter::new);
    }

    public static DataTypeConverter getInstance(String databaseProductName, MetaColumn metaColumn) throws IOException {
        DataBase dataBase;
        try {
            dataBase = DataBase.findByName(databaseProductName);
        } catch (RuntimeException e) {
            return null;
        }

        ThrowingBiFunction<String, MetaColumn, DataTypeConverter> constructor = CONVERTER_MAP.get(dataBase);
        if (constructor == null) {
            return null;
//            throw new UnsupportedOperationException("No DataTypeConverter for database: " + databaseProductName);
        }

        return constructor.apply(databaseProductName, metaColumn);
    }

}
