// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import ch.enterag.utils.jdbc.BaseDriver;
import ch.enterag.utils.logging.IndentLogger;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Tibero JDBC 드라이버를 원 SIARD 래퍼 규약으로 감싼다.
 *
 * <p>벤더 드라이버는 클래스 이름 문자열로만 가리키므로 이 모듈은 Tibero 클래스를 컴파일 의존하지 않는다.
 * Tibero 5 · 6 · 7 드라이버가 모두 같은 클래스 이름({@value #sTIBERO_DRIVER_CLASS})을 쓰므로
 * 실행 클래스패스에는 한 번에 한 판만 둔다.
 *
 * <p>등록 원리는 {@link BaseDriver#register}와 같다. DriverManager에서 벤더 드라이버를 내리고 이 래퍼를 그 자리에
 * 등록하며, 접속은 벤더 드라이버로 맺은 뒤 {@link TiberoConnection}으로 감싼다.
 */
public class TiberoDriver extends BaseDriver implements Driver {

    private static final IndentLogger _il = IndentLogger.getIndentLogger(TiberoDriver.class.getName());

    public static final String sTIBERO_SCHEME = "tibero";
    public static final String sTIBERO_URL_PREFIX = sJDBC_SCHEME + ":" + sTIBERO_SCHEME + ":";
    /** 벤더 드라이버 클래스. 5 · 6 · 7 공통. */
    public static final String sTIBERO_DRIVER_CLASS = "com.tmax.tibero.jdbc.TbDriver";
    public static final String sTIBERO_SAMPLE_URL = "jdbc:tibero:thin:@localhost:8629:tibero";

    public static void register() {
        try {
            BaseDriver.register(new TiberoDriver(), sTIBERO_DRIVER_CLASS, sTIBERO_SAMPLE_URL);
        } catch (Exception e) {
            _il.event("Tibero JDBC driver " + sTIBERO_DRIVER_CLASS
                    + " could not be registered. Put one Tibero JDBC jar (5, 6 or 7) on the class path: " + e);
            throw new Error(e);
        }
    }

    static {
        register();
    }

    /** {@inheritDoc} 스킴 비교는 대소문자를 가리지 않는다. */
    @Override
    public boolean acceptsURL(String url) {
        return (url != null)
                && url.regionMatches(true, 0, sTIBERO_URL_PREFIX, 0, sTIBERO_URL_PREFIX.length());
    }

    /** {@inheritDoc} 벤더 접속을 {@link TiberoConnection}으로 감싼다. */
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        _il.enter(url);
        Connection conn = super.connect(url, info);
        if (conn != null) {
            conn = new TiberoConnection(conn);
        }
        _il.exit(conn);
        return conn;
    }
}
