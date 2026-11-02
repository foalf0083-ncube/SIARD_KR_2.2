// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.generated;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;




























@XmlType(name = "referentialActionType", namespace = "http://www.bar.admin.ch/xmlns/siard/2/metadata.xsd")
@XmlEnum
public enum ReferentialActionType
{
  @XmlEnumValue("CASCADE")
  CASCADE("CASCADE"),
  @XmlEnumValue("SET NULL")
  SET_NULL("SET NULL"),
  @XmlEnumValue("SET DEFAULT")
  SET_DEFAULT("SET DEFAULT"),
  @XmlEnumValue("RESTRICT")
  RESTRICT("RESTRICT"),
  @XmlEnumValue("NO ACTION")
  NO_ACTION("NO ACTION");
  
  private final String value;
  
  ReferentialActionType(String v) {
    this.value = v;
  }
  
  public String value() {
    return this.value;
  }
  
  public static ReferentialActionType fromValue(String v) {
    for (ReferentialActionType c : values()) {
      if (c.value.equals(v)) {
        return c;
      }
    } 
    throw new IllegalArgumentException(v);
  }
}
