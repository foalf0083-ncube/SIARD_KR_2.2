// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.generated.old10;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;













































@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "referenceType", namespace = "http://www.bar.admin.ch/xmlns/siard/1.0/metadata.xsd", propOrder = {"column", "referenced"})
public class ReferenceType
{
  @XmlElement(namespace = "http://www.bar.admin.ch/xmlns/siard/1.0/metadata.xsd", required = true)
  protected String column;
  @XmlElement(namespace = "http://www.bar.admin.ch/xmlns/siard/1.0/metadata.xsd", required = true)
  protected String referenced;
  
  public String getColumn() {
    return this.column;
  }








  
  public void setColumn(String value) {
    this.column = value;
  }








  
  public String getReferenced() {
    return this.referenced;
  }








  
  public void setReferenced(String value) {
    this.referenced = value;
  }
}
