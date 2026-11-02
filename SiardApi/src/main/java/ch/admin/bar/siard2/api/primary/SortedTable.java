// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.primary;

import ch.admin.bar.siard2.api.Table;
import ch.enterag.utils.background.Progress;
import java.io.IOException;
import java.io.InputStream;

public interface SortedTable {
  InputStream open() throws IOException;
  
  void sort(Table paramTable, boolean paramBoolean, int paramInt, Progress paramProgress) throws IOException;
  
  boolean getAscending();
  
  int getSortColumn();
}
