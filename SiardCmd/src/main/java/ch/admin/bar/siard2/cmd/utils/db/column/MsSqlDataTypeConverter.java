// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd.utils.db.column;

import ch.admin.bar.siard2.api.MetaColumn;

import java.io.IOException;
import java.util.Map;

public class MsSqlDataTypeConverter extends AbstractDataTypeConverter {

    private static final Map<String, String> COMPATIBLE_DATA_TYPE_MAP = Map.ofEntries(
            Map.entry("BOOLEAN", "BIT"),
            Map.entry("CHARACTER", "CHAR"),
            Map.entry("CHARACTER VARYING", "VARCHAR"),
            Map.entry("CHAR VARYING", "VARCHAR"),
            Map.entry("NATIONAL CHARACTER", "NCHAR"),
            Map.entry("NATIONAL CHAR", "NCHAR"),
            Map.entry("NATIONAL CHARACTER VARYING", "NVARCHAR"),
            Map.entry("NATIONAL CHAR VARYING", "NVARCHAR"),
            Map.entry("NCHAR VARYING", "NVARCHAR"),
            Map.entry("DOUBLE PRECISION", "FLOAT"),
            Map.entry("DOUBLE", "FLOAT"),
            Map.entry("NUMBER", "DECIMAL"),
            Map.entry("TIMESTAMP", "DATETIME2"),
            Map.entry("TIMESTAMP WITH TIME ZONE", "DATETIMEOFFSET"),
            Map.entry("TIME WITH TIME ZONE", "TIME"),
            Map.entry("CLOB", "VARCHAR(MAX)"),
            Map.entry("CHARACTER LARGE OBJECT", "VARCHAR(MAX)"),
            Map.entry("NCLOB", "NVARCHAR(MAX)"),
            Map.entry("NATIONAL CHARACTER LARGE OBJECT", "NVARCHAR(MAX)"),
            Map.entry("NATIONAL CHAR LARGE OBJECT", "NVARCHAR(MAX)"),
            Map.entry("NCHAR LARGE OBJECT", "NVARCHAR(MAX)"),
            Map.entry("BLOB", "VARBINARY(MAX)"),
            Map.entry("BINARY LARGE OBJECT", "VARBINARY(MAX)"),
            Map.entry("BINARY VARYING", "VARBINARY"),
            Map.entry("DATALINK", "VARBINARY(MAX)")
    );

    private static final Map<String, String> DEFAULT_COLUMN_SPECIFIER = Map.of(
            "VARCHAR", "8000",
            "NVARCHAR", "4000",
            "CHAR", "1",
            "NCHAR", "1",
            "VARBINARY", "8000",
            "BINARY", "1",
            "DECIMAL", "38, 0",
            "NUMERIC", "38, 0"
    );

    public MsSqlDataTypeConverter(String databaseProductName, MetaColumn metaColumn) throws IOException {
        super(databaseProductName, metaColumn);
    }

    @Override
    public String getColumnType() throws IOException {
        String restored = restoreNativeOriginalType();
        if (restored != null) {
            return restored;
        }
        return super.getColumnType();
    }

    @Override
    protected String getAlternativeType(String dataType) {
        if (dataType.startsWith("INTERVAL")) {
            return "VARCHAR(255)";
        }
        return COMPATIBLE_DATA_TYPE_MAP.get(dataType);
    }

    @Override
    protected String appendSpecifier(String dataType, String specifier) {
        return super.appendSpecifier(dataType, normalizeSpecifier(dataType, specifier));
    }

    @Override
    protected String getDefaultSpecifier(String dataType) {
        return DEFAULT_COLUMN_SPECIFIER.get(dataType);
    }

    @Override
    protected boolean requiresSpecifier(String dataType) {
        return DEFAULT_COLUMN_SPECIFIER.containsKey(dataType);
    }

    private String normalizeSpecifier(String dataType, String specifier) {
        if (specifier == null || specifier.isEmpty()) {
            return specifier;
        }

        String normalizedType = dataType;
        int specifierIndex = normalizedType.indexOf("(");
        if (specifierIndex > -1) {
            normalizedType = normalizedType.substring(0, specifierIndex);
        }

        if (isVariableLengthType(normalizedType) && isSqlServerMaxSpecifier(normalizedType, specifier)) {
            return "(MAX)";
        }
        return specifier;
    }

    private boolean isVariableLengthType(String dataType) {
        return "VARCHAR".equals(dataType) || "NVARCHAR".equals(dataType) || "VARBINARY".equals(dataType);
    }

    private boolean isSqlServerMaxSpecifier(String dataType, String specifier) {
        String normalized = specifier.replace("(", "")
                .replace(")", "")
                .trim()
                .toUpperCase();
        if (normalized.endsWith("K") || normalized.endsWith("M") || normalized.endsWith("G")) {
            return true;
        }

        try {
            long length = Long.parseLong(normalized);
            return length > maxLength(dataType);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private long maxLength(String dataType) {
        if ("NVARCHAR".equals(dataType)) {
            return 4000L;
        }
        return 8000L;
    }

    private String restoreNativeOriginalType() {
        String original = originalBaseType();
        return switch (original) {
            case "UNIQUEIDENTIFIER" -> "UNIQUEIDENTIFIER";
            case "DATETIMEOFFSET" -> "DATETIMEOFFSET" + dateTimeOffsetPrecision();
            case "TINYINT" -> "TINYINT";
            case "MONEY" -> "MONEY";
            case "SMALLMONEY" -> "SMALLMONEY";
            default -> null;
        };
    }

    private String originalBaseType() {
        if (originalDataType == null) {
            return "";
        }
        String original = originalDataType.replace("\"", "").trim().toUpperCase();
        int specifierIndex = original.indexOf("(");
        if (specifierIndex > -1) {
            return original.substring(0, specifierIndex).trim();
        }
        return original;
    }

    private String dateTimeOffsetPrecision() {
        String precision = extractDateTimePrecision(originalDataType);
        if (precision == null && (dataType.startsWith("TIMESTAMP") || dataType.startsWith("TIME"))) {
            precision = extractDateTimePrecision(dataType);
        }
        return precision == null ? "(7)" : "(" + precision + ")";
    }

    private String extractDateTimePrecision(String type) {
        if (type == null) {
            return null;
        }
        int openIndex = type.indexOf("(");
        int closeIndex = type.indexOf(")", openIndex + 1);
        if (openIndex < 0 || closeIndex <= openIndex) {
            return null;
        }
        String precision = type.substring(openIndex + 1, closeIndex).trim();
        return precision.matches("[0-7]") ? precision : null;
    }
}
