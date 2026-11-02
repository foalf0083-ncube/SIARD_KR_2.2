// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.connection;

import ch.admin.bar.siardsuite.service.database.DbmsRegistry;
import ch.admin.bar.siardsuite.service.database.model.DbmsConnectionData;
import ch.admin.bar.siardsuite.service.database.model.ServerBasedDbms;
import ch.admin.bar.siardsuite.service.database.model.ServerBasedDbmsConnectionProperties;
import ch.admin.bar.siardsuite.ui.presenter.connection.fields.StringFormField;
import ch.admin.bar.siardsuite.ui.common.Validator;
import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import ch.admin.bar.siardsuite.framework.i18n.TranslatableText;
import ch.admin.bar.siardsuite.framework.i18n.keys.I18nKey;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import lombok.NonNull;
import lombok.val;

import java.util.Optional;
import java.util.function.Supplier;

public class ServerBasedDbmsConnectionPropertiesForm extends ConnectionPropertiesForm {

    private static final double PORT_FIELD_WITH = 120D;

    private static final I18nKey RIGHT_INFO_TEXT = I18nKey.of("connection.view.textRight");

    private static final I18nKey DB_SERVER_LABEL = I18nKey.of("connection.view.dbServer.label");
    private static final I18nKey DB_PORT_LABEL = I18nKey.of("connection.view.port.label");
    private static final I18nKey DB_NAME_LABEL = I18nKey.of("connection.view.databaseName.label");
    private static final I18nKey USERNAME_LABEL = I18nKey.of("connection.view.username.label");
    private static final I18nKey PASSWORD_LABEL = I18nKey.of("connection.view.password.label");

    private final Supplier<ServerBasedDbmsConnectionProperties> connectionPropertiesSupplier;
    private final ServerBasedDbms serverBasedDbms;

    private final StringFormField host;
    private final StringFormField port;
    private final StringFormField dbName;
    private final StringFormField jdbcUrl;

    public ServerBasedDbmsConnectionPropertiesForm(
            @NonNull final ServerBasedDbms dbms,
            @NonNull final Optional<ServerBasedDbmsConnectionProperties> initialValue
    ) {
        this.serverBasedDbms = dbms;

        host = StringFormField.builder()
                .title(TranslatableText.of(DB_SERVER_LABEL))
                .initialValue(initialValue
                        .map(ServerBasedDbmsConnectionProperties::getHost)
                        .orElse(""))
                .prompt(DisplayableText.of(dbms.getExampleHost()))
                .prefWidth(FORM_FIELD_WITH - PORT_FIELD_WITH - 10)
                .validator(Validator.IS_NOT_EMPTY_STRING_VALIDATOR)
                .validator(Validator.DOES_NOT_INCLUDE_COLONS_VALIDATOR)
                .onNewUserInput(newHost -> handleJdbcUrl())
                .build();

        port = StringFormField.builder()
                .title(TranslatableText.of(DB_PORT_LABEL))
                .initialValue(initialValue
                        .map(ServerBasedDbmsConnectionProperties::getPort)
                        .orElse(dbms.getExamplePort()))
                .prompt(DisplayableText.of(dbms.getExamplePort()))
                .prefWidth(PORT_FIELD_WITH)
                .validator(Validator.IS_NOT_EMPTY_STRING_VALIDATOR)
                .validator(Validator.DOES_NOT_INCLUDE_COLONS_VALIDATOR)
                .onNewUserInput(newHost -> handleJdbcUrl())
                .build();

        val urlAndPortHBox = new HBox(host, port);
        HBox.setMargin(host, new Insets(0, 10, 0, 0));

        dbName = StringFormField.builder()
                .title(TranslatableText.of(DB_NAME_LABEL))
                .initialValue(initialValue
                        .map(ServerBasedDbmsConnectionProperties::getDbName)
                        .orElse(""))
                .prompt(DisplayableText.of(dbms.getExampleDbName()))
                .prefWidth(FORM_FIELD_WITH)
                .validator(Validator.IS_NOT_EMPTY_STRING_VALIDATOR)
                .validator(Validator.DOES_NOT_INCLUDE_COLONS_VALIDATOR)
                .onNewUserInput(newHost -> handleJdbcUrl())
                .build();

        val username = StringFormField.builder()
                .title(TranslatableText.of(USERNAME_LABEL))
                .hint(TranslatableText.of(RIGHT_INFO_TEXT))
                .initialValue(initialValue
                        .map(ServerBasedDbmsConnectionProperties::getUser)
                        .orElse(""))
                .prefWidth(FORM_FIELD_WITH)
                .validator(Validator.IS_NOT_EMPTY_STRING_VALIDATOR)
                // 사용자명도 URL 을 다시 만들게 한다. 이 연결이 빠져 있어 CUBRID 처럼
                // 사용자명이 URL 에 들어가는 데이터베이스는 그 자리가 빈 채로 남았고,
                // 자기 검증에 걸려 다음 단계로 넘어갈 수 없었다.
                .onNewUserInput(newUser -> handleJdbcUrl())
                .build();

        val password = StringFormField.builder()
                .inputType(StringFormField.InputType.PASSWORD)
                .title(TranslatableText.of(PASSWORD_LABEL))
                .initialValue(initialValue
                        .map(ServerBasedDbmsConnectionProperties::getPassword)
                        .orElse(""))
                .prefWidth(FORM_FIELD_WITH)
                .validator(Validator.IS_NOT_EMPTY_STRING_VALIDATOR)
                .build();

        HBox.setMargin(urlAndPortHBox, new Insets(25));
        HBox.setMargin(username, new Insets(25));
        val firstLineHBox = new HBox(urlAndPortHBox, username);

        HBox.setMargin(dbName, new Insets(25));
        HBox.setMargin(password, new Insets(25));
        val secondLineHBox = new HBox(dbName, password);

        jdbcUrl = StringFormField.builder()
                .title(TranslatableText.of(JDBC_URL_LABEL))
                .initialValue(initialValue
                        .map(dbms.getJdbcConnectionStringEncoder())
                        .orElse(""))
                .prompt(DisplayableText.of(dbms.getJdbcConnectionStringEncoder().apply(ServerBasedDbmsConnectionProperties.builder()
                                .host(dbms.getExampleHost())
                                .port(dbms.getExamplePort())
                                .dbName(dbms.getExampleDbName())
                                .user("")
                                .password("")
                        .build())))
                .prefWidth(FORM_FIELD_WITH * 2)
                .validator(Validator.IS_NOT_EMPTY_STRING_VALIDATOR)
                .validator(validJdbcUrlValidator(serverBasedDbms))
                .onNewUserInput(newValue -> {
                    try {
                        val decoded = dbms.getJdbcConnectionStringDecoder().apply(newValue);
                        host.setValue(decoded.getHost());
                        port.setValue(decoded.getPort());
                        dbName.setValue(decoded.getDbName());
                    } catch (Exception e) {
                        // should not be thrown, because of validator
                    }
                })
                .build();

        HBox.setMargin(jdbcUrl, new Insets(25));
        val thirdLineHBox = new HBox(jdbcUrl);

        this.getChildren().addAll(
                firstLineHBox,
                secondLineHBox,
                thirdLineHBox
        );

        formFields.add(host);
        formFields.add(port);
        formFields.add(dbName);
        formFields.add(username);
        formFields.add(password);
        formFields.add(jdbcUrl);

        connectionPropertiesSupplier = () -> ServerBasedDbmsConnectionProperties.builder()
                .host(host.getValue())
                .port(port.getValue())
                .dbName(dbName.getValue())
                .user(username.getValue())
                .password(password.getValue())
                .build();
    }

    @Override
    public DbmsConnectionData getConnectionData() {
        return new DbmsConnectionData(serverBasedDbms, connectionPropertiesSupplier.get());
    }

    private void handleJdbcUrl() {
        // 사용자명은 아직 비어 있을 수 있으므로 조건에 넣지 않는다. 비어 있으면 그대로
        // 빈 자리로 만들어 두고, 값이 들어오는 즉시 위의 연결이 다시 부른다.
        if (host.hasValidValue() &&
                port.hasValidValue() &&
                dbName.hasValidValue()) {
            val currentProperties = connectionPropertiesSupplier.get();

            val currentJdbcUrl = serverBasedDbms.getJdbcConnectionStringEncoder().apply(currentProperties);
            jdbcUrl.setValue(currentJdbcUrl);
        }
    }

    private Validator<String> validJdbcUrlValidator(final ServerBasedDbms serverBasedDbms) {
        return Validator.<String>builder()
                .message(DisplayableText.of(INVALID_JDBC_URL_MESSAGE))
                .isValidCheck(DbmsRegistry.checkJdbcUrlValidity(serverBasedDbms))
                .build();
    }
}
