// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import ch.enterag.utils.DU;
import java.io.IOException;

public interface MetaSearch {
  String getFindString();
  
  int getFoundElement() throws IOException;
  
  String getFoundString(DU paramDU) throws IOException;
  
  int getFoundOffset();
  
  void find(String paramString, boolean paramBoolean) throws IOException;
  
  MetaSearch findNext(DU paramDU) throws IOException;
  
  boolean canFindNext();
}
