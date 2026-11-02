// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

import java.io.IOException;

public interface MetaField extends MetaValue {
  MetaColumn getParentMetaColumn();
  
  MetaField getParentMetaField();
  
  MetaAttribute getMetaAttribute() throws IOException;
}
