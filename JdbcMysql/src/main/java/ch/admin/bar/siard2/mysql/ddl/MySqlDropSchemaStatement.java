// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.mysql.ddl;

import ch.enterag.sqlparser.K;
import ch.enterag.sqlparser.SqlFactory;
import ch.enterag.sqlparser.ddl.DropSchemaStatement;
import ch.enterag.sqlparser.ddl.enums.DropBehavior;



















public class MySqlDropSchemaStatement
  extends DropSchemaStatement
{
  public MySqlDropSchemaStatement(SqlFactory sf) {
    super(sf);
  }



  
  public String format() {
    if (getDropBehavior() == DropBehavior.CASCADE)
      throw new IllegalArgumentException("Schema drop behavior CASCADE not supported by MSSQL!"); 
    String sStatement = K.DROP.getKeyword() + " " + K.SCHEMA.getKeyword() + " " + getSchemaName().format();
    return sStatement;
  }
}
