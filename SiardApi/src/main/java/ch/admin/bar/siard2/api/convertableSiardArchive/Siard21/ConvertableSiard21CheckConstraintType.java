// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard21;

import ch.admin.bar.siard2.api.convertableSiardArchive.Siard22.ConvertableSiard22CheckConstraintType;
import ch.admin.bar.siard2.api.convertableSiardArchive.Siard22.Siard21ToSiard22Transformer;
import ch.admin.bar.siard2.api.generated.old21.CheckConstraintType;

public class ConvertableSiard21CheckConstraintType
  extends CheckConstraintType
{
  public ConvertableSiard21CheckConstraintType(CheckConstraintType checkConstraintType) {
    this.name = checkConstraintType.getName();
    this.description = checkConstraintType.getDescription();
    this.condition = checkConstraintType.getCondition();
  }
  
  public ConvertableSiard22CheckConstraintType accept(Siard21ToSiard22Transformer visitor) {
    return visitor.visit(this);
  }
}
