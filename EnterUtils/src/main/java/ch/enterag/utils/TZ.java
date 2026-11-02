// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils;

import java.util.TimeZone;











public abstract class TZ
{
  private static final TimeZone m_tzLocal = TimeZone.getDefault();
  public static void setLocalTimeZone() { TimeZone.setDefault(m_tzLocal); } public static void setUtcTimeZone() {
    TimeZone.setDefault(getUtcTimeZone());
  }




  
  public static TimeZone getUtcTimeZone() {
    TimeZone tz = TimeZone.getTimeZone("GMT");
    tz.setRawOffset(0);
    return tz;
  }
}
