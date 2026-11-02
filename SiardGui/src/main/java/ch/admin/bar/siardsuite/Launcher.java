// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite;


public class Launcher {

    /** 이 프로그램이 쓰는 기록 구현. 설정(logback.xml)도 이것을 전제로 쓰여 있다. */
    private static final String LOGBACK_PROVIDER = "ch.qos.logback.classic.spi.LogbackServiceProvider";

    public static void main(String[] args) {
        // 클래스 경로에 기록 구현이 둘 있다. logback 과 tika-app 안에 들어 있는 log4j2 다. 무엇이 잡힐지는
        // 경로 순서에 달려 있어 정해지지 않는다. 잡히는 것이 바뀌면 기록 파일도 화면 로그 창도 조용히 빈다.
        // 기록이 시작되기 전에 못 박는다.
        System.setProperty("slf4j.provider", LOGBACK_PROVIDER);
        ch.admin.bar.siardsuite.SiardApplication.main(args);
    }
}