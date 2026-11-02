// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard22;

import ch.admin.bar.siard2.api.generated.RoleType;

public class ConvertableSiard22RoleType
  extends RoleType {
  public ConvertableSiard22RoleType(String name, String description, String admin) {
    this.name = name;
    this.description = description;
    this.admin = admin;
  }
}
