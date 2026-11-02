// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api.convertableSiardArchive.Siard22;

import ch.admin.bar.siard2.api.generated.DigestTypeType;
import ch.admin.bar.siard2.api.generated.MessageDigestType;


public class ConvertableSiard22MessageDigestType
  extends MessageDigestType
{
  ConvertableSiard22MessageDigestType(String digest, DigestTypeType digestType) {
    this.digest = digest;
    this.digestType = digestType;
  }
}
