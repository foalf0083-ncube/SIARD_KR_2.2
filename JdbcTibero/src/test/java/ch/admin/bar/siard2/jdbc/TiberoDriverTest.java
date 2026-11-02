// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 드라이버 등록 시험. lib 폴더의 Tibero JDBC jar가 시험 클래스패스에 있어야 벤더 클래스를 찾는다.
 * 서버 접속은 하지 않는다.
 */
class TiberoDriverTest {

    @Test
    void acceptsTiberoUrlsRegardlessOfCase() {
        TiberoDriver driver = new TiberoDriver();
        assertTrue(driver.acceptsURL("jdbc:tibero:thin:@localhost:8629:tibero"));
        assertTrue(driver.acceptsURL("JDBC:TIBERO:thin:@host:8629:db"));
        assertFalse(driver.acceptsURL("jdbc:oracle:thin:@host:1521:orcl"));
        assertFalse(driver.acceptsURL("tibero:thin:@host"));
        assertFalse(driver.acceptsURL(null));
    }

    @Test
    void wrapperReplacesVendorDriverInDriverManager() {
        new TiberoDriver();   // 클래스 초기화로 register() 실행
        boolean wrapperRegistered = false;
        boolean vendorRegistered = false;
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            String name = drivers.nextElement().getClass().getName();
            if (name.equals(TiberoDriver.class.getName())) wrapperRegistered = true;
            if (name.equals(TiberoDriver.sTIBERO_DRIVER_CLASS)) vendorRegistered = true;
        }
        assertTrue(wrapperRegistered, "wrapper must be registered with DriverManager");
        assertFalse(vendorRegistered, "vendor driver must be deregistered from DriverManager");
    }
}
