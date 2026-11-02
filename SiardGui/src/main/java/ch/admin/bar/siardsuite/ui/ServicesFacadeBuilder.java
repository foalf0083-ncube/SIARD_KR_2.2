// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui;

import ch.admin.bar.siardsuite.framework.ServicesFacade;
import ch.admin.bar.siardsuite.framework.dialogs.Dialogs;
import ch.admin.bar.siardsuite.framework.errors.Failure;
import ch.admin.bar.siardsuite.framework.errors.FailureDisplay;
import ch.admin.bar.siardsuite.framework.errors.HandlingInstruction;
import ch.admin.bar.siardsuite.framework.errors.MessageMatcher;
import ch.admin.bar.siardsuite.framework.errors.TypeAndMessageMatcher;
import ch.admin.bar.siardsuite.framework.errors.TypeMatcher;
import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import ch.admin.bar.siardsuite.framework.i18n.keys.I18nKey;
import ch.admin.bar.siardsuite.service.ArchiveHandler;
import ch.admin.bar.siardsuite.service.DbInteractionService;
import ch.admin.bar.siardsuite.service.FilesService;
import ch.admin.bar.siardsuite.service.InstallationService;
import ch.admin.bar.siardsuite.service.LogService;
import ch.admin.bar.siardsuite.service.database.DatabaseConnectionFactory;
import ch.admin.bar.siardsuite.service.database.DbmsRegistry;
import ch.admin.bar.siardsuite.service.preferences.UserPreferences;
import lombok.Getter;
import lombok.Setter;
import lombok.val;

import java.io.IOException;

public class ServicesFacadeBuilder {

    private static final I18nKey ACCESS_DENIED_TITLE = I18nKey.of("errors.accessDenied.title");
    private static final I18nKey ACCESS_DENIED_MESSAGE = I18nKey.of("errors.accessDenied.message");

    private static final I18nKey UNKNOWN_HOST_TITLE = I18nKey.of("errors.unknownHost.title");
    private static final I18nKey UNKNOWN_HOST_MESSAGE = I18nKey.of("errors.unknownHost.message");

    private static final I18nKey CONNECTION_REFUSED_TITLE = I18nKey.of("errors.connectionRefused.title");
    private static final I18nKey CONNECTION_REFUSED_MESSAGE = I18nKey.of("errors.connectionRefused.message");

    private static final I18nKey CORRUPTED_SIARD_ARCHIVE_TITLE = I18nKey.of("errors.corruptedSiardFile.title");
    private static final I18nKey CORRUPTED_SIARD_ARCHIVE_MESSAGE = I18nKey.of("errors.corruptedSiardFile.message");

    // 아래 셋은 실사용에서 자주 만나는데 분류가 없어 「예기치 않은 오류」로 떨어졌다.
    private static final I18nKey AUTH_FAILED_TITLE = I18nKey.of("errors.authFailed.title");
    private static final I18nKey AUTH_FAILED_MESSAGE = I18nKey.of("errors.authFailed.message");
    private static final HandlingInstruction AUTH_FAILED = HandlingInstruction.builder()
            // 데이터베이스마다 문구가 다르고, 드라이버 예외가 감싸여 올라와 타입으로는 잡히지 않는다.
            .matcher(MessageMatcher.builder()
                    .textFragment("password authentication failed")   // PostgreSQL
                    .textFragment("Incorrect or missing password")    // CUBRID
                    // MySQL·MariaDB 는 자격증명 오류(1045)와 권한 부족(1044)이 둘 다
                    // 'Access denied for user' 로 시작한다. 꼬리표로 갈라야 권한 부족을
                    // 「비밀번호가 틀렸다」로 잘못 안내하지 않는다.
                    .textFragment("(using password")                  // MySQL·MariaDB 자격증명
                    .textFragment("Login failed for user")            // MS SQL
                    .textFragment("invalid username/password")        // Oracle
                    .textFragment("password is not correct")
                    .build())
            .title(DisplayableText.of(AUTH_FAILED_TITLE))
            .message(DisplayableText.of(AUTH_FAILED_MESSAGE))
            .build();

    // MS SQL 의 'Login failed for user' 는 AUTH_FAILED 가 가져간다. 사유도 조치도 같은데
    // 따로 두면 MS SQL 만 제목이 달라져 같은 일에 두 얼굴이 생긴다.

    private static final I18nKey BROKEN_ZIP_TITLE = I18nKey.of("errors.brokenArchive.title");
    private static final I18nKey BROKEN_ZIP_MESSAGE = I18nKey.of("errors.brokenArchive.message");
    private static final HandlingInstruction BROKEN_ZIP = HandlingInstruction.builder()
            .matcher(MessageMatcher.builder()
                    // 'Invalid SIARD file' 은 여기 두지 않는다. 그것은 압축이 깨진 것이 아니라
                    // 압축은 멀쩡한데 SIARD 구조가 아닌 경우라 CORRUPTED_SIARD_ARCHIVE 소관이다.
                    // 양쪽이 나눠 가지면 같은 원인이 조건에 따라 다른 제목으로 뜬다.
                    .textFragment("End of central directory")
                    .textFragment("error in opening zip file")
                    .build())
            .title(DisplayableText.of(BROKEN_ZIP_TITLE))
            .message(DisplayableText.of(BROKEN_ZIP_MESSAGE))
            .build();

    private static final I18nKey UNUSABLE_IDENTIFIER_TITLE = I18nKey.of("errors.unusableIdentifier.title");
    private static final I18nKey UNUSABLE_IDENTIFIER_MESSAGE = I18nKey.of("errors.unusableIdentifier.message");
    private static final HandlingInstruction UNUSABLE_IDENTIFIER = HandlingInstruction.builder()
            .matcher(MessageMatcher.builder()
                    // 명령행과 화면이 같은 예외를 쓴다. 문구 조각은 두 언어 번들 모두에서 잡아야 한다.
                    .textFragment("그대로 쓸 수 없습니다")
                    .textFragment("is not representable")
                    .build())
            .title(DisplayableText.of(UNUSABLE_IDENTIFIER_TITLE))
            .message(DisplayableText.of(UNUSABLE_IDENTIFIER_MESSAGE))
            .build();

    private static final I18nKey RESERVED_SCHEMA_TITLE = I18nKey.of("errors.reservedSchemaName.title");
    private static final I18nKey RESERVED_SCHEMA_MESSAGE = I18nKey.of("errors.reservedSchemaName.message");
    private static final HandlingInstruction RESERVED_SCHEMA_NAME = HandlingInstruction.builder()
            // MS SQL 의 public 은 모든 데이터베이스에 있는 고정 역할 이름이라 스키마로 쓸 수 없다.
            // 따옴표로 감싸도 우회되지 않는다 — 이름을 바꾸는 것 말고 방법이 없다.
            // OBJECT_EXISTS 보다 앞에 등록해야 한다. 뒤에 두면 「덮어쓰기를 선택하십시오」라는
            // 통하지 않는 조치가 먼저 걸린다.
            .matcher(MessageMatcher.builder()
                    .textFragment("The name 'public' is reserved")
                    .textFragment("database principal or schema named 'public'")
                    .textFragment("already a user or role named 'public'")
                    .build())
            .title(DisplayableText.of(RESERVED_SCHEMA_TITLE))
            .message(DisplayableText.of(RESERVED_SCHEMA_MESSAGE))
            .build();

    private static final I18nKey OBJECT_EXISTS_TITLE = I18nKey.of("errors.objectExists.title");
    private static final I18nKey OBJECT_EXISTS_MESSAGE = I18nKey.of("errors.objectExists.message");
    private static final HandlingInstruction OBJECT_EXISTS = HandlingInstruction.builder()
            .matcher(TypeAndMessageMatcher.builder()
                    .expectedExceptionType(java.sql.SQLException.class)
                    .expectedTextFragment("There is already an object named")
                    .build())
            .title(DisplayableText.of(OBJECT_EXISTS_TITLE))
            .message(DisplayableText.of(OBJECT_EXISTS_MESSAGE))
            .build();

    private static final HandlingInstruction ACCESS_DENIED = HandlingInstruction.builder()
            .matcher(TypeAndMessageMatcher.builder()
                    .expectedExceptionType(java.sql.SQLException.class)
                    .expectedTextFragment("Access denied for user")
                    .build())
            .title(DisplayableText.of(ACCESS_DENIED_TITLE))
            .message(DisplayableText.of(ACCESS_DENIED_MESSAGE))
            .build();

    private static final HandlingInstruction UNKNOWN_HOST = HandlingInstruction.builder()
            .matcher(TypeMatcher.builder()
                    .expectedExceptionType(java.net.UnknownHostException.class)
                    .build())
            .title(DisplayableText.of(UNKNOWN_HOST_TITLE))
            .message(DisplayableText.of(UNKNOWN_HOST_MESSAGE))
            .build();

    private static final HandlingInstruction CONNECTION_REFUSED = HandlingInstruction.builder()
            .matcher(TypeMatcher.builder()
                    .expectedExceptionType(java.net.ConnectException.class)
                    .build())
            .title(DisplayableText.of(CONNECTION_REFUSED_TITLE))
            .message(DisplayableText.of(CONNECTION_REFUSED_MESSAGE))
            .build();

    private static final HandlingInstruction CORRUPTED_SIARD_ARCHIVE = HandlingInstruction.builder()
            .matcher(TypeAndMessageMatcher.builder()
                    .expectedExceptionType(IOException.class)
                    .expectedTextFragment("Invalid SIARD file")
                    .build())
            .title(DisplayableText.of(CORRUPTED_SIARD_ARCHIVE_TITLE))
            .message(DisplayableText.of(CORRUPTED_SIARD_ARCHIVE_MESSAGE))
            .build();

    private static final I18nKey NO_SCHEMA_TITLE = I18nKey.of("errors.noSchema.title");
    private static final I18nKey NO_SCHEMA_MESSAGE = I18nKey.of("errors.noSchema.message");
    private static final HandlingInstruction NO_SCHEMA = HandlingInstruction.builder()
            .matcher(TypeAndMessageMatcher.builder()
                    .expectedExceptionType(org.xml.sax.SAXParseException.class)
                    .expectedTextFragment("The content of element 'schemas' is not complete")
                    .build())
            .title(DisplayableText.of(NO_SCHEMA_TITLE))
            .message(DisplayableText.of(NO_SCHEMA_MESSAGE))
            .build();

    private static final I18nKey NO_COLUMNS_TITLE = I18nKey.of("errors.noColumns.title");
    private static final I18nKey NO_COLUMNS_MESSAGE = I18nKey.of("errors.noColumns.message");
    private static final HandlingInstruction NO_COLUMNS_AVAILABLE = HandlingInstruction.builder()
            .matcher(TypeAndMessageMatcher.builder()
                    .expectedExceptionType(org.xml.sax.SAXParseException.class)
                    .expectedTextFragment("The content of element 'columns' is not complete")
                    .build())
            .title(DisplayableText.of(NO_COLUMNS_TITLE))
            .message(DisplayableText.of(NO_COLUMNS_MESSAGE))
            .build();

    private static final I18nKey NO_USER_TITLE = I18nKey.of("errors.noUser.title");
    private static final I18nKey NO_USER_MESSAGE = I18nKey.of("errors.noUser.message");
    private static final HandlingInstruction NO_USER = HandlingInstruction.builder()
            .matcher(TypeAndMessageMatcher.builder()
                    .expectedExceptionType(org.xml.sax.SAXParseException.class)
                    .expectedTextFragment("The content of element 'users' is not complete")
                    .build())
            .title(DisplayableText.of(NO_USER_TITLE))
            .message(DisplayableText.of(NO_USER_MESSAGE))
            .build();

    public ServicesFacade build(final RootStage stage) {
        val archiveHandler = new ArchiveHandler();
        val dbmsRegistry = new DbmsRegistry();
        val installationService = new InstallationService();
        val userPreferences = new UserPreferences();
        val filesService = new FilesService();
        val logService = new LogService();
        val databaseConnectionFactory = new DatabaseConnectionFactory();

        val dbInteractionService = new DbInteractionService(archiveHandler, userPreferences, databaseConnectionFactory);

        val failureDisplay = new ShowDialogFailureDisplay();

        val services = ServicesFacade.builder()
                .viewDisplay(stage)
                .dialogDisplay(stage)
                .failureDisplay(failureDisplay)

                .service(archiveHandler)
                .service(dbmsRegistry)
                .service(installationService)
                .service(userPreferences)
                .service(filesService)
                .service(dbInteractionService)
                .service(logService)

                // 우선순위를 표현할 수단이 등록 순서뿐이다(ErrorHandler 는 같은 깊이에서
                // 먼저 등록된 것을 고른다). 좁은 조각을 앞에, 넓은 조각을 뒤에 둔다.
                .errorHandler(AUTH_FAILED)
                .errorHandler(ACCESS_DENIED)
                .errorHandler(UNKNOWN_HOST)
                .errorHandler(CONNECTION_REFUSED)
                .errorHandler(CORRUPTED_SIARD_ARCHIVE)
                .errorHandler(NO_SCHEMA)
                .errorHandler(NO_USER)
                .errorHandler(NO_COLUMNS_AVAILABLE)
                .errorHandler(BROKEN_ZIP)
                .errorHandler(RESERVED_SCHEMA_NAME)
                .errorHandler(OBJECT_EXISTS)
                .errorHandler(UNUSABLE_IDENTIFIER)

                .build();

        failureDisplay.setDialogs(services.dialogs());

        return services;
    }

    @Getter
    @Setter
    private static class ShowDialogFailureDisplay implements FailureDisplay {

        private Dialogs dialogs;

        @Override
        public void displayFailure(Failure failure) {
            dialogs.open(View.ERROR, failure);
        }
    }
}
