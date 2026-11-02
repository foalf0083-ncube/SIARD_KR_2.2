// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface MetaRole extends MetaSearch {
  MetaData getParentMetaData();
  
  String getName();
  
  void setAdmin(String paramString) throws IOException;
  
  String getAdmin();
  
  void setDescription(String paramString);
  
  String getDescription();
}
