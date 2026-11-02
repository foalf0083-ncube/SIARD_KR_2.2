// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.cmd.utils.CmdI18n;
import ch.admin.bar.siard2.api.*;
import ch.admin.bar.siard2.api.generated.CategoryType;
import ch.admin.bar.siard2.api.generated.ReferentialActionType;
import ch.admin.bar.siard2.api.meta.*;
import ch.admin.bar.siard2.cmd.utils.ByteFormatter;
import ch.enterag.sqlparser.BaseSqlFactory;
import ch.enterag.sqlparser.SqlLiterals;
import ch.enterag.sqlparser.datatype.DataType;
import ch.enterag.sqlparser.datatype.PredefinedType;
import ch.enterag.sqlparser.identifier.QualifiedId;
import ch.enterag.utils.EU;
import ch.enterag.utils.ProgramInfo;
import ch.enterag.utils.background.Progress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.*;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.admin.bar.dbexception.DatabaseExceptionHandlerHelper.doHandleSqlException;

public class MetaDataFromDb extends MetaDataBase {
    private static final Logger LOG = LoggerFactory.getLogger(MetaDataFromDb.class);

    static final Pattern _patARRAY_CONSTRUCTOR = Pattern.compile("^\\s*(.*?)\\s+ARRAY\\s*\\[\\s*(\\d+)\\s*\\]$");
    private boolean _bMaxLobNeeded = false;
    /** 화면 미리보기용 읽기. 고를 테이블 목록만 보여 주면 되므로 자료를 훑는 일(값 측정·행 수)과 루틴은 건너뛴다. */
    private boolean _bPreviewLight = false;
    /** Skip only the optional pre-extraction COUNT; keep complete type/key metadata. */
    public static final String PROPERTY_COUNT_ROWS = "siard.metadata.countRows";
    private boolean _bCountRows = Boolean.parseBoolean(System.getProperty(PROPERTY_COUNT_ROWS, "true"));

    public void setCountRows(boolean countRows) { this._bCountRows = countRows; }

    boolean needsRowScan() { return _bCountRows || _bMaxLobNeeded; }

    void collectRows(MetaTable table) throws IOException, SQLException {
        if (needsRowScan()) getRows(table);
    }

    public void setPreviewLight(boolean bPreviewLight) {
        this._bPreviewLight = bPreviewLight;
    }
    private MetaColumn _mcMaxLob = null;
    private long _lMaxLobSize;
    private Progress _progress;
    private boolean _bViewsAsTables;
    private int _iTablesAnalyzed;
    private int _iTables;
    private int _iTablesPercent;
    private Map<String, Set<String>> _selectedTables = Collections.emptyMap();

    String databaseProductName;

    private MetaDataFromDb(DatabaseMetaData dmd, MetaData md) throws SQLException {
        super(dmd, md, false);
        this._lMaxLobSize = -1L;
        this._progress = null;
        this._bViewsAsTables = false;
        this._iTablesAnalyzed = -1;
        this._iTables = -1;
        this._iTablesPercent = -1;
    }

    public static MetaDataFromDb newInstance(DatabaseMetaData dmd, MetaData md) throws SQLException {
        return new MetaDataFromDb(dmd, md);
    }

    public MetaColumn getMaxLobColumn() {
        return this._mcMaxLob;
    }

    private boolean checkMetaTable(MetaTable mt) {
        boolean bMetaDataOk = true;
        System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaTable", mt.getName()));
        try {
            for (int iColumn = 0; bMetaDataOk && iColumn < mt.getMetaColumns(); iColumn++) {
                MetaColumn mc = mt.getMetaColumn(iColumn);
                System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaColumn", mc.getName()));
                String sTypeName = mc.getTypeName();
                String sTypeSchema = mc.getTypeSchema();
                if (sTypeName != null && sTypeSchema == null) {
                    System.err.println(CmdI18n.get("cmd.metaFromDb.errorInColumnOfTable", mc.getName(), mt.getName()));
                    bMetaDataOk = false;
                }
                ((MetaColumnImpl) mc).getColumnType();
            }
            ((MetaTableImpl) mt).getTableType();
        } catch (Exception e) {
            System.err.println(EU.getExceptionMessage(e));
            bMetaDataOk = false;
        }
        return bMetaDataOk;
    }

    private boolean checkMetaView(MetaView mv) {
        boolean bMetaDataOk = true;
        System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaView", mv.getName()));
        try {
            for (int iColumn = 0; bMetaDataOk && iColumn < mv.getMetaColumns(); iColumn++) {
                MetaColumn mc = mv.getMetaColumn(iColumn);
                System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaColumn", mc.getName()));
                String sTypeName = mc.getTypeName();
                String sTypeSchema = mc.getTypeSchema();
                if (sTypeName != null && sTypeSchema == null) {
                    System.err.println(CmdI18n.get("cmd.metaFromDb.errorInColumnOfView", mc.getName(), mv.getName()));
                    bMetaDataOk = false;
                }
                ((MetaColumnImpl) mc).getColumnType();
            }
            ((MetaViewImpl) mv).getViewType();
        } catch (Exception e) {
            System.err.println(EU.getExceptionMessage(e));
            bMetaDataOk = false;
        }
        return bMetaDataOk;
    }

    private boolean checkMetaType(MetaType mt) {
        boolean bMetaDataOk = true;
        System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaType", mt.getName()));
        try {
            for (int iAttribute = 0; bMetaDataOk && iAttribute < mt.getMetaAttributes(); iAttribute++) {
                MetaAttribute ma = mt.getMetaAttribute(iAttribute);
                System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaAttribute", ma.getName()));
                String sTypeName = ma.getTypeName();
                String sTypeSchema = ma.getTypeSchema();
                if (sTypeName != null && sTypeSchema == null) {
                    System.err.println(CmdI18n.get("cmd.metaFromDb.errorInAttributeOfType", ma.getName(), mt.getName()));
                    bMetaDataOk = false;
                }
                ((MetaAttributeImpl) ma).getAttributeType();
            }
            ((MetaTypeImpl) mt).getTypeType();
        } catch (Exception e) {
            System.err.println(EU.getExceptionMessage(e));
            bMetaDataOk = false;
        }
        return bMetaDataOk;
    }

    private boolean checkMetaRoutine(MetaRoutine mr) {
        boolean bMetaDataOk = true;
        System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaRoutine", mr.getSpecificName()));
        try {
            for (int iParameter = 0; bMetaDataOk && iParameter < mr.getMetaParameters(); iParameter++) {
                MetaParameter mp = mr.getMetaParameter(iParameter);
                System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaParameter", mp.getName()));
                String sTypeName = mp.getTypeName();
                String sTypeSchema = mp.getTypeSchema();
                if (sTypeName != null && sTypeSchema == null) {
                    System.err.println(CmdI18n.get("cmd.metaFromDb.errorInParameterOfRoutine", mr.getName(), mr.getName()));
                    bMetaDataOk = false;
                }
                ((MetaParameterImpl) mp).getParameterType();
            }
            ((MetaRoutineImpl) mr).getRoutineType();
        } catch (Exception e) {
            System.err.println(EU.getExceptionMessage(e));
            bMetaDataOk = false;
        }
        return bMetaDataOk;
    }

    private boolean checkMetaSchema(MetaSchema ms) {
        boolean bMetaDataOk = true;
        System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaSchema", ms.getName()));
        try {
            if (ms.getSchema().getTables() == ms.getMetaTables()) {
                for (int iTable = 0; bMetaDataOk && iTable < ms.getMetaTables(); iTable++)
                    bMetaDataOk = checkMetaTable(ms.getMetaTable(iTable));
            } else {
                System.err.println(CmdI18n.get("cmd.metaFromDb.invalidNumberOfTablesInSchema", ms.getName()));
                bMetaDataOk = false;
            }
            for (int iView = 0; bMetaDataOk && iView < ms.getMetaViews(); iView++)
                bMetaDataOk = checkMetaView(ms.getMetaView(iView));
            for (int iType = 0; bMetaDataOk && iType < ms.getMetaTypes(); iType++)
                bMetaDataOk = checkMetaType(ms.getMetaType(iType));
            for (int iRoutine = 0; bMetaDataOk && iRoutine < ms.getMetaRoutines(); iRoutine++)
                bMetaDataOk = checkMetaRoutine(ms.getMetaRoutine(iRoutine));
            ((MetaSchemaImpl) ms).getSchemaType();
        } catch (Exception e) {
            System.err.println(EU.getExceptionMessage(e));
            bMetaDataOk = false;
        }
        return bMetaDataOk;
    }

    public boolean checkMetaData() {
        boolean bMetaDataOk = true;
        System.out.println(CmdI18n.get("cmd.metaFromDb.checkMetaData"));
        try {
            if (this._md.getArchive().getSchemas() == this._md.getMetaSchemas()) {
                for (int iSchema = 0; bMetaDataOk && iSchema < this._md.getMetaSchemas(); iSchema++)
                    bMetaDataOk = checkMetaSchema(this._md.getMetaSchema(iSchema));
                ((MetaDataImpl) this._md).getSiardArchive();
            } else {
                System.err.println(CmdI18n.get("cmd.metaFromDb.invalidNumberOfSchema"));
                bMetaDataOk = false;
            }
        } catch (Exception e) {
            System.err.println(EU.getExceptionMessage(e));
            bMetaDataOk = false;
        }
        return bMetaDataOk;
    }


    private void incTablesAnalyzed() {
        this._iTablesAnalyzed++;
        if (this._progress != null && this._iTables > 0 && this._iTablesAnalyzed % this._iTablesPercent == 0) {
            int iPercent = 100 * this._iTablesAnalyzed / this._iTables;
            this._progress.notifyProgress(iPercent);
        }
    }


    private boolean cancelRequested() {
        if (this._progress != null && this._progress.cancelRequested()) {
            LOG.info("Cancel downloading of meta data because of request");
            return true;
        }
        return false;
    }


    private String getReferentialAction(int iReferentialAction) {
        ReferentialActionType rat = null;
        switch (iReferentialAction) {
            case 0:
                rat = ReferentialActionType.CASCADE;
                break;
            case 2:
                rat = ReferentialActionType.SET_NULL;
                break;
            case 4:
                rat = ReferentialActionType.SET_DEFAULT;
                break;
            case 1:
                rat = ReferentialActionType.RESTRICT;
                break;
            case 3:
                rat = ReferentialActionType.NO_ACTION;
                break;
        }
        return rat.value();
    }


    private void getAttributes(MetaType mt) throws IOException, SQLException {
        int iPosition = 0;
        CategoryType cat = mt.getCategoryType();
        String schemaName = mt.getParentMetaSchema().getName();
        String tableName = mt.getName();
        ResultSet rs = this._dmd.getAttributes(null,
                getPatternedName(schemaName),
                getPatternedName(tableName),
                "%");

        while (rs.next()) {
            iPosition++;
            String sTypeSchema = rs.getString("TYPE_SCHEM");
            if (!sTypeSchema.equals(mt.getParentMetaSchema().getName()))
                throw new IOException("Attribute with unexpected type schema found!");
            String sTypeName = rs.getString("TYPE_NAME");
            if (!sTypeName.equals(mt.getName())) throw new IOException("Attribute with unexpected type name found");
            String sAttributeName = rs.getString("ATTR_NAME");
            int iDataType = rs.getInt("DATA_TYPE");
            String sAttrTypeName = rs.getString("ATTR_TYPE_NAME");
            int iAttrSize = rs.getInt("ATTR_SIZE");
            int iDecimalDigits = rs.getInt("DECIMAL_DIGITS");
            MetaType mtAttr = null;
            if (iDataType == 2001 || iDataType == 2002)
                mtAttr = createType(sAttrTypeName, mt.getParentMetaSchema().getName(), iAttrSize, iDecimalDigits);
            if (cat == CategoryType.DISTINCT) {
                mt.setBase(sTypeName);
                if (iDataType != 2001 && iDataType != 2003 && iDataType != 2002)
                    mt.setBasePreType(iDataType, iAttrSize, iDecimalDigits);
                continue;
            }
            MetaAttribute ma = mt.createMetaAttribute(sAttributeName);
            if (iDataType != 2001 && iDataType != 2003 && iDataType != 2002) {
                if (iDataType == 1111) {
                    ma.setType(sAttrTypeName);
                } else {
                    ma.setPreType(iDataType, iAttrSize, iDecimalDigits);
                }
            } else if (iDataType == 2003) {
                Matcher m = _patARRAY_CONSTRUCTOR.matcher(sAttrTypeName);
                if (m.matches()) {
                    String sBaseType = m.group(1);

                    BaseSqlFactory bsf = new BaseSqlFactory();
                    DataType dt = bsf.newDataType();
                    dt.parse(sBaseType);
                    if (dt.getPredefinedType() != null) {
                        ma.setType(dt.format());
                    } else {
                        MetaType mtyBase = createType(sBaseType, sTypeSchema, -1, -1);


                        QualifiedId qiTypeBase = new QualifiedId(null, mtyBase.getParentMetaSchema().getName(), mtyBase.getName());
                        ma.setTypeName(qiTypeBase.getName());
                        ma.setTypeSchema(qiTypeBase.getSchema());
                    }

                    ma.setCardinality(Integer.parseInt(m.group(2)));
                } else {
                    throw new SQLException("Invalid ARRAY constructor for attribute " + ma.getName() + " of type " + mt.getName() + "!");
                }
            } else {
                ma.setTypeName(mtAttr.getName());
                ma.setTypeSchema(mtAttr.getParentMetaSchema().getName());
            }

            int iNullable = rs.getInt("NULLABLE");
            if (iNullable == 0) {
                ma.setNullable(false);
            } else if (iNullable == 1) {
                ma.setNullable(true);
            }
            String sAttributeDefault = rs.getString("ATTR_DEF");
            if (sAttributeDefault != null) ma.setDefaultValue(sAttributeDefault);
            String sRemarks = rs.getString("REMARKS");
            if (sRemarks != null) ma.setDescription(sRemarks);
            int iOrdinalPosition = rs.getInt("ORDINAL_POSITION");
            if (iOrdinalPosition != iPosition) throw new IOException("Invalid ordinal position of attribute!");

        }
        rs.close();
    }


    private MetaType createType(String sTypeName, String sDefaultSchema, int iPrecision, int iScale) throws IOException, SQLException {
        MetaType mt = null;
        try {
            QualifiedId qiType = new QualifiedId(sTypeName);
            String sTypeSchema = qiType.getSchema();
            if (sTypeSchema == null) {
                sTypeSchema = sDefaultSchema;
                qiType.setSchema(sTypeSchema);
                qiType.parseName(sTypeName);
            }
            Schema schema = this._md.getArchive().getSchema(qiType.getSchema());
            if (schema == null) schema = this._md.getArchive().createSchema(qiType.getSchema());
            MetaSchema ms = schema.getMetaSchema();
            mt = ms.getMetaType(qiType.getName());
            if (mt == null) {
                System.out.println(CmdI18n.get("cmd.metaFromDb.type", qiType.format()));
                mt = ms.createMetaType(qiType.getName());
                String schemaName = qiType.getSchema();
                String tableName = qiType.getName();
                ResultSet rs = this._dmd.getUDTs(null,
                        getPatternedName(schemaName),
                        getPatternedName(tableName),
                        new int[]{2001, 2002});

                if (!rs.next()) {
                    rs.close();
                    throw new SQLException("Type " + qiType.format() + " not found in user-defined types!");
                }
                if (qiType.getName().equals(rs.getString("TYPE_NAME")) && qiType.getSchema()
                        .equals(rs.getString("TYPE_SCHEM"))) {
                    String sRemarks = rs.getString("REMARKS");
                    if (sRemarks != null) mt.setDescription(sRemarks);
                    BaseSqlFactory bsf = new BaseSqlFactory();
                    PredefinedType pt = bsf.newPredefinedType();
                    int iBaseType = rs.getInt("BASE_TYPE");
                    if (iBaseType != 0) {
                        mt.setCategory(CategoryType.DISTINCT.value());
                        pt.initialize(iBaseType, iPrecision, iScale);
                        mt.setBase(pt.format());
                    } else {
                        mt.setCategory(CategoryType.UDT.value());
                        getAttributes(mt);
                    }
                } else {
                    throw new SQLException("Invalid type meta data found!");
                }
                rs.close();
            }
        } catch (ParseException pe) {
            throw new SQLException("Type name " + sTypeName + " could not be parsed!", pe);
        }
        return mt;
    }


    private void getColumns(MetaView mv) throws IOException, SQLException {
        String schemaName = mv.getParentMetaSchema().getName();
        String tableName = mv.getName();
        ResultSet rs = this._dmd.getColumns(null,
                getPatternedName(schemaName),
                getPatternedName(tableName),
                "%");

        while (rs.next()) {
            String sTableSchema = rs.getString("TABLE_SCHEM");
            if (sTableSchema != null && !schemaNamesMatch(mv.getParentMetaSchema().getName(),
                    sTableSchema, rs.getString("TABLE_NAME")))
                throw new IOException("Invalid view schema for column found!");
            String sTableName = rs.getString("TABLE_NAME");
            if (!tableNamesMatch(sTableName, mv.getName())) throw new IOException("Invalid view name for column found!");
            String sColumnName = rs.getString("COLUMN_NAME");
            MetaColumn mc = mv.createMetaColumn(sColumnName);
            getColumnData(rs, mc);
        }
        rs.close();
    }


    private void getProcedureParameters(MetaRoutine mr) throws IOException, SQLException {
        String schemaName = mr.getParentMetaSchema().getName();
        String tableName = mr.getName();
        ResultSet rs = this._dmd.getProcedureColumns(null,
                getPatternedName(schemaName),
                getPatternedName(tableName),
                "%");

        while (rs.next()) {
            String sProcedureSchema = rs.getString("PROCEDURE_SCHEM");
            if (!sProcedureSchema.equals(mr.getParentMetaSchema().getName()))
                throw new IOException("Invalid procedure parameter schema encountered!");
            String sProcedureName = rs.getString("PROCEDURE_NAME");
            if (!sProcedureName.equals(mr.getName()))
                throw new IOException("Invalid procedure parameter name encountered!");
            String sParameterName = rs.getString("COLUMN_NAME");
            int iColumnType = rs.getInt("COLUMN_TYPE");
            int iDataType = rs.getInt("DATA_TYPE");
            String sTypeName = rs.getString("TYPE_NAME");
            MetaType mt = null;
            long lPrecision = rs.getLong("PRECISION");
            int iScale = rs.getInt("SCALE");
            if (iDataType == 2001 || iDataType == 2002) {
                mt = createType(sTypeName, mr.getParentMetaSchema().getName(), (int) lPrecision, iScale);
            }
            String sRemarks = rs.getString("REMARKS");
            int iOrdinalPosition = rs.getInt("ORDINAL_POSITION");
            String sSpecificName = rs.getString("SPECIFIC_NAME");
            if (sSpecificName == null) sSpecificName = sProcedureName;

            if (sSpecificName.equals(mr.getSpecificName())) {
                if (iColumnType == 5 || iColumnType == 3) {
                    mr.setReturnType(sTypeName);
                    if (iDataType != 2001 && iDataType != 2003 && iDataType != 2002)
                        mr.setReturnPreType(iDataType, lPrecision, iScale);
                    continue;
                }
                MetaParameter mp = mr.createMetaParameter(sParameterName);
                if (iOrdinalPosition != mr.getMetaParameters())
                    throw new IOException("Invalid order of procedure columns!");
                switch (iColumnType) {
                    case 1:
                        mp.setMode("IN");
                        break;
                    case 4:
                        mp.setMode("OUT");
                        break;
                    case 2:
                        mp.setMode("INOUT");
                        break;
                }


                if (iDataType != 2001 && iDataType != 2003 && iDataType != 2002) {
                    mp.setPreType(iDataType, lPrecision, iScale);
                    mp.setTypeOriginal(sTypeName);
                } else if (iDataType == 2003) {
                    Matcher m = _patARRAY_CONSTRUCTOR.matcher(sTypeName);
                    if (m.matches()) {
                        String sBaseType = m.group(1);

                        BaseSqlFactory bsf = new BaseSqlFactory();
                        DataType dt = bsf.newDataType();
                        dt.parse(sBaseType);
                        if (dt.getPredefinedType() != null) {
                            mp.setType(dt.format());
                        } else {
                            MetaType mtyBase = createType(sBaseType, sProcedureSchema, -1, -1);


                            QualifiedId qiTypeBase = new QualifiedId(null, mtyBase.getParentMetaSchema().getName(), mtyBase.getName());
                            mp.setTypeName(qiTypeBase.getName());
                            mp.setTypeSchema(qiTypeBase.getSchema());
                        }

                        mp.setCardinality(Integer.parseInt(m.group(2)));
                    } else {
                        throw new SQLException("Invalid ARRAY constructor for parameter " + mp.getName() + " of routine " + mr.getName() + "!");
                    }
                } else {
                    mp.setTypeName(mt.getName());
                    mp.setTypeSchema(mt.getParentMetaSchema().getName());
                }

                if (sRemarks != null) mp.setDescription(sRemarks);

            }
        }
        rs.close();
    }


    private void getFunctionParameters(MetaRoutine mr) throws IOException, SQLException {
        String schemaName = mr.getParentMetaSchema().getName();
        String tableName = mr.getName();
        ResultSet rs = this._dmd.getFunctionColumns(null,
                getPatternedName(schemaName),
                getPatternedName(tableName),
                "%");

        while (rs.next()) {
            String sFunctionSchema = rs.getString("FUNCTION_SCHEM");
            if (!mr.getParentMetaSchema().getName().equals(sFunctionSchema))
                throw new IOException("Invalid function parameter schema encountered!");
            String sFunctionName = rs.getString("FUNCTION_NAME");
            if (!mr.getName().equals(sFunctionName))
                throw new IOException("Invalid function parameter name encountered!");
            String sParameterName = rs.getString("COLUMN_NAME");
            int iColumnType = rs.getInt("COLUMN_TYPE");
            int iDataType = rs.getInt("DATA_TYPE");
            String sTypeName = rs.getString("TYPE_NAME");
            long lPrecision = rs.getLong("PRECISION");
            int iScale = rs.getInt("SCALE");
            MetaType mt = null;
            if (iDataType == 2001 || iDataType == 2002)
                mt = createType(sTypeName, mr.getParentMetaSchema().getName(), (int) lPrecision, iScale);
            String sRemarks = rs.getString("REMARKS");
            int iOrdinalPosition = rs.getInt("ORDINAL_POSITION");
            String sSpecificName = rs.getString("SPECIFIC_NAME");
            if (sSpecificName == null) sSpecificName = sFunctionName;

            if (sSpecificName.equals(mr.getSpecificName())) {


                if (iColumnType == 5 || iColumnType == 3) {
                    mr.setReturnType(sTypeName);
                    if (iDataType != 2001 && iDataType != 2003 && iDataType != 2002)
                        mr.setReturnPreType(iDataType, lPrecision, iScale);
                    continue;
                }
                MetaParameter mp = mr.createMetaParameter(sParameterName);
                if (iOrdinalPosition != mr.getMetaParameters())
                    throw new IOException("Invalid order of function columns!");
                switch (iColumnType) {
                    case 1:
                        mp.setMode("IN");
                        break;
                    case 4:
                        mp.setMode("OUT");
                        break;
                    case 2:
                        mp.setMode("INOUT");
                        break;
                }


                if (iDataType != 2001 && iDataType != 2003 && iDataType != 2002) {
                    mp.setPreType(iDataType, lPrecision, iScale);
                    mp.setTypeOriginal(sTypeName);
                } else if (iDataType == 2003) {
                    Matcher m = _patARRAY_CONSTRUCTOR.matcher(sTypeName);
                    if (m.matches()) {
                        String sBaseType = m.group(1);

                        BaseSqlFactory bsf = new BaseSqlFactory();
                        DataType dt = bsf.newDataType();
                        dt.parse(sBaseType);
                        if (dt.getPredefinedType() != null) {
                            mp.setType(dt.format());
                        } else {
                            MetaType mtyBase = createType(sBaseType, sFunctionSchema, -1, -1);


                            QualifiedId qiTypeBase = new QualifiedId(null, mtyBase.getParentMetaSchema().getName(), mtyBase.getName());
                            mp.setTypeName(qiTypeBase.getName());
                            mp.setTypeSchema(qiTypeBase.getSchema());
                        }

                        mp.setCardinality(Integer.parseInt(m.group(2)));
                    } else {
                        throw new SQLException("Invalid ARRAY constructor for parameter " + mp.getName() + " of routine " + mr.getName() + "!");
                    }
                } else {
                    mp.setTypeName(mt.getName());
                    mp.setTypeSchema(mt.getParentMetaSchema().getName());
                }

                if (sRemarks != null) mp.setDescription(sRemarks);

            }
        }
        rs.close();
    }


    private void addReferences(MetaTable mt, String sForeignKeyName, Map<Integer, String> mapFkColumns, Map<String, String> mapPkColumns) throws IOException {
        if (sForeignKeyName != null) {

            MetaForeignKey mfk = mt.getMetaForeignKey(sForeignKeyName);
            for (int iColumn = 0; iColumn < mapFkColumns.size(); iColumn++) {
                String sFkColumnName = mapFkColumns.get(Integer.valueOf(iColumn + 1));
                mfk.addReference(sFkColumnName, mapPkColumns.get(sFkColumnName));
            }
            mapFkColumns.clear();
            mapPkColumns.clear();
        }
    }


    private void addColumns(MetaTable mt, String sUniqueKeyName, Map<Integer, String> mapUniqueColumns) throws IOException {
        if (sUniqueKeyName != null) {
            MetaUniqueKey muk = mt.getMetaCandidateKey(sUniqueKeyName);
            for (int iColumn = 0; iColumn < mapUniqueColumns.size(); iColumn++) {
                String sColumnName = mapUniqueColumns.get(Integer.valueOf(iColumn + 1));
                muk.addColumn(sColumnName);
            }
            mapUniqueColumns.clear();
        }
    }


    /** Oracle 이면 십진 부동 소수 컬럼을 재는 일. 같은 컬럼을 두 번 재지 않도록 하나만 둔다. */
    private OracleNumberMeasure _oracleNumberMeasure = null;
    /** Oracle 이면 문자 의미 컬럼의 선언 길이. 같은 테이블을 두 번 읽지 않도록 하나만 둔다. */
    private OracleCharLength _oracleCharLength = null;

    private boolean isOracleDb() {
        try {
            return DataBase.ORACLE.equals(DataBase.findByName(getDatabaseProductName()));
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private void getColumnData(ResultSet rs, MetaColumn mc) throws IOException, SQLException {
        int iDataType = rs.getInt("DATA_TYPE");
        String sTypeName = rs.getString("TYPE_NAME");
        long lColumnSize = rs.getLong("COLUMN_SIZE");
        int iDecimalDigits = rs.getInt("DECIMAL_DIGITS");
        MetaSchema ms = null;
        QualifiedId qiParent = null;
        if (mc.getParentMetaTable() != null) {
            MetaTable mt = mc.getParentMetaTable();
            ms = mt.getParentMetaSchema();
            qiParent = new QualifiedId(null, ms.getName(), mt.getName());
        } else {
            MetaView mv = mc.getParentMetaView();
            ms = mv.getParentMetaSchema();
            qiParent = new QualifiedId(null, ms.getName(), mv.getName());
        }
        // Oracle 래퍼는 정밀도 없는 NUMBER 와 FLOAT 를 FLOAT 로 보고해 값이 배정밀도로 깎인다. 자료를 담는 테이블
        // (-v 로 테이블로 담는 뷰 포함)의 컬럼은 값을 재어 NUMERIC(p,s) 로 적는다. 뷰 정의의 컬럼은 자료가 없어 재지 않는다.
        if (!this._bPreviewLight && (mc.getParentMetaTable() != null) && OracleNumberMeasure.isDecimalFloat(iDataType, sTypeName) && isOracleDb()) {
            if (_oracleNumberMeasure == null) {
                _oracleNumberMeasure = new OracleNumberMeasure(this._dmd.getConnection());
            }
            int[] aiMeasured = _oracleNumberMeasure.measure(qiParent.getSchema(), qiParent.getName(), mc.getName());
            if (aiMeasured != null) {
                iDataType = Types.NUMERIC;
                lColumnSize = aiMeasured[0];
                iDecimalDigits = aiMeasured[1];
            }
        }
        // Oracle 래퍼는 문자 의미 컬럼(VARCHAR2(n CHAR) · NCHAR 등)의 길이를 바이트로 보고한다. 선언 길이로 바꾼다.
        if (OracleCharLength.isCharacterType(sTypeName) && isOracleDb()) {
            if (_oracleCharLength == null) {
                _oracleCharLength = new OracleCharLength(this._dmd.getConnection());
            }
            Integer iChars = _oracleCharLength.of(qiParent.getSchema(), qiParent.getName(), mc.getName());
            if (iChars != null) {
                lColumnSize = iChars.longValue();
            }
        }
        MetaType mty = null;
        if (iDataType == 2001 || iDataType == 2002)
            mty = createType(sTypeName, ms.getName(), (int) lColumnSize, iDecimalDigits);
        if (iDataType != 2001 && iDataType != 2003 && iDataType != 2002) {
            if (iDataType != 1111) {
                mc.setPreType(iDataType, lColumnSize, iDecimalDigits);
            } else {
                mc.setType(sTypeName);
            }
            mc.setTypeOriginal(sTypeName);
        } else if (iDataType == 2003) {
            Matcher m = _patARRAY_CONSTRUCTOR.matcher(sTypeName);
            if (m.matches()) {
                String sBaseType = m.group(1);

                BaseSqlFactory bsf = new BaseSqlFactory();
                DataType dt = bsf.newDataType();
                dt.parse(sBaseType);
                if (dt.getPredefinedType() != null) {
                    mc.setType(dt.format());
                } else {
                    MetaType mtyBase = createType(sBaseType, ms.getName(), -1, -1);
                    QualifiedId qiTypeBase = new QualifiedId(null, mtyBase.getParentMetaSchema().getName(), mtyBase.getName());
                    mc.setTypeName(qiTypeBase.getName());
                    mc.setTypeSchema(qiTypeBase.getSchema());
                }

                mc.setCardinality(Integer.parseInt(m.group(2)));
                mc.setTypeOriginal(sTypeName);
            } else {
                throw new SQLException("Invalid ARRAY constructor for column " + mc.getName() + " of table " + qiParent.format() + "!");
            }
        } else {
            mc.setTypeName(mty.getName());
            mc.setTypeSchema(mty.getParentMetaSchema().getName());
        }

        int iNullable = rs.getInt("NULLABLE");
        if (mc.getParentMetaTable() != null)
            if (iNullable == 0) {
                mc.setNullable(false);
            } else if (iNullable == 1) {
                mc.setNullable(true);
            }

        String sRemarks = rs.getString("REMARKS");
        if (sRemarks != null) mc.setDescription(sRemarks);
        String sColumnDefault = rs.getString("COLUMN_DEF");
        if (mc.getParentMetaTable() != null && sColumnDefault != null) mc.setDefaultValue(sColumnDefault);

        int iOrdinalPosition = rs.getInt("ORDINAL_POSITION");
        int metaColumnPosition = mc.getPosition();
        if (iOrdinalPosition != metaColumnPosition)
            throw new IOException(String.format(
                    "컬럼 순서가 맞지 않습니다. 위치=%s.%s 드라이버=%d 내부=%d",
                    qiParent.format(), mc.getName(), iOrdinalPosition, metaColumnPosition));

    }


    private void getRoles() throws IOException, SQLException {
        for (int iPrivilege = 0; iPrivilege < this._md.getMetaPrivileges(); iPrivilege++) {
            MetaPrivilege mp = this._md.getMetaPrivilege(iPrivilege);
            String sGrantee = mp.getGrantee();
            MetaRole mrGrantee = this._md.getMetaRole(sGrantee);
            MetaUser muGrantee = this._md.getMetaUser(sGrantee);
            if (mrGrantee == null && muGrantee == null) {
                mrGrantee = this._md.createMetaRole(sGrantee, null);
                mrGrantee.setAdmin(mp.getGrantor());
            }
        }
    }


    private void getUsers() throws IOException, SQLException {
        this._md.createMetaUser(this._dmd.getUserName());

        for (int iPrivilege = 0; iPrivilege < this._md.getMetaPrivileges(); iPrivilege++) {
            MetaPrivilege mp = this._md.getMetaPrivilege(iPrivilege);
            String sGrantor = mp.getGrantor();
            MetaUser muGrantor = this._md.getMetaUser(sGrantor);
            if (muGrantor == null) muGrantor = this._md.createMetaUser(sGrantor);

        }
    }


    private void getPrivileges() throws IOException, SQLException {
        for (int iSchema = 0; iSchema < this._md.getMetaSchemas(); iSchema++) {
            MetaSchema ms = this._md.getMetaSchema(iSchema);
            for (int iTable = 0; iTable < ms.getMetaTables(); iTable++) {
                MetaTable mt = ms.getMetaTable(iTable);

                String schemaName = ms.getName();
                String tableName = mt.getName();

                ResultSet rs = this._dmd.getTablePrivileges(null,
                        getPatternedName(schemaName),
                        getPatternedName(tableName)
                );

                while (rs.next()) {
                    String sTableSchema = rs.getString("TABLE_SCHEM");
                    String sTableName = rs.getString("TABLE_NAME");
                    String sGrantor = rs.getString("GRANTOR");
                    String sGrantee = rs.getString("GRANTEE");
                    String sPrivilege = rs.getString("PRIVILEGE");
                    String sIsGrantable = rs.getString("IS_GRANTABLE");
                    // GRANTOR 도 JDBC 규격상 NULL 이 올 수 있다. 이름 없는 사용자가 만들어지면
                    // 보존포맷이 규격을 어기므로, 객체 권한을 준 주체인 스키마 소유자로 적는다.
                    if (sGrantor == null) {
                        sGrantor = schemaName;
                        LOG.info("GRANTOR 가 비어 스키마 소유자로 적음: {}.{} 권한={} 대상={}",
                                schemaName, tableName, sPrivilege, sGrantee);
                    }
                    MetaPrivilege mp = this._md.createMetaPrivilege(sPrivilege, (new QualifiedId(null, sTableSchema, sTableName))


                            .format(), sGrantor, sGrantee);


                    // IS_GRANTABLE 는 JDBC 규격상 알 수 없으면 NULL 이다. 티베로는 NULL 을 준다.
                    if (sIsGrantable != null && !sIsGrantable.equalsIgnoreCase("NO")) mp.setOption("GRANT");
                }
                rs.close();
            }
        }
    }


    private String getQuery(String sViewDefinition) {
        String sQuery = sViewDefinition;
        String[] asParts = sQuery.split("(A|a)(S|s)\\s+(\\-\\-[^\\n]*\\s+)?(S|s)(E|e)(L|l)(E|e)(C|c)(T|t)");
        if (asParts.length > 1) sQuery = "SELECT" + asParts[1];
        return sQuery;
    }


    private void getViews(MetaSchema ms) throws IOException, SQLException {

        String schemaName = ms.getName();
        for (String[] asRow : listViewRows(schemaName)) {
            String sTableSchema = asRow[0];
            if (sTableSchema != null && !sTableSchema.equals(ms.getName())) throw new IOException("Invalid view schema found!");
            String sViewName = asRow[1];
            if (hasSelectedTableFilter() && !isSelectedTable(sTableSchema, sViewName)) continue;
            QualifiedId qiView = new QualifiedId(null, sTableSchema, sViewName);
            System.out.println(CmdI18n.get("cmd.metaFromDb.view", qiView.format()));
            MetaView mv = ms.createMetaView(sViewName);
            try {
                String sTableType = asRow[2];
                if (!sTableType.equals("VIEW")) throw new IOException("Invalid table type for view found!");
                String sRemarks = asRow[3];
                if (sRemarks != null) mv.setDescription(sRemarks);
                String sQueryText = (asRow[4] != null) ? getQuery(asRow[4]) : null;

                if (sQueryText != null) mv.setQueryOriginal(sQueryText);
                getColumns(mv);
            } catch (SQLException se) {
                System.err.println(CmdI18n.get("cmd.metaFromDb.viewCouldNotBeArchived", qiView.format(), EU.getExceptionMessage(se)));
                ms.removeMetaView(mv);
            }
        }
    }


    private void getRoutines(MetaSchema ms) throws IOException, SQLException {
        String schemaName = ms.getName();

        ResultSet rs = this._dmd.getProcedures(null,
                getPatternedName(schemaName),
                "%");

        while (rs.next()) {
            String sProcedureSchema = rs.getString("PROCEDURE_SCHEM");
            if (!sProcedureSchema.equals(ms.getName())) throw new IOException("Invalid procedure schema found!");
            String sProcedureName = rs.getString("PROCEDURE_NAME");
            QualifiedId qiRoutine = new QualifiedId(null, sProcedureSchema, sProcedureName);
            System.out.println(CmdI18n.get("cmd.metaFromDb.routine", qiRoutine.format()));
            String sRemarks = rs.getString("REMARKS");
            String sSpecificName = rs.getString("SPECIFIC_NAME");
            if (sSpecificName == null) sSpecificName = sProcedureName;
            if (ms.getMetaRoutine(sSpecificName) == null
                    && skipUnresolvableRoutine(qiRoutine, findUnresolvableParameterType(sProcedureSchema, sProcedureName, true)))
                continue;
            MetaRoutine mr = ms.createMetaRoutine(sSpecificName);
            mr.setName(sProcedureName);
            if (sRemarks != null) mr.setDescription(sRemarks);
            getProcedureParameters(mr);
        }
        rs.close();
        try {
            rs = this._dmd.getFunctions(null,
                    getPatternedName(schemaName),
                    "%");
            while (rs.next()) {
                String sFunctionSchema = rs.getString("FUNCTION_SCHEM");
                if (!sFunctionSchema.equals(ms.getName())) throw new IOException("Invalid function schema found!");
                String sFunctionName = rs.getString("FUNCTION_NAME");
                String sRemarks = rs.getString("REMARKS");
                String sSpecificName = rs.getString("SPECIFIC_NAME");
                if (sSpecificName == null) sSpecificName = sFunctionName;
                MetaRoutine mr = ms.getMetaRoutine(sSpecificName);
                if (mr == null) {

                    QualifiedId qiFunction = new QualifiedId(null, sFunctionSchema, sFunctionName);
                    System.out.println(CmdI18n.get("cmd.metaFromDb.function", qiFunction.format()));
                    if (skipUnresolvableRoutine(qiFunction, findUnresolvableParameterType(sFunctionSchema, sFunctionName, false)))
                        continue;
                    mr = ms.createMetaRoutine(sSpecificName);
                    mr.setName(sFunctionName);
                    if (sRemarks != null) mr.setDescription(sRemarks);
                    getFunctionParameters(mr);
                }
            }
            rs.close();
        } catch (SQLFeatureNotSupportedException sQLFeatureNotSupportedException) {
        }
    }


    /**
     * 매개변수 형식의 정의를 찾을 수 없는 루틴을 건너뛴다.
     *
     * <p>Oracle 은 다른 스키마의 객체 형식처럼 이 계정에 보이지 않는 형식을 매개변수로
     * 보고하는데 getUDTs 로는 그 정의를 찾을 수 없다. 원래는 여기서 메타데이터 읽기 전체가
     * 멈췄다. 루틴은 테이블 데이터와 무관하므로 경고를 남기고 그 루틴만 뺀다.</p>
     */
    private boolean skipUnresolvableRoutine(QualifiedId qiRoutine, String sUnresolvedType) {
        if (sUnresolvedType == null) return false;
        LOG.warn("루틴 {} 을(를) 건너뜀: 매개변수 형식 {} 의 정의를 찾을 수 없음", qiRoutine.format(), sUnresolvedType);
        System.out.println("    건너뜀: 매개변수 형식 " + sUnresolvedType + " 의 정의를 찾을 수 없음");
        return true;
    }

    private String findUnresolvableParameterType(String sSchema, String sRoutine, boolean bProcedure) throws SQLException {
        ResultSet rs = bProcedure
                ? this._dmd.getProcedureColumns(null, getPatternedName(sSchema), getPatternedName(sRoutine), "%")
                : this._dmd.getFunctionColumns(null, getPatternedName(sSchema), getPatternedName(sRoutine), "%");
        try {
            while (rs.next()) {
                int iDataType = rs.getInt("DATA_TYPE");
                String sTypeName = rs.getString("TYPE_NAME");
                String sCheck = null;
                if (iDataType == 2001 || iDataType == 2002) {
                    sCheck = sTypeName;
                } else if (iDataType == 2003) {
                    Matcher m = _patARRAY_CONSTRUCTOR.matcher(sTypeName == null ? "" : sTypeName);
                    if (!m.matches()) return sTypeName;
                    BaseSqlFactory bsf = new BaseSqlFactory();
                    DataType dt = bsf.newDataType();
                    dt.parse(m.group(1));
                    if (dt.getPredefinedType() == null) sCheck = m.group(1);
                }
                if (sCheck != null && !udtExists(sCheck, sSchema)) return sCheck;
            }
        } finally {
            rs.close();
        }
        return null;
    }

    private boolean udtExists(String sTypeName, String sDefaultSchema) throws SQLException {
        try {
            QualifiedId qiType = new QualifiedId(sTypeName);
            if (qiType.getSchema() == null) {
                qiType.setSchema(sDefaultSchema);
                qiType.parseName(sTypeName);
            }
            Schema schema = this._md.getArchive().getSchema(qiType.getSchema());
            if (schema != null && schema.getMetaSchema().getMetaType(qiType.getName()) != null) return true;
            ResultSet rs = this._dmd.getUDTs(null,
                    getPatternedName(qiType.getSchema()),
                    getPatternedName(qiType.getName()),
                    new int[]{2001, 2002});
            try {
                return rs.next()
                        && qiType.getName().equals(rs.getString("TYPE_NAME"))
                        && qiType.getSchema().equals(rs.getString("TYPE_SCHEM"));
            } finally {
                rs.close();
            }
        } catch (ParseException pe) {
            return false;
        }
    }


    private void getRows(MetaTable mt) throws IOException, SQLException {
        int iLobs = 0;
        String sQuery = "SELECT COUNT(*) AS RECORDS";
        if (this._bMaxLobNeeded) {
            String sProduct = this._dmd.getConnection().getMetaData().getDatabaseProductName()
                    .toLowerCase();
            String sLenFunc = sProduct.contains("sql server") ? "DATALENGTH" : "OCTET_LENGTH";
            boolean bCubrid = sProduct.contains("cubrid");
            for (int iColumn = 0; iColumn < mt.getMetaColumns(); iColumn++) {
                MetaColumn mc = mt.getMetaColumn(iColumn);
                int iPreType = mc.getPreType();
                if (mc.getCardinality() < 0 && (iPreType == 2005 || iPreType == 2011 || iPreType == 2004)) {

                    if (!mc.getTypeOriginal().equals("\"LONG\"")) {

                        String sColLenFunc = bCubrid
                                ? (iPreType == 2004 ? "BLOB_LENGTH" : "CLOB_LENGTH")
                                : sLenFunc;
                        sQuery = sQuery + ",\r\n SUM(" + sColLenFunc + "(" + SqlLiterals.formatId(mc.getName()) + ")) AS " + SqlLiterals.formatId(mc
                                .getName() + "_SIZE");
                        iLobs++;
                    }
                }
            }
        }
        sQuery = sQuery + "\r\nFROM " + ch.admin.bar.siard2.cmd.utils.IdQuoting.safeQualified(
                mt.getParentMetaSchema().getName(), mt.getName());
        LOG.debug("getRows SQL(len={}): [{}]", sQuery.length(), sQuery.replace("\r\n", "|"));
        try (Statement stmtSizes = this._dmd.getConnection().createStatement()) {
        stmtSizes.setQueryTimeout(this._iQueryTimeoutSeconds);
        try (ResultSet rsSizes = stmtSizes.executeQuery(sQuery)) {
        ResultSetMetaData rsmd = rsSizes.getMetaData();
        if (rsSizes.next()) {
            long lRows = rsSizes.getLong("RECORDS");
            mt.setRows(lRows);
            if (this._bMaxLobNeeded)
                for (int iLob = 0; iLob < iLobs; iLob++) {
                    String sLobName = rsmd.getColumnLabel(iLob + 2);
                    long lLobSize = rsSizes.getLong(sLobName);
                    if (this._lMaxLobSize < lLobSize) {
                        this._lMaxLobSize = lLobSize;
                        this._mcMaxLob = mt.getMetaColumn(sLobName.substring(0, sLobName.length() - "_SIZE".length()));
                    }
                }
        } else {
            throw new IOException("Size of table " + mt.getName() + " could not be determined!");
        }
        LOG.debug("Size of table '{}.{}' successfully determined",
                mt.getParentMetaSchema().getName(), mt.getName());

        }
        }
    }


    private void getUniqueKeys(MetaTable mt) throws IOException, SQLException {
        String sUniqueKeyName = null;
        Map<Integer, String> mapUniqueColumns = new HashMap<>();
        ResultSet rs = this._dmd.getIndexInfo(null, mt.getParentMetaSchema().getName(), mt.getName(), true, false);
        while (rs.next()) {
            String sTableSchema = rs.getString("TABLE_SCHEM");
            if (sTableSchema != null && !schemaNamesMatch(mt.getParentMetaSchema().getName(),
                    sTableSchema, rs.getString("TABLE_NAME")))
                throw new IOException("Invalid unique key table schema found!");
            String sTableName = rs.getString("TABLE_NAME");
            if (!tableNamesMatch(sTableName, mt.getName())) throw new IOException("Invalid unique key table name found!");
            boolean bNonUnique = rs.getBoolean("NON_UNIQUE");
            if (bNonUnique) throw new IOException("Invalid non-unique unique index found!");
            String sIndexName = rs.getString("INDEX_NAME");
            int iIndexType = rs.getInt("TYPE");

            boolean bPrimary = mt.getMetaPrimaryKey() != null && mt.getMetaPrimaryKey().getName().equals(sIndexName);
            if (iIndexType != 0 && iIndexType != 3 && !bPrimary) {
                MetaUniqueKey muk = mt.getMetaCandidateKey(sIndexName);
                if (muk == null) {
                    addColumns(mt, sUniqueKeyName, mapUniqueColumns);
                    sUniqueKeyName = sIndexName;
                    muk = mt.createMetaCandidateKey(sUniqueKeyName);
                }
                int iOrdinalPosition = rs.getInt("ORDINAL_POSITION");
                String sColumnName = rs.getString("COLUMN_NAME");
                mapUniqueColumns.put(Integer.valueOf(iOrdinalPosition), sColumnName);
            }

            LOG.debug("Metadata for unique key '{}' (table '{}.{}') loaded", sUniqueKeyName, sTableSchema, sTableName);
        }


        rs.close();
        addColumns(mt, sUniqueKeyName, mapUniqueColumns);
    }


    private void getForeignKeys(MetaTable mt) throws IOException, SQLException {
        String sForeignKeyName = null;
        Map<Integer, String> mapFkColumns = new HashMap<>();
        Map<String, String> mapPkColumns = new HashMap<>();
        ResultSet rs = this._dmd.getImportedKeys(null, mt.getParentMetaSchema().getName(), mt.getName());
        while (rs.next()) {
            String sPkTableSchema = rs.getString("PKTABLE_SCHEM");
            String sPkTableName = rs.getString("PKTABLE_NAME");
            if (sPkTableName != null) {
                int dotPk = sPkTableName.lastIndexOf('.');
                if (dotPk >= 0) {
                    sPkTableSchema = sPkTableName.substring(0, dotPk);
                    sPkTableName = sPkTableName.substring(dotPk + 1);
                }
            }
            String sPkColumnName = rs.getString("PKCOLUMN_NAME");
            if (hasSelectedTableFilter() && !isSelectedTable(sPkTableSchema, sPkTableName)) {
                LOG.warn("Skipping foreign key metadata for {}.{} because it references outside selected table closure: {}.{}",
                        mt.getParentMetaSchema().getName(), mt.getName(), sPkTableSchema, sPkTableName);
                continue;
            }
            String sFkTableSchema = rs.getString("FKTABLE_SCHEM");
            if (sFkTableSchema != null && !schemaNamesMatch(mt.getParentMetaSchema().getName(),
                    sFkTableSchema, rs.getString("FKTABLE_NAME")))
                throw new IOException("Invalid foreign key table schema found!");
            String sFkTableName = rs.getString("FKTABLE_NAME");
            if (!tableNamesMatch(sFkTableName, mt.getName()))
                throw new IOException("Invalid foreign key table name found!");
            String sFkColumnName = rs.getString("FKCOLUMN_NAME");
            MetaColumn mc = mt.getMetaColumn(sFkColumnName);
            if (mc == null) throw new IOException("Invalid foreign key column name found!");
            int iKeySeq = rs.getInt("KEY_SEQ");
            int iUpdateRule = rs.getInt("UPDATE_RULE");
            int iDeleteRule = rs.getInt("DELETE_RULE");
            String sFkName = rs.getString("FK_NAME");
            MetaForeignKey mfk = mt.getMetaForeignKey(sFkName);
            if (mfk == null) {

                addReferences(mt, sForeignKeyName, mapFkColumns, mapPkColumns);

                sForeignKeyName = sFkName;
                mfk = mt.createMetaForeignKey(sForeignKeyName);
            }
            mapPkColumns.put(sFkColumnName, sPkColumnName);
            mapFkColumns.put(Integer.valueOf(iKeySeq), sFkColumnName);
            mfk.setReferencedSchema(sPkTableSchema);
            mfk.setReferencedTable(sPkTableName);
            mfk.setDeleteAction(getReferentialAction(iDeleteRule));
            mfk.setUpdateAction(getReferentialAction(iUpdateRule));

            LOG.debug("Metadata for foreign key '{}' (table '{}.{}') loaded", sForeignKeyName, sPkTableSchema, sPkTableName);
        }


        rs.close();

        addReferences(mt, sForeignKeyName, mapFkColumns, mapPkColumns);
    }


    private void getPrimaryKey(MetaTable mt) throws IOException, SQLException {
        String sPkName = "PK_" + mt.getName();
        Map<Integer, String> mapPkColumns = new HashMap<>();
        ResultSet rs = this._dmd.getPrimaryKeys(null, mt.getParentMetaSchema().getName(), mt.getName());
        while (rs.next()) {
            String sTableSchema = rs.getString("TABLE_SCHEM");
            if (sTableSchema != null && !schemaNamesMatch(mt.getParentMetaSchema().getName(),
                    sTableSchema, rs.getString("TABLE_NAME")))
                throw new IOException("Invalid table schema for primary key found!");
            String sTableName = rs.getString("TABLE_NAME");
            if (!tableNamesMatch(sTableName, mt.getName())) throw new IOException("Invalid table name for primary key found!");
            String sColumnName = rs.getString("COLUMN_NAME");
            int iKeySeq = rs.getInt("KEY_SEQ");
            mapPkColumns.put(Integer.valueOf(iKeySeq), sColumnName);
            String s = rs.getString("PK_NAME");
            if (s != null) {
                sPkName = s;
            } else {
                LOG.info("No name for primary key of column '{}.{}.{}' available. Used '{}' instead.", sTableSchema, sTableName, sColumnName, sPkName);
            }


            LOG.debug("Metadata for primary key '{}' (column '{}.{}.{}') loaded", sPkName, sTableSchema, sTableName, sColumnName);
        }


        rs.close();
        if (mapPkColumns.size() > 0) {
            MetaUniqueKey muk = mt.createMetaPrimaryKey(sPkName);
            for (int iColumn = 0; iColumn < mapPkColumns.size(); iColumn++) {
                int idx = iColumn + 1;
                String sColumnName = mapPkColumns.get(idx);
                muk.addColumn(sColumnName);
            }
        }
    }


    private void getColumns(MetaTable mt) throws IOException, SQLException {
        String schemaName = mt.getParentMetaSchema().getName();
        String tableName = mt.getName();

        ResultSet rs = this._dmd.getColumns(null,
                getPatternedName(schemaName),
                getPatternedName(tableName),
                "%");

        while (rs.next()) {
            String sTableSchema = rs.getString("TABLE_SCHEM");
            String sTableName = rs.getString("TABLE_NAME");
            if (sTableSchema != null
                    && !schemaNamesMatch(mt.getParentMetaSchema().getName(), sTableSchema, sTableName))
                throw new IOException("Invalid table schema for column found!");
            if (!tableNamesMatch(sTableName, mt.getName())) throw new IOException("Invalid table name for column found!");
            String sColumnName = rs.getString("COLUMN_NAME");
            MetaColumn mc = mt.createMetaColumn(sColumnName);
            getColumnData(rs, mc);

            LOG.debug("Metadata for column '{}.{}.{}' loaded", sTableSchema, sTableName, sColumnName);
        }
        if (mt.getMetaColumns() == 0) throw new SQLException("Table " + mt.getName() + " has no columns!");
        rs.close();
    }


    private void getGlobalMetaData() throws IOException, SQLException {
        getPrivileges();

        getUsers();

        getRoles();
    }


    private void getSchemaMetaData() throws IOException, SQLException {
        for (int iSchema = 0; iSchema < this._md.getMetaSchemas(); iSchema++) {
            MetaSchema ms = this._md.getMetaSchema(iSchema);
            if (ms.getMetaTables() > 0) {
                if (!this._bViewsAsTables && !this._bPreviewLight) getViews(ms);
                if (!hasSelectedTableFilter() && !this._bPreviewLight) {
                    // 루틴은 자료가 아니다. Oracle 10.2.0.1 의 ALL_PROCEDURES 에는 원본 래퍼가 쓰는 OBJECT_TYPE 컬럼이
                    // 없어 목록 조회 자체가 ORA-00904 로 실패한다. 루틴을 읽지 못하면 알리고 루틴 없이 계속한다.
                    try {
                        getRoutines(ms);
                    } catch (SQLException se) {
                        LOG.warn("스키마 {} 의 루틴을 읽지 못해 루틴 없이 계속함: {}", ms.getName(), se.getMessage());
                        System.out.println("  건너뜀: 스키마 " + ms.getName() + " 의 루틴 목록을 읽지 못함 (" + se.getMessage() + ")");
                    }
                }
            }
        }
    }

    private Map<String, List<String>> expandSelectedTableClosure(Map<String, List<String>> selectedSchemaTableMap)
            throws SQLException {
        Map<String, LinkedHashSet<String>> expanded = toMutableSelectedMap(selectedSchemaTableMap);
        boolean changed;
        do {
            changed = false;
            List<Map.Entry<String, String>> snapshot = selectedTableSnapshot(expanded);
            for (Map.Entry<String, String> selected : snapshot) {
                Map.Entry<String, String> stored = resolveStoredQualified(selected.getKey(), selected.getValue());
                try (ResultSet rs = this._dmd.getImportedKeys(null, stored.getKey(), stored.getValue())) {
                    while (rs.next()) {
                        String pkSchema = rs.getString("PKTABLE_SCHEM");
                        String pkTable = rs.getString("PKTABLE_NAME");
                        if (pkTable != null) {
                            int dot = pkTable.lastIndexOf('.');
                            if (dot >= 0) {
                                pkSchema = pkTable.substring(0, dot);
                                pkTable = pkTable.substring(dot + 1);
                            }
                        }
                        if (pkSchema == null || pkSchema.isBlank()) pkSchema = selected.getKey();
                        changed |= addSelectedTable(expanded, pkSchema, pkTable);
                    }
                } catch (SQLFeatureNotSupportedException e) {
                    LOG.warn("Imported-key metadata is not supported. Selected table dependency closure may be incomplete.", e);
                    return toListMap(expanded);
                }
            }
        } while (changed);
        return toListMap(expanded);
    }

    private Map.Entry<String, String> resolveStoredQualified(String schema, String table) {
        String[] schemaCandidates = schema == null
                ? new String[]{null}
                : new String[]{schema, schema.toUpperCase(), schema.toLowerCase()};
        String[] tableCandidates = table == null
                ? new String[]{null}
                : new String[]{table, table.toUpperCase(), table.toLowerCase()};
        OracleTableList otl = null;
        try {
            otl = ownTableList();
        } catch (SQLException ignore) {
        }
        for (String sc : schemaCandidates) {
            for (String tc : tableCandidates) {
                if (otl != null) {
                    try {
                        String[] asFound = otl.resolve(sc, tc);
                        if (asFound != null) return new AbstractMap.SimpleImmutableEntry<>(asFound[0], asFound[1]);
                    } catch (SQLException ignore) {
                    }
                    continue;
                }
                try (ResultSet rs = this._dmd.getTables(null, sc, tc, null)) {
                    if (rs.next()) {
                        String s = rs.getString("TABLE_SCHEM");
                        String t = rs.getString("TABLE_NAME");
                        return new AbstractMap.SimpleImmutableEntry<>(
                                s != null && !s.isBlank() ? s : sc,
                                t != null && !t.isBlank() ? t : tc);
                    }
                } catch (SQLException ignore) {
                }
            }
        }
        return new AbstractMap.SimpleImmutableEntry<>(schema, table);
    }

    private static Map<String, LinkedHashSet<String>> toMutableSelectedMap(Map<String, List<String>> selectedSchemaTableMap) {
        Map<String, LinkedHashSet<String>> expanded = new LinkedHashMap<>();
        if (selectedSchemaTableMap == null) return expanded;
        selectedSchemaTableMap.forEach((schema, tables) -> {
            if (schema == null || tables == null) return;
            for (String table : tables) {
                addSelectedTable(expanded, schema, table);
            }
        });
        return expanded;
    }

    private static List<Map.Entry<String, String>> selectedTableSnapshot(Map<String, LinkedHashSet<String>> selected) {
        List<Map.Entry<String, String>> snapshot = new ArrayList<>();
        selected.forEach((schema, tables) ->
                tables.forEach(table -> snapshot.add(new AbstractMap.SimpleImmutableEntry<>(schema, table))));
        return snapshot;
    }

    private void verifySelectedTablesArchived(Map<String, List<String>> requested) throws IOException {
        if (requested == null || requested.isEmpty()) return;
        List<String> missing = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : requested.entrySet()) {
            if (entry.getValue() == null) continue;
            for (String requestedTable : entry.getValue()) {
                if (requestedTable == null || requestedTable.isBlank()) continue;
                if (!isArchived(entry.getKey(), requestedTable)) {
                    missing.add(entry.getKey() + "." + requestedTable);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IOException(CmdI18n.get("cmd.metaFromDb.selectedTablesNotFound",
                    String.join(", ", missing)));
        }
    }

    private boolean isArchived(String schema, String table) {
        Archive archive = this._md.getArchive();
        for (int i = 0; i < archive.getSchemas(); i++) {
            Schema s = archive.getSchema(i);
            String archivedSchema = s.getMetaSchema().getName();
            for (int j = 0; j < s.getTables(); j++) {
                String archivedTable = s.getTable(j).getMetaTable().getName();
                if (!schemaNamesMatch(schema, archivedSchema, archivedTable)) continue;
                if (tableNamesMatch(table, archivedTable)) return true;
            }
        }
        return false;
    }

    private static boolean addSelectedTable(Map<String, LinkedHashSet<String>> selected, String schema, String table) {
        if (schema == null || schema.isBlank() || table == null || table.isBlank()) return false;
        return selected.computeIfAbsent(schema, k -> new LinkedHashSet<>()).add(table);
    }

    private static Map<String, List<String>> toListMap(Map<String, LinkedHashSet<String>> selected) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        selected.forEach((schema, tables) -> result.put(schema, new ArrayList<>(tables)));
        return result;
    }

    private static Map<String, Set<String>> toSetMap(Map<String, List<String>> selected) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        selected.forEach((schema, tables) -> result.put(schema, new LinkedHashSet<>(tables)));
        return result;
    }

    private boolean hasSelectedTableFilter() {
        return this._selectedTables != null && !this._selectedTables.isEmpty();
    }

    private boolean isSelectedTable(String schema, String table) {
        return containsSelectedTable(this._selectedTables, schema, table);
    }

    private boolean containsSelectedTable(Map<String, ? extends Collection<String>> selected,
                                          String schema, String table) {
        if (selected == null || selected.isEmpty() || schema == null || table == null) return false;
        Collection<String> tables = selected.get(schema);
        if (tables != null && tables.contains(table)) return true;
        for (Map.Entry<String, ? extends Collection<String>> entry : selected.entrySet()) {
            if (!schemaNamesMatch(entry.getKey(), schema, table)) continue;
            for (String candidate : entry.getValue()) {
                if (tableNamesMatch(candidate, table)) return true;
            }
        }
        return false;
    }

    private boolean schemaNamesMatch(String selectedSchema, String reportedSchema, String reportedTable) {
        if (selectedSchema == null) return false;
        if (selectedSchema.equalsIgnoreCase(reportedSchema)) return true;
        if (reportedTable != null) {
            int dot = reportedTable.lastIndexOf('.');
            if (dot >= 0 && selectedSchema.equalsIgnoreCase(reportedTable.substring(0, dot))) return true;
        }
        return reportedSchema != null && reportedSchema.equalsIgnoreCase(connectionSchemaSafe());
    }

    private String connectionSchemaSafe() {
        if (this._sConnectionSchema == null) {
            try {
                this._sConnectionSchema = this._dmd.getConnection().getSchema();
            } catch (Throwable ignore) {
                this._sConnectionSchema = "";
            }
        }
        return this._sConnectionSchema;
    }

    private String _sConnectionSchema = null;

    private static boolean tableNamesMatch(String selectedName, String reportedName) {
        if (selectedName == null || reportedName == null) return false;
        if (selectedName.equalsIgnoreCase(reportedName)) return true;
        return bareTableName(selectedName).equalsIgnoreCase(bareTableName(reportedName));
    }

    private static String bareTableName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }


    private String extractComment(String remarks) throws SQLException {
        if (remarks == null) {
            return null;
        }
        String databaseProductName = this._dmd.getConnection().getMetaData().getDatabaseProductName().toLowerCase();
        // CUBRID인 경우에만 COMMENT 추출
        if ("cubrid".equalsIgnoreCase(databaseProductName)) {
            Pattern pattern = Pattern.compile("COMMENT='(.*?)'");
            Matcher matcher = pattern.matcher(remarks);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null; // COMMENT가 없으면 null 반환
        }
        // CUBRID가 아닌 경우 원본 remarks 반환
        return remarks;
    }


    private void getTables(Map<String, List<String>> selectedSchemaTableMap) throws IOException, SQLException {
        String[] asTypes = {"TABLE"};
        if (this._bViewsAsTables) asTypes = new String[]{"TABLE", "VIEW"};
        List<String[]> listTableRows = listTableRows(asTypes);
        this._iTables = listTableRows.size();
        this._iTablesPercent = (this._iTables + 99) / 100;
        this._iTablesAnalyzed = 0;

        boolean hasSelected = !selectedSchemaTableMap.entrySet().isEmpty();

        for (String[] asRow : listTableRows) {
            if (cancelRequested()) break;
            String sTableSchema = asRow[0];
            String sTableName = asRow[1];
            String sTableType = asRow[2];
            if (isCubridSkippable(sTableName) || isMySqlSkippable(sTableSchema)) continue;
            if (!Arrays.asList(asTypes).contains(sTableType))
                throw new IOException("Invalid table type found!");
            String sRemarks = asRow[3];
            // 특정 엔티티를 선택한 경우 선택되지 않은 엔티티는 메타 정보 조회 무시 1
            if (hasSelected) {
                if (!containsSelectedTable(selectedSchemaTableMap, sTableSchema, sTableName)) continue;
            }

            Schema schema = this._md.getArchive().getSchema(sTableSchema);
            if (schema == null) schema = this._md.getArchive().createSchema(sTableSchema);
            Table table = schema.getTable(sTableName);
            if (table == null) table = schema.createTable(sTableName);
            MetaTable mt = table.getMetaTable();
            QualifiedId qiTable = new QualifiedId(null, sTableSchema, sTableName);
            System.out.println(CmdI18n.get("cmd.common.table", qiTable.format()));

          // if (sRemarks != null && sRemarks.length() > 0) mt.setDescription(sRemarks);
            if (sRemarks != null && !sRemarks.isEmpty()) {
                sRemarks = extractComment(sRemarks);
                mt.setDescription(sRemarks);
            }

            LOG.debug("Load metadata for table '{}.{}'", sTableSchema, sTableName);

            getTableSize(table, mt);
            getColumns(mt);
            getPrimaryKey(mt);
            getForeignKeys(mt);
            getUniqueKeys(mt);
            if (!this._bPreviewLight) collectRows(mt);
            incTablesAnalyzed();
        }
    }


    /*
     * Oracle 10.2.0.1 에서 원본 Oracle 래퍼의 getTables 가 같은 테이블을 수백~수천 번 되풀이해 돌려준다
     * (ALL_OBJECTS 와 ALL_TABLES 의 ANSI 외부 조인이 행을 부풀림. 순수 드라이버와 11g 이상에서는 한 번).
     * 같은 테이블을 다시 처리하면 컬럼이 겹쳐 추출 전체가 멈추므로, 이미 본 (스키마, 이름) 행은 건너뛴다.
     */
    private static String objectKey(String sSchema, String sName) {
        String s = String.valueOf(sSchema);
        return s.length() + ":" + s + "." + sName; // 길이를 앞에 붙여 이름에 어떤 글자가 있어도 겹치지 않는다
    }

    private Boolean _bOwnTableList = null;
    private OracleTableList _oracleTableList = null;

    /** Oracle 10 이면 테이블 · 뷰 목록을 래퍼 대신 직접 조회한다(OracleTableList 설명). 아니면 null. */
    private OracleTableList ownTableList() throws SQLException {
        if (_bOwnTableList == null) {
            _bOwnTableList = isOracleDb() && OracleTableList.isEnabled(this._dmd);
            if (_bOwnTableList) {
                _oracleTableList = new OracleTableList(this._dmd.getConnection().unwrap(Connection.class));
                LOG.info("Oracle {}.{}: 테이블 · 뷰 목록을 래퍼 대신 직접 조회함 ({}={})",
                        this._dmd.getDatabaseMajorVersion(), this._dmd.getDatabaseMinorVersion(),
                        OracleTableList.PROPERTY, System.getProperty(OracleTableList.PROPERTY, "auto"));
            }
        }
        return _oracleTableList;
    }

    /** 테이블 목록. 행은 {스키마, 이름, 종류, 설명}이고 같은 (스키마, 이름)은 한 번만 담는다. */
    private List<String[]> listTableRows(String[] asTypes) throws SQLException {
        OracleTableList otl = ownTableList();
        if (otl != null) {
            // 래퍼도 Oracle 에서는 TABLE · VIEW 만 돌려준다(구체화 뷰 종류는 없다).
            List<String> listTypes = Arrays.asList(asTypes);
            return otl.tables(listTypes.contains("TABLE"), listTypes.contains("VIEW"));
        }
        List<String[]> listRows = new ArrayList<>();
        Set<String> setSeen = new HashSet<>();
        try (ResultSet rs = this._dmd.getTables(null, "%", "%", asTypes)) {
            while (rs.next()) {
                String sSchema = rs.getString("TABLE_SCHEM");
                String sName = rs.getString("TABLE_NAME");
                if (setSeen.add(objectKey(sSchema, sName)))
                    listRows.add(new String[]{sSchema, sName, rs.getString("TABLE_TYPE"), rs.getString("REMARKS")});
            }
        }
        return listRows;
    }

    /** 한 스키마의 뷰 목록. 행은 {스키마, 이름, 종류, 설명, 정의문}이고 같은 뷰는 한 번만 담는다. */
    private List<String[]> listViewRows(String sSchema) throws SQLException {
        OracleTableList otl = ownTableList();
        if (otl != null) return otl.views(sSchema);
        List<String[]> listRows = new ArrayList<>();
        Set<String> setSeen = new HashSet<>();
        try (ResultSet rs = this._dmd.getTables(null, getPatternedName(sSchema), "%", new String[]{"VIEW"})) {
            while (rs.next()) {
                String sViewSchema = rs.getString("TABLE_SCHEM");
                String sName = rs.getString("TABLE_NAME");
                if (!setSeen.add(objectKey(sViewSchema, sName))) continue;
                String sQueryText = null;
                try {
                    sQueryText = rs.getString("QUERY_TEXT");
                } catch (SQLException ignore) {
                }
                listRows.add(new String[]{sViewSchema, sName, rs.getString("TABLE_TYPE"), rs.getString("REMARKS"), sQueryText});
            }
        }
        return listRows;
    }

    private boolean supportsMaterializedViewType() {
        try (ResultSet rsTypes = this._dmd.getTableTypes()) {
            while (rsTypes.next()) {
                String sType = rsTypes.getString(1);
                if (sType != null && sType.trim().equalsIgnoreCase("MATERIALIZED VIEW")) return true;
            }
        } catch (SQLException se) {
            LOG.debug("getTableTypes() 조회 실패, 구체화 뷰 유형을 사용하지 않는다: {}", se.getMessage());
        }
        return false;
    }

    private void getTables() throws IOException, SQLException {//  databaseProductName = this._dmd.getConnection().getMetaData().getDatabaseProductName().toLowerCase();
        java.util.List<String> listTypes = new java.util.ArrayList<String>();
        listTypes.add("TABLE");
        if (this._bViewsAsTables) listTypes.add("VIEW");
        if (supportsMaterializedViewType()) listTypes.add("MATERIALIZED VIEW");
        String[] asTypes = listTypes.toArray(new String[0]);
        LOG.debug("getTables 유형: {}", listTypes);
        List<String[]> listTableRows = listTableRows(asTypes);
        this._iTables = listTableRows.size();
        this._iTablesPercent = (this._iTables + 99) / 100;
        this._iTablesAnalyzed = 0;
        for (String[] asRow : listTableRows) {
            if (cancelRequested()) break;
            String sTableSchema = asRow[0];
            String sTableName = asRow[1];
            String sTableType = asRow[2];

//            sTableName = trimTableNameIfCubrid(sTableName);
            if (isCubridSkippable(sTableName) || isMySqlSkippable(sTableSchema)) continue; // 티베로, 큐브리드 경우 시스템 테이블도 조회되므로 무시

            checkIdentifierIsUsable(sTableSchema, sTableName);

            if (!Arrays.asList(asTypes).contains(sTableType))
                throw new IOException("Invalid table type found!");
            String sRemarks = asRow[3];
            Schema schema = this._md.getArchive().getSchema(sTableSchema);
            if (schema == null) schema = this._md.getArchive().createSchema(sTableSchema);
            Table table = schema.getTable(sTableName);
            if (table == null) table = schema.createTable(sTableName);
            MetaTable mt = table.getMetaTable();
            QualifiedId qiTable = new QualifiedId(null, sTableSchema, sTableName);
            System.out.println(CmdI18n.get("cmd.common.table", qiTable.format()));
            // if (sRemarks != null && sRemarks.length() > 0) mt.setDescription(sRemarks);
            if (sRemarks != null && !sRemarks.isEmpty()) {
                sRemarks = extractComment(sRemarks);
                mt.setDescription(sRemarks);
            }

            LOG.debug("Load metadata for table '{}.{}'", sTableSchema, sTableName);

            // 미리보기는 고를 테이블 목록만 보여 준다. 컬럼·키·크기는 최종 추출이 고른 테이블만 다시 읽는다.
            if (!this._bPreviewLight) {
                // 테이블 사이즈 세팅
                getTableSize(table, mt);
                getColumns(mt);
                getPrimaryKey(mt);
                getForeignKeys(mt);
                getUniqueKeys(mt);
                collectRows(mt);
            }
            incTablesAnalyzed();
        }
    }

    /**
     * 이름을 그대로 쓸 수 있는지 본다. 쓸 수 없으면 사유와 조치를 담아 세운다.
     *
     * 데이터베이스가 한글을 담을 수 없는 문자집합(예: 로케일 en_US 의 iso88591)으로
     * 만들어졌는데 한글 식별자를 쓰면, 서버는 UTF-8 바이트를 그 문자집합으로 해석해
     * 돌려준다. 그 결과 이름에 제어문자가 섞이고, SIARD 산출물의 요소·폴더 이름으로
     * 쓸 수 없다. 여기서 세우지 않으면 이름이 조용히 바뀐 채 뒤쪽에서 조회가 어긋나
     * 원인을 알 수 없는 자리에서 끝난다.
     *
     * 데이터베이스를 만들 때 정해지는 성질이라 추출 도구가 고칠 수 있는 것이 아니다.
     * 그래서 고치는 대신, 무엇이 왜 안 되는지와 어떻게 해야 하는지를 알린다.
     */
    private void checkIdentifierIsUsable(String sSchemaName, String sTableName) throws SQLException {
        String sBroken = firstBrokenIdentifier(sSchemaName, sTableName);
        if (sBroken != null)
            throw new SQLException(CmdI18n.get("cmd.common.identifierNotRepresentable",
                    describeIdentifier(sBroken)));
    }

    private String firstBrokenIdentifier(String... asNames) {
        for (String sName : asNames) {
            if (hasUnusableCharacter(sName))
                return sName;
        }
        return null;
    }

    /** 제어문자(C0·C1)가 섞였으면 이름이 온전하지 않다. 탭·개행은 식별자에 오지 않는다. */
    private boolean hasUnusableCharacter(String sName) {
        if (sName == null)
            return false;
        for (int i = 0; i < sName.length(); i++) {
            char c = sName.charAt(i);
            if (c < 0x20 || (c >= 0x7F && c <= 0x9F))
                return true;
        }
        return false;
    }

    /** 깨진 이름은 그대로 찍으면 읽을 수 없으므로 제어문자를 부호점으로 바꿔 보인다. */
    private String describeIdentifier(String sName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sName.length(); i++) {
            char c = sName.charAt(i);
            if (c < 0x20 || (c >= 0x7F && c <= 0x9F))
                sb.append(String.format("\\u%04X", (int) c));
            else
                sb.append(c);
        }
        return sb.toString();
    }

    private void logDownload() throws IOException, SQLException {
        if (this._md.getDataOwner() == null) this._md.setDataOwner("(...)");
        if (this._md.getDataOriginTimespan() == null) this._md.setDataOriginTimespan("(...)");
        if (this._md.getDbName() == null) this._md.setDbName("(...)");
        ProgramInfo pi = ProgramInfo.getProgramInfo();
        this._md.setProducerApplication(pi.getProgram() + " " + pi.getVersion() + " " + pi.getCopyright());

        try {
            InetAddress ia = InetAddress.getLocalHost();
            this._md.setClientMachine(ia.getCanonicalHostName());
        } catch (UnknownHostException uhe) {
            LOG.error("Can not determine host", uhe);
        }

        this._md.setDatabaseProduct(this._dmd.getDatabaseProductName() + " " + this._dmd.getDatabaseProductVersion());

        this._md.setConnection(this._dmd.getURL());

        this._md.setDatabaseUser(this._dmd.getUserName());
    }

    public void download(boolean bViewsAsTables, boolean bMaxLobNeeded, Progress progress) throws IOException, SQLException {
        downloadInternal(bViewsAsTables, bMaxLobNeeded, progress, null);
    }

    /**
     * 선택한 엔티티의 메타 정보 추출
     *
     * @param bViewsAsTables
     * @param bMaxLobNeeded
     * @param progress
     * @param selectedSchemaTableMap
     * @throws IOException
     * @throws SQLException
     */
    public void download(boolean bViewsAsTables,
                         boolean bMaxLobNeeded,
                         Progress progress,
                         Map<String, List<String>> selectedSchemaTableMap) throws IOException, SQLException {
        downloadInternal(bViewsAsTables, bMaxLobNeeded, progress, selectedSchemaTableMap);
    }

    private void downloadInternal(boolean bViewsAsTables,
                                  boolean bMaxLobNeeded,
                                  Progress progress,
                                  Map<String, List<String>> selectedSchemaTableMap) throws IOException, SQLException {

        LOG.info("Start meta data download to archive {} (view-as-tables: {}, max-lob-needed: {})",
                this._md.getArchive().getFile().getAbsoluteFile(),
                Boolean.valueOf(bViewsAsTables),
                Boolean.valueOf(bMaxLobNeeded));

        System.out.println(CmdI18n.get("cmd.common.metaData"));

        this._progress = progress;
        this._bViewsAsTables = bViewsAsTables;
        this._bMaxLobNeeded = bMaxLobNeeded;

        logDownload();

        if (selectedSchemaTableMap != null && !selectedSchemaTableMap.isEmpty()) {
            Map<String, List<String>> expandedSelectedSchemaTableMap =
                    expandSelectedTableClosure(selectedSchemaTableMap);
            this._selectedTables = toSetMap(expandedSelectedSchemaTableMap);
            this._md.getArchive().setSelectedSchemaTableMap(expandedSelectedSchemaTableMap);
            getTables(expandedSelectedSchemaTableMap);
            verifySelectedTablesArchived(selectedSchemaTableMap);
        } else {
            this._selectedTables = Collections.emptyMap();
            getTables();
        }

        if (!cancelRequested()) getSchemaMetaData();
        if (!cancelRequested()) getGlobalMetaData();

        if (cancelRequested()) throw new IOException(CmdI18n.get("cmd.cancel.metaDataDownload"));

        LOG.info("Meta data download finished");

    }

    /**
     * 데이터베이스 테이블 별 사이즈를 구하여 세팅
     *
     * @param table
     * @param metaTable
     * @throws SQLException
     */
    private void getTableSize(Table table, MetaTable metaTable) {
        String dbName = null;
        try {
            String databaseProductName = this._dmd.getConnection().getMetaData().getDatabaseProductName().toLowerCase();
            dbName = databaseProductName;
            DataBase dataBase = DataBase.findByName(databaseProductName);

            String tableName = metaTable.getName();
            String schemaName = metaTable.getParentMetaSchema().getName();

            if (dataBase.equals(DataBase.CUBRID)) {
                LOG.warn("Table size is not computed for CUBRID: {}.{}"
                                + " (no catalog view exposes storage size; ';info stats' is a csql command)",
                        metaTable.getParentMetaSchema().getName(), metaTable.getName());
                return;
            }

            String query = switch (dataBase) {
                case MYSQL -> "SELECT (data_length + index_length) AS size " +
                        "FROM information_schema.tables " +
                        "WHERE table_schema = ? " +
                        "AND table_name = ?";
                case ORACLE -> getOracleQuery();
                case POSTGRESQL -> "SELECT pg_total_relation_size(?) AS size";
                case MSSQL -> "SELECT SUM(total_pages) * 8 * 1024 AS table_size " +
                        "FROM sys.allocation_units " +
                        "WHERE container_id IN (" +
                        "SELECT p.partition_id FROM sys.partitions p, sys.tables t, sys.schemas s " +
                        "WHERE p.object_id = t.object_id AND t.schema_id = s.schema_id " +
                        "AND s.name = ? AND t.name = ?)";
                case CUBRID -> ";info stats " + tableName;
                // ADMINTABINFO 는 KB 단위로 보고한다.
                case DB2 -> "SELECT (COALESCE(DATA_OBJECT_P_SIZE, 0) + COALESCE(INDEX_OBJECT_P_SIZE, 0) " +
                        "+ COALESCE(LONG_OBJECT_P_SIZE, 0) + COALESCE(LOB_OBJECT_P_SIZE, 0)) * 1024 AS size " +
                        "FROM SYSIBMADM.ADMINTABINFO WHERE TABSCHEMA = ? AND TABNAME = ?";
                case TIBERO -> getTiberoQuery();
            };

            Connection sizeConnection = _dmd.getConnection();
            try {
                if (sizeConnection.isWrapperFor(Connection.class)) {
                    Connection nativeConnection = sizeConnection.unwrap(Connection.class);
                    if (nativeConnection != null) sizeConnection = nativeConnection;
                }
            } catch (SQLException ignore) { /* 래퍼가 아니면 그대로 사용 */ }

            long size = 0;
            try (PreparedStatement stmt = sizeConnection.prepareStatement(query)) {
                switch (dataBase) {
                    case MYSQL -> {
                        stmt.setString(1, schemaName);
                        stmt.setString(2, tableName);
                    }
                    // DB2 카탈로그는 식별자를 대문자로 보관한다.
                    case DB2 -> {
                        stmt.setString(1, schemaName.toUpperCase());
                        stmt.setString(2, tableName.toUpperCase());
                    }
                    case ORACLE -> {
                        stmt.setString(1, tableName);
                        stmt.setString(2, tableName);
                    }
                    case POSTGRESQL -> stmt.setString(1, schemaName + "." + tableName);
                    case MSSQL -> {
                        stmt.setString(1, schemaName);
                        stmt.setString(2, tableName);
                    }
                    // TODO :: more db
                    case TIBERO -> {
                        stmt.setString(1, tableName);
                        stmt.setString(2, tableName);
                    }
                }

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    size = rs.getLong(1);  // Assuming the size is in the first column
                }
            }

            Schema parentSchema = table.getParentSchema();
            parentSchema.addTableSize(size);

            table.setFormattedTableSize(ByteFormatter.convertToBestFitUnit(size));
            table.setTableSize(size);
        } catch (Exception e) {
            if (e instanceof SQLException) {
                SQLException sqlException = (SQLException) e;
                doHandleSqlException(dbName, "getTableSize", sqlException);
            } else {
                LOG.warn("getTableSize failed (db={}, table={}): {}", dbName, metaTable.getName(), e.toString());
            }
        }

    }


//    private String getCubridCommentQuery() {
//        String databaseUser = this._md.getArchive().getMetaData().getDatabaseUser();
//
//    }

    private String getTiberoQuery() {
        String databaseUser = this._md.getArchive().getMetaData().getDatabaseUser();
        if (isSysdba(databaseUser)) {
            return "SELECT bytes AS table_size " +
                    "FROM dba_segments " +
                    "WHERE (segment_name = UPPER(?) OR segment_name = LOWER(?)) " +
                    "AND segment_type = 'TABLE'";
        } else {
            return "SELECT bytes AS table_size " +
                    "FROM USER_segments " +
                    "WHERE (segment_name = UPPER(?) OR segment_name = LOWER(?)) " +
                    "AND segment_type = 'TABLE'";
        }
    }

    private String getOracleQuery() {
        String databaseUser = this._md.getArchive().getMetaData().getDatabaseUser();
        if (isSysdba(databaseUser)) {
            return "SELECT bytes AS table_size " +
                    "FROM dba_segments " +
                    "WHERE (segment_name = UPPER(?) OR segment_name = LOWER(?)) " +
                    "AND segment_type = 'TABLE'";
        } else {
            return "SELECT bytes AS table_size " +
                    "FROM USER_segments " +
                    "WHERE (segment_name = UPPER(?) OR segment_name = LOWER(?)) " +
                    "AND segment_type = 'TABLE'";
        }
    }

    private boolean isSysdba(String databaseUser) {
//        return true;
        Optional.ofNullable(databaseUser).orElseThrow(() -> new IllegalArgumentException("database user is not specified!!"));
        return !databaseUser.startsWith("C##") && databaseUser.startsWith("SYS");
    }

}
