// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd.utils;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;








public final class CastHelper
{
  public static <T> Optional<T> tryCast(Object o, Class<T> type) {
    if (type.isInstance(o)) {
      return Optional.of((T)o);
    }
    return Optional.empty();
  }



  
  public static <T> Stream<T> tryCastWithStream(Object o, Class<T> type) {
    if (type.isInstance(o)) {
      return Stream.of((T)o);
    }
    return Stream.empty();
  }
  
  public static <T, R> Function<T, Stream<R>> tryCast(Class<R> type) {
    return t -> tryCastWithStream(t, type);
  }
}
