// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard21;

import ch.admin.bar.siard2.api.convertableSiardArchive.Siard22.Siard21ToSiard22Transformer;


public class ConvertableSiard21Archive extends ch.admin.bar.siard2.api.generated.old21.SiardArchive {
  public ch.admin.bar.siard2.api.generated.SiardArchive accept(Siard21ToSiard22Transformer visitor) {
    return visitor.visit(this);
  }
}
