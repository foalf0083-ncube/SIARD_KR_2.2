// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.mysql.expression;

import ch.admin.bar.siard2.mysql.MySqlLiterals;
import ch.enterag.sqlparser.SqlFactory;
import ch.enterag.sqlparser.expression.Literal;





















public class MySqlLiteral
  extends Literal
{
  public MySqlLiteral(SqlFactory sf) {
    super(sf);
  }







  
  public String format() {
    String sFormatted = "";
    
    if (getBytes() != null) {
      sFormatted = MySqlLiterals.formatBytesLiteral(getBytes());
    }
    
    return sFormatted;
  }
}
