// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.utils.jdbc.BaseBlob;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;

/**
 * Tibero BLOB 래퍼. 이진 스트림을 요청하면 그 시점에 내용을 모두 읽어 메모리 스트림으로 돌려준다.
 * 사유는 {@link TiberoClob}과 같다.
 */
public class TiberoBlob extends BaseBlob {

    private byte[] _abMaterialized = null;

    public TiberoBlob(Blob blobWrapped) {
        super(blobWrapped);
    }

    protected byte[] materialize() throws SQLException {
        if (_abMaterialized == null) {
            long lLength = length();
            if (lLength > Integer.MAX_VALUE) {
                throw new SQLException("BLOB longer than " + Integer.MAX_VALUE + " bytes is not supported: " + lLength);
            }
            _abMaterialized = (lLength <= 0) ? new byte[0] : getBytes(1, (int) lLength);
        }
        return _abMaterialized;
    }

    @Override
    public InputStream getBinaryStream() throws SQLException {
        return new ByteArrayInputStream(materialize());
    }
}
