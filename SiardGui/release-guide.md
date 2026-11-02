# SIARD KR 배포 확인

1. JDK 17에서 `:jar :test :SiardCmd:test :SiardApi:test :Zip64:test :JdbcTibero:test`를 실행합니다.
2. `asciidoctorPdf installDist`로 매뉴얼과 실행본을 생성합니다. JRE 포함 실행본은 `runtime`으로 생성합니다.
3. 별도 시험 DB로 추출·열기 및 한글 표시를 확인합니다.
4. 배포본에 라이선스와 수정 고지가 포함되어 있는지 확인합니다.

Windows에서는 설치 경로를 짧게 유지하세요. 실행 스크립트의 클래스패스가 CMD 길이 제한을 넘을 수 있습니다.
Tibero JDBC 드라이버는 한 버전만 포함하고, 서버 버전에 대한 호환성을 확인하세요.
