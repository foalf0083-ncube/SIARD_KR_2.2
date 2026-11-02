// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.generated.old10;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

























@XmlType(name = "actionTimeType", namespace = "http://www.bar.admin.ch/xmlns/siard/1.0/metadata.xsd")
@XmlEnum
public enum ActionTimeType
{
  BEFORE,
  AFTER;
  
  public String value() {
    return name();
  }
  
  public static ActionTimeType fromValue(String v) {
    return valueOf(v);
  }
}
