// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard21;

import ch.admin.bar.siard2.api.convertableSiardArchive.Siard22.ConvertableSiard22UniqueKeyType;
import ch.admin.bar.siard2.api.convertableSiardArchive.Siard22.Siard21ToSiard22Transformer;
import ch.admin.bar.siard2.api.generated.old21.UniqueKeyType;

public class ConvertableSiard21UniqueKeyType
  extends UniqueKeyType
{
  public ConvertableSiard21UniqueKeyType(UniqueKeyType uniqueKeyType) {
    this.name = uniqueKeyType.getName();
    this.description = uniqueKeyType.getDescription();
    this.column = uniqueKeyType.getColumn();
  }
  
  public ConvertableSiard22UniqueKeyType accept(Siard21ToSiard22Transformer visitor) {
    return visitor.visit(this);
  }
}
