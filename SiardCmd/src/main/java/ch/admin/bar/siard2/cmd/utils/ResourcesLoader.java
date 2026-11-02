// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd.utils;

import java.io.InputStream;
import java.util.Optional;













public class ResourcesLoader
{
  public static InputStream loadResource(String resource) throws Throwable {
    Optional<InputStream> urlToResource = Optional.ofNullable(ResourcesLoader.class
        .getClassLoader()
        .getResourceAsStream(resource));
    
    return urlToResource
      .<Throwable>orElseThrow(() -> new IllegalArgumentException(String.format("Resource \"%s\" not found", resource)));
  }
}
