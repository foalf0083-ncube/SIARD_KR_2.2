// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard10;

import ch.admin.bar.siard2.api.generated.old10.SiardArchive;

public class ConvertableSiard10Archive
  extends SiardArchive {
  public <T> T transform(SiardArchive10Transformer transformer) {
    return transformer.transform();
  }
}
