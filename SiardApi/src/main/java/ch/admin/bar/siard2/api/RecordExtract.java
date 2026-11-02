// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface RecordExtract {
  Table getTable();
  
  RecordExtract getParentRecordExtract();
  
  long getOffset();
  
  long getDelta();
  
  String getLabel();
  
  Record getRecord();
  
  int getRecordExtracts();
  
  RecordExtract getRecordExtract(int paramInt) throws IOException;
}
