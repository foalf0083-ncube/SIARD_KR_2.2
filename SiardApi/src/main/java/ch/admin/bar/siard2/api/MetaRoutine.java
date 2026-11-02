// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface MetaRoutine extends MetaSearch {
  MetaSchema getParentMetaSchema();
  
  boolean isValid();
  
  String getSpecificName();
  
  void setName(String paramString);
  
  String getName();
  
  void setBody(String paramString);
  
  String getBody();
  
  void setSource(String paramString) throws IOException;
  
  String getSource();
  
  void setDescription(String paramString);
  
  String getDescription();
  
  void setCharacteristic(String paramString) throws IOException;
  
  String getCharacteristic();
  
  void setReturnType(String paramString) throws IOException;
  
  void setReturnPreType(int paramInt1, long paramLong, int paramInt2) throws IOException;
  
  String getReturnType();
  
  int getMetaParameters();
  
  MetaParameter getMetaParameter(int paramInt);
  
  MetaParameter getMetaParameter(String paramString);
  
  MetaParameter createMetaParameter(String paramString) throws IOException;
}
