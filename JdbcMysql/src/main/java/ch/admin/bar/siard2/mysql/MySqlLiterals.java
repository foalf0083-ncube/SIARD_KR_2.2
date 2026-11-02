// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.mysql;

import ch.enterag.sqlparser.Interval;
import ch.enterag.sqlparser.SqlLiterals;
import ch.enterag.utils.BU;














public abstract class MySqlLiterals
  extends SqlLiterals
{
  private static final String sBYTE_LITERAL_PREFIX = "x";
  
  public static String formatStringLiteral(String sValue) {
    String sQuoted = sNULL;
    if (sValue != null) {
      sQuoted = "'" + sValue.replaceAll("'", "''") + "'";
    }
    return sQuoted;
  }








  
  public static String formatBytesLiteral(byte[] bufValue) {
    String sFormatted = sNULL;
    if (bufValue != null) {
      sFormatted = "x" + formatStringLiteral(BU.toHex(bufValue));
    }
    return sFormatted;
  }









  
  public static String formatIntervalLiteral(Interval intervalValue) {
    String sFormatted = sNULL;
    if (intervalValue != null) {
      sFormatted = formatBytesLiteral(serialize(intervalValue));
    }
    return sFormatted;
  }
}
