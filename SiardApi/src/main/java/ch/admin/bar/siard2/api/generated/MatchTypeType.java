// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.generated;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


























@XmlType(name = "matchTypeType", namespace = "http://www.bar.admin.ch/xmlns/siard/2/metadata.xsd")
@XmlEnum
public enum MatchTypeType
{
  FULL,
  PARTIAL,
  SIMPLE;
  
  public String value() {
    return name();
  }
  
  public static MatchTypeType fromValue(String v) {
    return valueOf(v);
  }
}
