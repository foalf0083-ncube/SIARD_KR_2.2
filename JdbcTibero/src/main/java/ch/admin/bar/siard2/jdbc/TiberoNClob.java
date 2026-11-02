// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import java.sql.NClob;

/** Tibero NCLOB 래퍼. 동작은 {@link TiberoClob}과 같고 타입만 {@link NClob}을 유지한다. */
public class TiberoNClob extends TiberoClob implements NClob {

    public TiberoNClob(NClob nclobWrapped) {
        super(nclobWrapped);
    }
}
