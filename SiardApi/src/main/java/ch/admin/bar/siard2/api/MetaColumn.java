// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface MetaColumn extends MetaValue {
  MetaTable getParentMetaTable();
  
  MetaView getParentMetaView();
  
  boolean isValid();
  
  void setType(String paramString) throws IOException;
  
  void setPreType(int paramInt1, long paramLong, int paramInt2) throws IOException;
  
  void setTypeOriginal(String paramString) throws IOException;
  
  String getTypeOriginal();
  
  void setTypeSchema(String paramString) throws IOException;
  
  String getTypeSchema();
  
  void setTypeName(String paramString) throws IOException;
  
  String getTypeName();
  
  void setNullable(boolean paramBoolean) throws IOException;
  
  boolean isNullable();
  
  void setDefaultValue(String paramString) throws IOException;
  
  String getDefaultValue();
  
  void setCardinality(int paramInt) throws IOException;
}
