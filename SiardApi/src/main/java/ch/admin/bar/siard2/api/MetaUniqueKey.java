// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface MetaUniqueKey extends MetaSearch {
  MetaTable getParentMetaTable();
  
  boolean isValid();
  
  String getName();
  
  int getColumns();
  
  String getColumn(int paramInt);
  
  void addColumn(String paramString) throws IOException;
  
  String getColumnsString();
  
  void setDescription(String paramString);
  
  String getDescription();
}
