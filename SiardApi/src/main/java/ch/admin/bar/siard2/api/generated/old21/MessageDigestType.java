// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.generated.old21;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;











































@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "messageDigestType", namespace = "http://www.bar.admin.ch/xmlns/siard/2/metadata.xsd", propOrder = {"digestType", "digest"})
public class MessageDigestType
{
  @XmlElement(namespace = "http://www.bar.admin.ch/xmlns/siard/2/metadata.xsd", required = true)
  @XmlSchemaType(name = "string")
  protected DigestTypeType digestType;
  @XmlElement(namespace = "http://www.bar.admin.ch/xmlns/siard/2/metadata.xsd", required = true)
  protected String digest;
  
  public DigestTypeType getDigestType() {
    return this.digestType;
  }








  
  public void setDigestType(DigestTypeType value) {
    this.digestType = value;
  }








  
  public String getDigest() {
    return this.digest;
  }








  
  public void setDigest(String value) {
    this.digest = value;
  }
}
