// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import ch.admin.bar.siard2.api.Archive;
import ch.admin.bar.siard2.api.primary.ArchiveImpl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Mapping
{
  protected static Map<String, String> getDisambiguated(List<String> list, int iMaxLength) {
    Map<String, String> map = new HashMap<>();
    for (Iterator<String> iter = list.iterator(); iter.hasNext(); ) {
      String sOriginalName = iter.next();
      String sMappedName = sOriginalName;
      if (iMaxLength > 0) {
        
        if (sOriginalName.length() > iMaxLength)
          sMappedName = sOriginalName.substring(0, iMaxLength - 1) + "_"; 
        for (int iCounter = 0; map.containsValue(sMappedName); iCounter++) {
          
          String sSuffix = "_" + iCounter;
          sMappedName = sOriginalName.substring(0, iMaxLength - sSuffix.length()) + sSuffix;
        } 
      } 
      map.put(sOriginalName, sMappedName);
    }
    return map;
  }
}
