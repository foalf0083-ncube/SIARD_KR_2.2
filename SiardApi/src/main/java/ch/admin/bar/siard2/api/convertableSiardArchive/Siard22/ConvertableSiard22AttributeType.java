// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard22;

import ch.admin.bar.siard2.api.generated.AttributeType;
import java.math.BigInteger;



public class ConvertableSiard22AttributeType
  extends AttributeType
{
  public ConvertableSiard22AttributeType(String name, String description, String type, String typeSchema, String typeName, BigInteger cardinality, String defaultValue, Boolean nullable, String typeOriginal) {
    this.name = name;
    this.description = description;
    this.type = type;
    this.typeSchema = typeSchema;
    this.typeName = typeName;
    this.cardinality = cardinality;
    this.defaultValue = defaultValue;
    this.nullable = nullable;
    this.typeOriginal = typeOriginal;
  }
}
