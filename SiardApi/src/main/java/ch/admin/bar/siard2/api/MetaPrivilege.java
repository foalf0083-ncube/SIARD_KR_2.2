// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface MetaPrivilege extends MetaSearch {
  MetaData getParentMetaData();
  
  String getType();
  
  String getObject();
  
  String getGrantor();
  
  String getGrantee();
  
  void setOption(String paramString) throws IOException;
  
  String getOption();
  
  void setDescription(String paramString);
  
  String getDescription();
}
