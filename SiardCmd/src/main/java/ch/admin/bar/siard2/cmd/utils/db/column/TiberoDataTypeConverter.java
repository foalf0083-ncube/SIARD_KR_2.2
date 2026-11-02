// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd.utils.db.column;

import ch.admin.bar.siard2.api.MetaColumn;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import static java.util.Map.entry;

public class TiberoDataTypeConverter extends AbstractDataTypeConverter {

    public TiberoDataTypeConverter(String databaseProductName, MetaColumn metaColumn) throws IOException {
        super(databaseProductName, metaColumn);
    }

    /**
     * 길이가 주어져야 하는 타입
     */
    private static final Map<String, String> DEFAULT_COLUMN_SPECIFIER = Map.of(
            "CHAR", "2000",
            "VARCHAR", "65532",
            "VARCHAR2", "65532",
            "NCHAR", "2000",
            "NVARCHAR", "65532",
            "NVARCHAR2", "65532",
            "RAW", "2000"
    );

    private static final Map<String, String> COMPATIBLE_COLUMN_TYPE_MAP = Map.ofEntries(
            entry("BIGINT", "INTEGER"),
            entry("SMALLINT", "INTEGER"),
            entry("DOUBLE PRECISION", "FLOAT"),
            entry("CHARACTER VARYING", "VARCHAR2"),
            entry("CHAR VARYING", "VARCHAR2"),
            entry("NATIONAL CHARACTER VARYING", "NVARCHAR2"),
            entry("NCHAR VARYING", "NVARCHAR2"),
            entry("BINARY", "RAW"),
            entry("VARBINARY", "RAW"),
            entry("BINARY VARYING", "RAW"),
            entry("CHARACTER LARGE OBJECT", "CLOB"),
            entry("NATIONAL CHARACTER LARGE OBJECT", "NCLOB"),
            entry("NCHAR LARGE OBJECT", "NCLOB"),
            entry("BINARY LARGE OBJECT", "BLOB"),
            entry("TIMESTAMP WITH TIME ZONE", "TIMESTAMP WITH TIME ZONE"),
            entry("TIME WITH TIME ZONE", "TIME WITH TIME ZONE")
    );

    /**
     * 길이 의미를 문자로 못박아야 하는 타입.
     *
     * <p>보존물의 길이는 문자 수인데 Tibero 의 {@code NLS_LENGTH_SEMANTICS} 기본값은 BYTE 다. 그대로 두면
     * 한글 한 글자가 3바이트를 차지해 재적재에서 「값이 너무 큽니다」로 멈춘다(2026-09-08 실측, JDBC-11048).
     * 국가 문자 타입(NCHAR · NVARCHAR2)은 이미 문자 단위라 손대지 않는다.
     */
    private static final java.util.Set<String> CHAR_SEMANTICS_TYPES = java.util.Set.of("CHAR", "VARCHAR", "VARCHAR2");

    /** 길이가 한 개 지정된 타입 표기. 보기: {@code VARCHAR2(1000)} */
    private static final java.util.regex.Pattern SINGLE_LENGTH =
            java.util.regex.Pattern.compile("([^(]+)\\((\\d+)\\)");

    /** 문자 타입의 선언 길이 한도(바이트). 이 안에서만 문자 의미를 붙인다. */
    private static final int MAX_CHAR_LENGTH = 2000;
    private static final int MAX_VARCHAR_LENGTH = 4000;

    @Override
    protected String appendSpecifier(String dataType, String specifier) {
        String sType = super.appendSpecifier(dataType, specifier);
        String sBase = dataType.toUpperCase();
        if (!CHAR_SEMANTICS_TYPES.contains(sBase)) {
            return sType;
        }
        java.util.regex.Matcher m = SINGLE_LENGTH.matcher(sType);
        if (!m.matches()) {
            return sType;
        }
        int iLength;
        try {
            iLength = Integer.parseInt(m.group(2));
        } catch (NumberFormatException nfe) {
            return sType;
        }
        int iMax = "CHAR".equals(sBase) ? MAX_CHAR_LENGTH : MAX_VARCHAR_LENGTH;
        if (iLength <= 0 || iLength > iMax) {
            return sType;
        }
        return m.group(1) + "(" + iLength + " CHAR)";
    }

    protected boolean requiresSpecifier(String type) {
        return DEFAULT_COLUMN_SPECIFIER.containsKey(type);
    }

    @Override
    protected String getAlternativeType(String type) {
        return COMPATIBLE_COLUMN_TYPE_MAP.get(type.toUpperCase());
    }

    @Override
    protected String getDefaultSpecifier(String dataType) {
        return DEFAULT_COLUMN_SPECIFIER.get(dataType);
    }

    @Override
    protected boolean isSupported(String dataType) {
        return SupportedDataType.isSupported(dataType);
    }

    private enum SupportedDataType {

        NUMBER,
        NCHAR,
        INT,
        INTEGER,
        DECIMAL,
        NUMERIC,
        FLOAT,
        DATE,
        TIMESTAMP,
        TIME,
        CHAR,
        VARCHAR,
        VARCHAR2,
        NVARCHAR,
        NVARCHAR2,
        CLOB,
        BLOB,
        NCLOB,
        ROWID,
        RAW,
        ;

        private static boolean isSupported(String type) {
            return Arrays.stream(values()).anyMatch(supportedType -> supportedType.name().equalsIgnoreCase(type));
        }

    }

}
