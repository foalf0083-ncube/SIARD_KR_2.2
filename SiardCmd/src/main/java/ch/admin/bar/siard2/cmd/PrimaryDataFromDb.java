// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.cmd.utils.CmdI18n;
import ch.admin.bar.dbexception.DatabaseExceptionHandlerHelper;
import ch.admin.bar.siard2.api.Record;
import ch.admin.bar.siard2.api.*;
import ch.admin.bar.siard2.api.ext.SchemaTableKey;
import ch.admin.bar.siard2.api.ext.form.FormData;
import ch.admin.bar.siard2.api.ext.form.FormDataHelper;
import ch.admin.bar.siard2.api.generated.CategoryType;
import ch.admin.bar.siard2.api.primary.RecordRetainerImpl;
import ch.admin.bar.siard2.api.primary.TableImpl;
import ch.enterag.sqlparser.identifier.QualifiedId;
import ch.enterag.utils.StopWatch;
import ch.enterag.utils.background.Progress;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.Duration;
import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.*;
import java.util.Map;
import java.util.Set;

public class PrimaryDataFromDb extends PrimaryDataTransfer {
    private static final Logger LOG = LoggerFactory.getLogger(PrimaryDataFromDb.class);
    private Progress progress = null;
    private long recordsDownloaded = -1L;
    private long recordsTotal = -1L;
    private long recordsPercent = -1L;
    private TransferTimings timings = TransferTimings.DISABLED;
    private final Tika tika = new Tika();

    private PrimaryDataFromDb(Connection connection, Archive archive) throws SQLException {
        super(connection, archive, null, true, true, true);
    }

    public static PrimaryDataFromDb newInstance(Connection connection, Archive archive) throws SQLException {
        return new PrimaryDataFromDb(connection, archive);
    }

    public void download(Progress progress) throws IOException, SQLException {
        LOG.info("Start primary data download to archive {}", this._archive.getFile().getAbsoluteFile());
        System.out.println(CmdI18n.get("cmd.common.primaryData"));
        this.progress = progress;
        this.recordsTotal = 0L;

        countRecords();

        this.recordsPercent = (this.recordsTotal + 99L) / 100L;
        this.recordsDownloaded = 0L;

        processData(progress);

        if (this.cancelRequested()) {
            throw new IOException("\r\n" + CmdI18n.get("cmd.cancel.primaryDataDownload"));
        } else {
            System.out.println(CmdI18n.get("cmd.dataFromDb.downloadTerminatedSuccessfully"));
            this._conn.rollback();
            LOG.info("Primary data download finished");
        }
    }

    private void countRecords() {
        _archive.getSelectedSchemaMap()
                .forEach((s, schema) -> this.recordsTotal += schema.getRecordCount());
    }

    private void processData(Progress progress) {
//        Map<String, Schema> map = _archive.getSelectedSchemaMap().isEmpty() ? _archive.getSchemaMap() : _archive.getSelectedSchemaMap();
//        map.forEach((s, schema) -> this.tryGetSchema(schema));

        // 스키마 맵 가져오기
        Map<String, Schema> map = _archive.getSelectedSchemaMap().isEmpty()
                ? _archive.getSchemaMap()
                : _archive.getSelectedSchemaMap();

        long totalTasks = map.values().stream()
                .mapToLong(schema -> schema.getSelectedTables().size())
                .sum();

        long completedTasks = 0;

        for (Map.Entry<String, Schema> entry : map.entrySet()) {
            Schema schema = entry.getValue();

            for (Table table : schema.getSelectedTables()) {
                this.tryGetTable(table); // 테이블 처리
                completedTasks++;

                // 진행 상태 업데이트
                if (progress != null) {
                    int percentage = (int) ((double) completedTasks / totalTasks * 100);
                    progress.notifyProgress(percentage);
                }

                // 취소 요청 확인
                if (progress != null && progress.cancelRequested()) {
                    throw new RuntimeException(CmdI18n.get("cmd.cancel.download"));
                }
            }
        }
    }


    private void tryGetTable(Table table) {
        try {
            this.getTable(table);
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void incDownloaded() {
        ++this.recordsDownloaded;
        if (this.progress != null && this.recordsTotal > 0L && this.recordsDownloaded % this.recordsPercent == 0L) {
            int iPercent = (int) (100L * this.recordsDownloaded / this.recordsTotal);
            this.progress.notifyProgress(iPercent);
        }

    }

    private boolean cancelRequested() {
        if (this.progress != null && this.progress.cancelRequested()) {
            LOG.info("Cancel downloading of primary data because of request");
            return true;
        } else {
            return false;
        }
    }

    private void setValue(Value value, Object oValue, MimeTypeHandler mimeTypeHandler) throws IOException, SQLException {
        boolean copiedOracleNClob = oValue instanceof ObjectValueReader.OracleNClobValue;
        if (copiedOracleNClob) oValue = ((ObjectValueReader.OracleNClobValue)oValue).clob();
        if (oValue != null) {
            if (oValue instanceof String) {
                value.setString((String) oValue);
            } else if (oValue instanceof byte[]) {
                byte[] bytes = (byte[]) oValue;
                mimeTypeHandler.add(value, bytes);
                value.setBytes(bytes);
            } else if (oValue instanceof Boolean) {
                value.setBoolean((Boolean) oValue);
            } else if (oValue instanceof Short) {
                value.setShort((Short) oValue);
            } else if (oValue instanceof Integer) {
                value.setInt((Integer) oValue);
            } else if (oValue instanceof Long) {
                value.setLong((Long) oValue);
            } else if (oValue instanceof BigInteger) {
                value.setBigInteger((BigInteger) oValue);
            } else if (oValue instanceof BigDecimal) {
                value.setBigDecimal((BigDecimal) oValue);
            } else if (oValue instanceof Float) {
                value.setFloat((Float) oValue);
            } else if (oValue instanceof Double) {
                value.setDouble((Double) oValue);
            } else if (oValue instanceof Timestamp) {
                value.setTimestamp((Timestamp) oValue);
            } else if (oValue instanceof Time) {
                value.setTime((Time) oValue);
            } else if (oValue instanceof Date) {
                value.setDate((Date) oValue);
            } else if (oValue instanceof Duration) {
                value.setDuration((Duration) oValue);
            } else if (oValue instanceof Clob clob) {
                try {
                    ClobTransfer.copy(value, clob, mimeTypeHandler, copiedOracleNClob);
                } catch (IOException e) {
                    throw new IOException("Error handling clob data : ", e);
                } catch (SQLException e) {
                    throw new SQLException("Error accessing clob data : ", e);
                }catch (OutOfMemoryError e) {
                    DatabaseExceptionHandlerHelper.doHandleOutOfMemoryException(e); // 기존 로직 활용
                    throw e;
                }

            } else if (oValue instanceof SQLXML sqlxml) {
                value.setReader(sqlxml.getCharacterStream());
                sqlxml.free();
            } else if (oValue instanceof Blob blob) {
                try (BlobResource resource = new BlobResource(blob)) {
                    mimeTypeHandler.add(value, blob);
                    value.setInputStream(blob.getBinaryStream());
                } catch (IOException e) {
                    throw new IOException("Error handling blob data : ", e);
                } catch (SQLException e) {
                    throw new SQLException("Error accessing blob data : ", e);
                }catch (OutOfMemoryError e) {
                    DatabaseExceptionHandlerHelper.doHandleOutOfMemoryException(e); // 기존 로직 활용
                    throw e;
                }
            } else if (oValue instanceof URL url) {
                value.setInputStream(url.openStream(), url.getPath());
            } else {
                Object[] ao;
                int iAttribute;
                if (oValue instanceof Array array) {
                    ao = (Object[]) array.getArray();

                    for (iAttribute = 0; iAttribute < ao.length; ++iAttribute) {
                        Value valueElement = value.getElement(iAttribute);
                        this.setValue(valueElement, ao[iAttribute], mimeTypeHandler);
                        mimeTypeHandler.applyMimeType(valueElement);
                    }

                    array.free();
                } else {
                    if (!(oValue instanceof Struct struct)) {
                        throw new SQLException("Invalid value type " + oValue.getClass().getName() + " encountered!");
                    }

                    ao = struct.getAttributes();

                    for (iAttribute = 0; iAttribute < ao.length; ++iAttribute) {
                        this.setValue(value.getAttribute(iAttribute), ao[iAttribute], mimeTypeHandler);
                    }
                }
            }
        }

    }

    private static final class BlobResource implements AutoCloseable {
        private final Blob blob;
        private BlobResource(Blob blob) { this.blob = blob; }
        @Override public void close() throws SQLException { blob.free(); }
    }

    private void getRecord(ResultSet rs, Record record, MimeTypeHandler mimeTypeHandler,
                           ObjectValueReader[] readers, SchemaTableKey tableKey) throws IOException, SQLException {
        if (readers.length != record.getCells()) {
            throw new IOException("Invalid number of result columns found!");
        } else {
            for (int cellIndex = 0; cellIndex < record.getCells(); ++cellIndex) {
                Cell cell = this.getCell(record, cellIndex);
                Object oValue = this.getValue(rs, readers[cellIndex]);
                this.setValue(mimeTypeHandler, cell, oValue, tableKey);
            }

        }
    }

    private Cell getCell(Record record, int cellIndex) throws IOException {
        return record.getCell(cellIndex);
    }

    private Object getValue(ResultSet rs, ObjectValueReader reader) throws SQLException {
        long start = timings.now();
        try {
            Object oValue = reader.read();
            return rs.wasNull() ? null : oValue;
        } finally {
            timings.getterNanos += timings.elapsed(start);
        }
    }

    private void setValue(MimeTypeHandler mimeTypeHandler, Cell cell, Object oValue,
                          SchemaTableKey tableKey) throws IOException, SQLException {
        this.setValue(cell, oValue, mimeTypeHandler);
        mimeTypeHandler.applyMimeType(cell);

        Set<FormData> formDataSet = _archive.getFormDataSet();
        for (FormData formData : formDataSet) {
            FormDataHelper formDataHelper = FormDataHelper.builder()
                    .formData(formData)
                    .defaultTargetDirectory(_archive.getFile().getParent())
                    .schemaTableKey(tableKey)
                    .cell(cell)
                    .oValue(oValue)
                    .build();
            formDataHelper.send();
        }
    }

    private int getDataType(MetaColumn mc) throws IOException {
        int iDataType = mc.getPreType();
        if (mc.getCardinality() >= 0) {
            iDataType = 2003;
        }

        MetaType mt = mc.getMetaType();
        if (mt != null) {
            CategoryType cat = mt.getCategoryType();
            if (cat == CategoryType.DISTINCT) {
                iDataType = mt.getBasePreType();
            } else {
                iDataType = 2002;
            }
        }

        return iDataType;
    }

    private void getTable(Table table) throws IOException, SQLException {
        timings = TransferTimings.forTable();
        QualifiedId qiTable = new QualifiedId(null, table.getParentSchema().getMetaSchema().getName(), table.getMetaTable().getName());
        System.out.println(CmdI18n.get("cmd.common.table", qiTable.format()));
        long lRecord = 0L;
        boolean streamLobs = table instanceof TableImpl && hasFileValues(table);
        RecordRetainer rr = streamLobs ? ((TableImpl)table).createRecordsStreamingLobs() : table.createRecords();
        try {
            long queryStart = timings.now();
            ResultSet opened = this.openTable(table, null);
            timings.queryNanos += timings.elapsed(queryStart);
            try (Statement stmt = opened.getStatement(); ResultSet rs = opened) {
                int columns = table.getMetaTable().getMetaColumns();
                if (rs.getMetaData().getColumnCount() != columns)
                    throw new IOException("Invalid number of result columns found!");
                ObjectValueReader[] readers = new ObjectValueReader[columns];
                for (int column = 0; column < columns; column++)
                    readers[column] = new ObjectValueReader(rs,
                            getDataType(table.getMetaTable().getMetaColumn(column)), column + 1);
                SchemaTableKey tableKey = SchemaTableKey.of(qiTable.getSchema(), qiTable.getName());
                StopWatch sw = StopWatch.getInstance();
                sw.start();
                long lBytesStart = rr.getByteCount();
                MimeTypeHandler mimeTypeHandler = new MimeTypeHandler(this.tika, timings);

                while (true) {
                    long start = timings.now();
                    boolean next = rs.next();
                    timings.fetchNanos += timings.elapsed(start);
                    if (!next || this.cancelRequested()) break;
                    start = timings.now();
                    Record record = rr.create();
                    timings.createNanos += timings.elapsed(start);
                    start = timings.now();
                    this.getRecord(rs, record, mimeTypeHandler, readers, tableKey);
                    timings.rowNanos += timings.elapsed(start);
                    start = timings.now();
                    rr.put(record);
                    timings.xmlNanos += timings.elapsed(start);
                    lBytesStart = logRecordProgress(lRecord++, sw, rr, lBytesStart);
                    this.incDownloaded();
                }
                if (this.cancelRequested())
                    throw new IOException(CmdI18n.get("cmd.cancel.primaryDataDownload"));
                System.out.println(CmdI18n.get("cmd.common.recordKbS", lRecord, sw.formatRate(rr.getByteCount() - lBytesStart, sw.stop())));
                System.out.println(CmdI18n.get("cmd.common.totalRecordsBytesInMs", StopWatch.formatLong(lRecord), StopWatch.formatLong(rr.getByteCount()), sw.formatMs()));
            }
            long finishStart = timings.now();
            rr.close();
            timings.finishNanos += timings.elapsed(finishStart);
            if (timings.enabled) LOG.info(timings.summary(qiTable.format(), lRecord));
        } catch (IOException | SQLException | RuntimeException | Error failure) {
            if (rr instanceof RecordRetainerImpl) {
                try { ((RecordRetainerImpl)rr).abort(); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
        LOG.debug("All data of table '{}.{}' successfully downloaded", qiTable.getSchema(), qiTable.getName());
    }

    private boolean hasFileValues(Table table) throws IOException {
        for (int i = 0; i < table.getMetaTable().getMetaColumns(); i++) {
            MetaColumn column = table.getMetaTable().getMetaColumn(i);
            int type = getDataType(column);
            // Include complex values, whose fields may contain LOBs. Tables of
            // ordinary scalar values retain the direct-to-ZIP XML writer.
            if (type == Types.CLOB || type == Types.NCLOB || type == Types.BLOB
                    || type == Types.SQLXML || type == Types.DATALINK
                    || type == Types.ARRAY || type == Types.STRUCT) return true;
            if ((type == Types.CHAR || type == Types.VARCHAR || type == Types.NCHAR
                    || type == Types.NVARCHAR || type == Types.BINARY || type == Types.VARBINARY)
                    && column.getMaxLength() > _archive.getMaxInlineSize()) return true;
        }
        return false;
    }

    private static long logRecordProgress(long lRecord, StopWatch sw, RecordRetainer rr, long lBytesStart) {
        if (lRecord % 1000L == 0L) {
            System.out.println(CmdI18n.get("cmd.common.recordKbS", lRecord, sw.formatRate(rr.getByteCount() - lBytesStart, sw.stop())));
            lBytesStart = rr.getByteCount();
            sw.start();
        }

        return lBytesStart;
    }

}
