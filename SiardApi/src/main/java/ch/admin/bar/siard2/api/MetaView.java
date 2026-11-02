// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface MetaView extends MetaSearch {
  MetaSchema getParentMetaSchema();
  
  boolean isValid();
  
  String getName();
  
  void setQuery(String paramString);
  
  String getQuery();
  
  void setQueryOriginal(String paramString) throws IOException;
  
  String getQueryOriginal();
  
  void setDescription(String paramString);
  
  String getDescription();
  
  void setRows(long paramLong) throws IOException;
  
  long getRows();
  
  int getMetaColumns();
  
  MetaColumn getMetaColumn(int paramInt);
  
  MetaColumn getMetaColumn(String paramString);
  
  MetaColumn createMetaColumn(String paramString) throws IOException;
}
