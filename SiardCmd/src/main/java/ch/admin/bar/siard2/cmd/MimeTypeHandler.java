// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.api.Cell;
import ch.admin.bar.siard2.api.Field;
import ch.admin.bar.siard2.api.MetaValue;
import ch.admin.bar.siard2.api.Value;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.tika.Tika;


class MimeTypeHandler
{
  private final Map<String, Set<String>> mimeTypes;
  private final Tika tika;
  private final MimeDetectionCache detectionCache;
  final TransferTimings timings;
  private char[] asciiBuffer;
  private boolean asciiBufferBorrowed;
  
  public MimeTypeHandler(Tika tika) {
    this(tika, TransferTimings.DISABLED);
  }

  MimeTypeHandler(Tika tika, TransferTimings timings) {
    this.mimeTypes = new HashMap<>();
    this.tika = tika;
    this.detectionCache = new MimeDetectionCache(tika);
    this.timings = timings;
  }

  char[] borrowAsciiBuffer() {
    if (asciiBufferBorrowed) return new char[8192];
    if (asciiBuffer == null) asciiBuffer = new char[8192];
    asciiBufferBorrowed = true;
    return asciiBuffer;
  }

  void releaseAsciiBuffer(char[] buffer) {
    if (buffer == asciiBuffer) asciiBufferBorrowed = false;
  }

  /** Once multiple types were seen, this column's final MIME remains empty. */
  boolean needsDetection(Value value) {
    String name;
    if (value instanceof Cell) name = ((Cell)value).getMetaColumn().getName();
    else if (value instanceof Field) name = ((Field)value).getMetaField().getName();
    else return false;
    Set<String> types = mimeTypes.get(name);
    return types == null || types.size() < 2;
  }
  
  public void add(Value value, Blob blob) throws SQLException, IOException {
    if (value instanceof Cell) add((Cell)value, blob); 
    if (value instanceof Field) add((Field)value, blob); 
  }
  
  public void add(Value value, byte[] bytes) throws IOException {
    if (value instanceof Cell) add((Cell)value, bytes); 
    if (value instanceof Field) add((Field)value, bytes); 
  }
  
  public void add(Value value, Clob clob) throws SQLException, IOException {
    if (value instanceof Cell) add((Cell)value, clob); 
    if (value instanceof Field) add((Field)value, clob);
  
  }

  /** Detect from already-read data; the caller owns the stream. */
  public void add(Value value, InputStream input) throws IOException {
    String mimeType = detect(input);
    if (value instanceof Cell) add((Cell)value, mimeType);
    if (value instanceof Field) add((Field)value, mimeType);
  }







  
  public void applyMimeType(Value value) throws IOException {
    if (value instanceof Cell) applyMimeType((Cell)value); 
    if (value instanceof Field) applyMimeType((Field)value); 
  }
  
  private void add(Cell cell, byte[] bytes) {
    add(cell, detect(bytes));
  }
  
  private void add(Field field, byte[] bytes) {
    add(field, detect(bytes));
  }
  
  private void add(Cell cell, Clob clob) throws SQLException, IOException {
    add(cell, detect(clob));
  }

  private void add(Field field, Clob clob) throws SQLException, IOException {
    add(field, detect(clob));
  }

  /**
   * 문자 대용량 값의 MIME 형식. ASCII 스트림을 주지 못하는 드라이버는 문자 자료이므로 text/plain 으로 본다.
   * Oracle 래퍼(jdbcoracle 2.2.1)의 LONG 값은 ASCII 스트림을 지원하지 않아(SQLFeatureNotSupportedException)
   * LONG 컬럼이 있는 테이블의 추출이 통째로 멈췄다(2026-10-14 Oracle 11g XE 실측).
   */
  private String detect(Clob clob) throws SQLException, IOException {
    try (InputStream input = clob.getAsciiStream()) {
      return detect(timings.source(input), MimeDetectionCache.memoryLength(input));
    } catch (SQLFeatureNotSupportedException e) {
      return "text/plain";
    }
  }
  
  private void add(Cell cell, Blob blob) throws SQLException, IOException {
    try (InputStream input = timings.source(blob.getBinaryStream())) {
      add(cell, detect(input));
    }
  }
  
  private void add(Field field, Blob blob) throws SQLException, IOException {
    try (InputStream input = timings.source(blob.getBinaryStream())) {
      add(field, detect(input));
    }
  }

  private String detect(InputStream input) throws IOException {
    return detect(input, MimeDetectionCache.memoryLength(input));
  }

  private String detect(InputStream input, int memoryLength) throws IOException {
    long readsBefore = timings.sourceReadNanos, start = timings.now();
    try { return this.detectionCache.detect(input, memoryLength); }
    finally { timings.finishMime(start, readsBefore); }
  }

  private String detect(byte[] bytes) {
    long start = timings.now();
    try { return this.detectionCache.detect(bytes); }
    finally { if (timings.enabled) timings.mimeNanos += timings.elapsed(start); }
  }
  
  private void add(Cell cell, String mimeType) {
    add(mimeType, cell.getMetaColumn().getName());
  }
  
  private void add(Field field, String mimeType) {
    add(mimeType, field.getMetaField().getName());
  }
  
  private void add(String mimeType, String name) {
    if (!this.mimeTypes.containsKey(name)) {
      this.mimeTypes.put(name, new HashSet<>(Collections.singletonList(mimeType)));
    } else {
      this.mimeTypes.get(name).add(mimeType);
    } 
  }
  
  private void applyMimeType(Cell cell) throws IOException {
    applyMimeType(cell.getMetaColumn());
  }
  
  private void applyMimeType(Field field) throws IOException {
    applyMimeType(field.getMetaField());
  }
  
  private void applyMimeType(MetaValue metaValue) throws IOException {
    Set<String> types = this.mimeTypes.get(metaValue.getName());
    if (types == null)
      return;  if (types.size() == 1) metaValue.setMimeType(types.iterator().next());
    if (types.size() != 1) metaValue.setMimeType(""); 
  }
}
