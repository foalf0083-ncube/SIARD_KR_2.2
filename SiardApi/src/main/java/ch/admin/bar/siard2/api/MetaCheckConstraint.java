// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface MetaCheckConstraint extends MetaSearch {
  MetaTable getParentMetaTable();
  
  boolean isValid();
  
  String getName();
  
  void setCondition(String paramString) throws IOException;
  
  String getCondition();
  
  void setDescription(String paramString);
  
  String getDescription();
}
