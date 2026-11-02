// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard22;

import ch.admin.bar.siard2.api.generated.ForeignKeyType;
import ch.admin.bar.siard2.api.generated.MatchTypeType;
import ch.admin.bar.siard2.api.generated.ReferenceType;
import ch.admin.bar.siard2.api.generated.ReferentialActionType;
import java.util.List;




public class ConvertableSiard22ForeignKeyTypes
  extends ForeignKeyType
{
  public ConvertableSiard22ForeignKeyTypes(String name, String description, MatchTypeType matchType, ReferentialActionType deleteAction, ReferentialActionType updateAction, String referencedSchema, String referencedTable, List<ReferenceType> references) {
    this.name = name;
    this.description = description;
    this.matchType = matchType;
    this.deleteAction = deleteAction;
    this.updateAction = updateAction;
    this.referencedSchema = referencedSchema;
    this.referencedTable = referencedTable;
    this.reference = references;
  }
}
