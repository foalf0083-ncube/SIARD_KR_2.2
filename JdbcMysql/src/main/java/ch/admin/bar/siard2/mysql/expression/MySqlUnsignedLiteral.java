// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.mysql.expression;

import ch.admin.bar.siard2.mysql.MySqlLiterals;
import ch.enterag.sqlparser.SqlFactory;
import ch.enterag.sqlparser.expression.UnsignedLiteral;





















public class MySqlUnsignedLiteral
  extends UnsignedLiteral
{
  public MySqlUnsignedLiteral(SqlFactory sf) {
    super(sf);
  }






  
  public String format() {
    String sExpression = null;
    
    if (getInterval() != null) {
      sExpression = MySqlLiterals.formatIntervalLiteral(getInterval());
    } else {
      sExpression = super.format();
    } 
    
    return sExpression;
  }
}
