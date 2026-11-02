// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface RecordRetainer {
  Record create() throws IOException;
  
  void put(Record paramRecord) throws IOException;
  
  void close() throws IOException;
  
  long getPosition();
  
  long getByteCount();
}
