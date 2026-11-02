// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.generated.old21;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


























@XmlType(name = "actionTimeType", namespace = "http://www.bar.admin.ch/xmlns/siard/2/metadata.xsd")
@XmlEnum
public enum ActionTimeType
{
  BEFORE("BEFORE"),
  INSTEAD_OF("INSTEAD OF"),
  
  AFTER("AFTER");
  private final String value;
  
  ActionTimeType(String v) {
    this.value = v;
  }
  
  public String value() {
    return this.value;
  }
  
  public static ActionTimeType fromValue(String v) {
    for (ActionTimeType c : values()) {
      if (c.value.equals(v)) {
        return c;
      }
    } 
    throw new IllegalArgumentException(v);
  }
}
