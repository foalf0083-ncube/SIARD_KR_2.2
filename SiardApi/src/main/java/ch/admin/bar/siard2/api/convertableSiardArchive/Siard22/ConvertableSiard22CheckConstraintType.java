// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard22;

import ch.admin.bar.siard2.api.generated.CheckConstraintType;

public class ConvertableSiard22CheckConstraintType
  extends CheckConstraintType {
  public ConvertableSiard22CheckConstraintType(String name, String description, String condition) {
    this.name = name;
    this.description = description;
    this.condition = condition;
  }
}
