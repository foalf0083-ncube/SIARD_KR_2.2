// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.mysql.datatype;

import ch.enterag.sqlparser.SqlFactory;
import ch.enterag.sqlparser.datatype.DataType;
import ch.enterag.sqlparser.datatype.PredefinedType;





















public class MySqlDataType
  extends DataType
{
  public MySqlDataType(SqlFactory sf) {
    super(sf);
  }





  
  public String formatStructType() {
    PredefinedType pt = getSqlFactory().newPredefinedType();
    pt.initBlobType(-1, null);
    return pt.format();
  }





  
  public String formatRowType() {
    PredefinedType pt = getSqlFactory().newPredefinedType();
    pt.initBlobType(-1, null);
    return pt.format();
  }





  
  public String formatRefType() {
    PredefinedType pt = getSqlFactory().newPredefinedType();
    pt.initBlobType(-1, null);
    return pt.format();
  }





  
  public String formatArrayType() {
    PredefinedType pt = getSqlFactory().newPredefinedType();
    pt.initBlobType(-1, null);
    return pt.format();
  }





  
  public String formatMultisetType() {
    PredefinedType pt = getSqlFactory().newPredefinedType();
    pt.initBlobType(-1, null);
    return pt.format();
  }






  
  public String format() {
    String sDataType = null;
    switch (getType()) {
      case PRE:
        sDataType = getPredefinedType().format(); break;
      case STRUCT: sDataType = formatStructType(); break;
      case ROW: sDataType = formatRowType(); break;
      case REF: sDataType = formatRefType(); break;
      case ARRAY: sDataType = formatArrayType(); break;
      case MULTISET: sDataType = formatMultisetType(); break;
    } 
    return sDataType;
  }
}
