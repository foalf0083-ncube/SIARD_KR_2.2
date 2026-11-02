// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.mysql.expression;

import ch.enterag.sqlparser.SqlFactory;
import ch.enterag.sqlparser.expression.ValueExpressionPrimary;





















public class MySqlValueExpressionPrimary
  extends ValueExpressionPrimary
{
  public String format() {
    String sExpression = "";
    
    if (isArrayValueConstructor())
      throw new IllegalArgumentException("ARRAY value constructor is not available in MySQL!"); 
    if (isMultisetValueConstructor())
      throw new IllegalArgumentException("MULTISET value constructor is not available in MySQL!"); 
    if (isTableMultisetValueConstructor()) {
      throw new IllegalArgumentException("TABLE MULTISET value constructor is not available in MySQL!");
    }
    sExpression = super.format();

    
    return sExpression;
  }






  
  public MySqlValueExpressionPrimary(SqlFactory sf) {
    super(sf);
  }
}
