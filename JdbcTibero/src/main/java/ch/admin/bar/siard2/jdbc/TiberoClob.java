// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.utils.jdbc.BaseClob;

import java.io.Reader;
import java.io.StringReader;
import java.sql.Clob;
import java.sql.SQLException;

/**
 * Tibero CLOB 래퍼. 문자 스트림을 요청하면 그 시점에 내용을 모두 읽어 메모리 스트림으로 돌려준다.
 *
 * <p>엔진은 스트림을 얻은 직후 {@code free()}를 부르고 스트림은 나중에 소비한다. Tibero 드라이버는
 * {@code free()} 뒤 로케이터 접근을 막으므로 지연 스트림이 실패한다. 현행 엔진의 Tibero 전용
 * 통째 읽기({@code getSubString(1, length)})와 같은 결과를 낸다. 조각 읽기로 바꾸는 것은
 * 실기동 검증(WP-06) 뒤에 판단한다.
 */
public class TiberoClob extends BaseClob {

    private String _sMaterialized = null;

    public TiberoClob(Clob clobWrapped) {
        super(clobWrapped);
    }

    protected String materialize() throws SQLException {
        if (_sMaterialized == null) {
            long lLength = length();
            if (lLength > Integer.MAX_VALUE) {
                throw new SQLException("CLOB longer than " + Integer.MAX_VALUE + " characters is not supported: " + lLength);
            }
            _sMaterialized = (lLength <= 0) ? "" : getSubString(1, (int) lLength);
        }
        return _sMaterialized;
    }

    @Override
    public Reader getCharacterStream() throws SQLException {
        return new StringReader(materialize());
    }
}
