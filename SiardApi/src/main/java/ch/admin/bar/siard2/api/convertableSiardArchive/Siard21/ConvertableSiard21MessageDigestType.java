// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard21;


import ch.admin.bar.siard2.api.generated.old21.MessageDigestType;

public class ConvertableSiard21MessageDigestType extends MessageDigestType {

  public ConvertableSiard21MessageDigestType(MessageDigestType messageDigest) {
    this.digest = messageDigest.getDigest();
    this.digestType = messageDigest.getDigestType();
  }
  
  public ch.admin.bar.siard2.api.generated.MessageDigestType accept(Siard21Transformer visitor) {
    return visitor.visit(this);
  }
}
