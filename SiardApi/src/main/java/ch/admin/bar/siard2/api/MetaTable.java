// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;
import java.util.List;

public interface MetaTable extends MetaSearch {
  MetaSchema getParentMetaSchema();
  
  Table getTable();
  
  boolean isValid();
  
  String getName();
  
  String getFolder();
  
  void setDescription(String paramString);
  
  String getDescription();
  
  void setRows(long paramLong) throws IOException;
  
  long getRows();
  
  int getMetaColumns();
  
  MetaColumn getMetaColumn(int paramInt);
  
  MetaColumn getMetaColumn(String paramString);
  
  MetaColumn createMetaColumn(String paramString) throws IOException;
  
  MetaUniqueKey getMetaPrimaryKey();
  
  MetaUniqueKey createMetaPrimaryKey(String paramString) throws IOException;
  
  int getMetaForeignKeys();
  
  MetaForeignKey getMetaForeignKey(int paramInt);
  
  MetaForeignKey getMetaForeignKey(String paramString);
  
  MetaForeignKey createMetaForeignKey(String paramString) throws IOException;
  
  int getMetaCandidateKeys();
  
  MetaUniqueKey getMetaCandidateKey(int paramInt);
  
  MetaUniqueKey getMetaCandidateKey(String paramString);
  
  MetaUniqueKey createMetaCandidateKey(String paramString) throws IOException;
  
  int getMetaCheckConstraints();
  
  MetaCheckConstraint getMetaCheckConstraint(int paramInt);
  
  MetaCheckConstraint getMetaCheckConstraint(String paramString);
  
  MetaCheckConstraint createMetaCheckConstraint(String paramString) throws IOException;
  
  int getMetaTriggers();
  
  MetaTrigger getMetaTrigger(int paramInt);
  
  MetaTrigger getMetaTrigger(String paramString);
  
  MetaTrigger createMetaTrigger(String paramString) throws IOException;
  
  List<List<String>> getColumnNames(boolean paramBoolean1, boolean paramBoolean2) throws IOException;
  
  String getType(List<String> paramList) throws IOException;
}
