// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard22;

import ch.admin.bar.siard2.api.generated.ReferenceType;

public class ConvertableSiard22ReferenceType
  extends ReferenceType {
  public ConvertableSiard22ReferenceType(String referenced, String column) {
    this.referenced = referenced;
    this.column = column;
  }
}
