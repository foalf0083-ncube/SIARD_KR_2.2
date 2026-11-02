// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.meta;

import ch.enterag.sqlparser.BaseSqlFactory;
import ch.enterag.sqlparser.datatype.PredefinedType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PredefinedTypesTest {
    private static final String[] TYPES = {
            "CHAR(14)", "CHAR(1)", "VARCHAR(200)", "VARCHAR(4000)", "NCHAR(10)", "NATIONAL CHARACTER VARYING(100)",
            "BINARY(16)", "BINARY VARYING(2000)", "VARBINARY(2000)", "NUMERIC(18, 1)", "NUMERIC(38)", "DECIMAL(12, 2)",
            "INTEGER", "INT", "SMALLINT", "BIGINT", "REAL", "DOUBLE PRECISION", "FLOAT(126)", "BOOLEAN",
            "DATE", "TIME", "TIMESTAMP", "TIMESTAMP(6)", "INTERVAL YEAR TO MONTH", "INTERVAL DAY TO SECOND(6)",
            "CLOB", "CLOB(2G)", "NCLOB", "BLOB", "BLOB(1M)", "CLOB(64K)", "XML"};

    private static String describe(PredefinedType prt) {
        return prt.getType() + "|" + (prt.getType() == null ? "" : prt.getType().getSqlType()) + "|" + prt.getLength()
                + "|" + prt.getMultiplier() + "|" + prt.getPrecision() + "|" + prt.getScale();
    }

    @Test
    void cachedParseEqualsFreshParse() {
        for (String type : TYPES) {
            PredefinedType fresh = new BaseSqlFactory().newPredefinedType();
            fresh.parse(type);
            assertEquals(describe(fresh), describe(PredefinedTypes.parsed(type)), type);
            assertEquals(describe(fresh), describe(PredefinedTypes.parsed(type)), type + " (again)");
        }
    }

    @Test
    void sameStringReturnsSameParsedValue() {
        assertSame(PredefinedTypes.parsed("VARCHAR(30)"), PredefinedTypes.parsed("VARCHAR(30)"));
    }
}
