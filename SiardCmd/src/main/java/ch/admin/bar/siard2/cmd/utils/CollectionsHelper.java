// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public class CollectionsHelper
{
  public static <T> Set<T> setOf(T... entries) {
    HashSet<T> set = new HashSet<>(Arrays.asList(entries));
    return Collections.unmodifiableSet(set);
  }
}
