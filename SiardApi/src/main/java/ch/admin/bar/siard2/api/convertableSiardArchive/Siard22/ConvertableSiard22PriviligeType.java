// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard22;

import ch.admin.bar.siard2.api.generated.PrivOptionType;
import ch.admin.bar.siard2.api.generated.PrivilegeType;

public class ConvertableSiard22PriviligeType
  extends PrivilegeType {
  public ConvertableSiard22PriviligeType(String type, String description, String grantee, String grantor, String object, PrivOptionType option) {
    this.type = type;
    this.description = description;
    this.grantee = grantee;
    this.grantor = grantor;
    this.object = object;
    this.option = option;
  }
}
