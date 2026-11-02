// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.mysql.ddl;

import ch.enterag.sqlparser.K;
import ch.enterag.sqlparser.SqlFactory;
import ch.enterag.sqlparser.ddl.DropTableStatement;


























public class MySqlDropTableStatement
  extends DropTableStatement
{
  public String format() {
    String sStatement = K.DROP.getKeyword() + " " + K.TABLE.getKeyword() + " " + getTableName().format();
    return sStatement;
  }





  
  public MySqlDropTableStatement(SqlFactory sf) {
    super(sf);
  }
}
