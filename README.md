# SIARD KR 2.2

SIARD Suite를 바탕으로 한 관계형 데이터베이스 보존 도구입니다.
주요 수정 사항은 [변경 내역](CHANGELOG.md)을 참고하세요.

## 빌드·실행

JDK 17과 Git LFS가 필요합니다. `JAVA_HOME`을 JDK 17 설치 경로로 설정하세요.
최초 빌드에서는 Gradle과 의존성을 내려받을 인터넷 연결이 필요합니다.

```powershell
git lfs install
git clone https://github.com/foalf0083-ncube/SIARD_KR_2.2.git
cd SIARD_KR_2.2
git lfs pull
cd SiardGui
.\gradlew.bat :jar :SiardCmd:test :SiardApi:test :Zip64:test :JdbcTibero:test
.\gradlew.bat run
```

실행 스크립트·라이브러리 구성은 `installDist`, JRE 포함 구성은 `runtime` 태스크로 생성합니다.
산출물은 `SiardGui/build` 아래에 생성됩니다. Linux/macOS에서는 `./gradlew`를 사용합니다.
DB 접속정보는 실행 시 지정하며 저장소에 기록하지 마세요.

## 매뉴얼

[한국어 사용자 매뉴얼](SiardGui/docs/user-manual/ko/user-manual.adoc)을 제공합니다.
`SiardGui`에서 `.\gradlew.bat asciidoctorPdf`를 실행하면 PDF를 생성합니다.
[Tibero 래퍼 안내](JdbcTibero/README.md)도 참고하세요.

## 라이선스

원본 및 수정 소스의 CDDL 1.0은 [LICENSE.txt](LICENSE.txt), 저작권·수정자 표시는
[NOTICE.md](NOTICE.md)에 있습니다. 포함된 외부 라이브러리와 글꼴에는 각자의 라이선스가 적용됩니다.
