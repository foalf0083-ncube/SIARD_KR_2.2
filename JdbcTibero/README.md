# Tibero JDBC 래퍼

SIARD의 `Base*` JDBC 래퍼 구조에 Tibero를 연결합니다. 메타데이터 순번·자료형,
LOB 스트림 및 시스템 스키마 필터를 보정합니다.

실행 시 `SiardGui/lib`에 호환되는 Tibero JDBC 드라이버 한 버전만 두세요.
래퍼는 벤더 클래스를 직접 import하지 않으며, 드라이버 사용·배포에는 공급자 라이선스가 적용됩니다.
실제 서버 검증 범위는 Tibero 6입니다. 다른 버전과 SIARD 재적재는 별도 검증이 필요합니다.

`SiardGui`에서 `.\gradlew.bat :JdbcTibero:test`로 서버 접속 없이 회귀시험을 실행합니다.
드라이버 등록 시험에는 Tibero JDBC JAR가 필요합니다.
