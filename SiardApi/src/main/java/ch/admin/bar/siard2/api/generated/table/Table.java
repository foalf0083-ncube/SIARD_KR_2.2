// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.generated.table;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;



























































@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {"row"})
@XmlRootElement(name = "table", namespace = "http://www.bar.admin.ch/xmlns/siard/2/table.xsd")
public class Table
{
  @XmlElement(namespace = "http://www.bar.admin.ch/xmlns/siard/2/table.xsd")
  protected List<RecordType> row;
  @XmlAttribute(name = "version", required = true)
  protected String version;
  
  public List<RecordType> getRow() {
    if (this.row == null) {
      this.row = new ArrayList<>();
    }
    return this.row;
  }








  
  public String getVersion() {
    return this.version;
  }








  
  public void setVersion(String value) {
    this.version = value;
  }
}
