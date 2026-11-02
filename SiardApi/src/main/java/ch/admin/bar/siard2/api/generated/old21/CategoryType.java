// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.generated.old21;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


























@XmlType(name = "categoryType", namespace = "http://www.bar.admin.ch/xmlns/siard/2/metadata.xsd")
@XmlEnum
public enum CategoryType
{
  DISTINCT("distinct"),
  
  UDT("udt");
  
  private final String value;
  
  CategoryType(String v) {
    this.value = v;
  }
  
  public String value() {
    return this.value;
  }
  
  public static CategoryType fromValue(String v) {
    for (CategoryType c : values()) {
      if (c.value.equals(v)) {
        return c;
      }
    } 
    throw new IllegalArgumentException(v);
  }
}
