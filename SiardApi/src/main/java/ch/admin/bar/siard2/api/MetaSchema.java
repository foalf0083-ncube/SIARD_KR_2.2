// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface MetaSchema extends MetaSearch {
  MetaData getParentMetaData();
  
  Schema getSchema();
  
  boolean isValid();
  
  String getName();
  
  String getFolder();
  
  void setDescription(String paramString);
  
  String getDescription();
  
  int getMetaTables();
  
  MetaTable getMetaTable(int paramInt);
  
  MetaTable getMetaTable(String paramString);
  
  int getMetaViews();
  
  MetaView getMetaView(int paramInt);
  
  MetaView getMetaView(String paramString);
  
  MetaView createMetaView(String paramString) throws IOException;
  
  boolean removeMetaView(MetaView paramMetaView) throws IOException;
  
  int getMetaRoutines();
  
  MetaRoutine getMetaRoutine(int paramInt);
  
  MetaRoutine getMetaRoutine(String paramString);
  
  MetaRoutine createMetaRoutine(String paramString) throws IOException;
  
  int getMetaTypes();
  
  MetaType getMetaType(int paramInt);
  
  MetaType getMetaType(String paramString);
  
  MetaType createMetaType(String paramString) throws IOException;
}
