// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.archive.browser.forms;

import ch.admin.bar.siardsuite.util.I18n;
import ch.admin.bar.siard2.api.ext.SftpConnection;
import ch.admin.bar.siard2.api.ext.form.FormData;
import ch.admin.bar.siardsuite.model.TreeAttributeWrapper;
import ch.admin.bar.siardsuite.ui.common.ValidationProperty;
import ch.admin.bar.siardsuite.ui.common.Validator;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static ch.admin.bar.siard2.api.ext.SftpSender.checkConnection;

/**
 * 테이블 선택 시 SFTP 연결 설정 팝업
 */
public class SftpConnectionFormData {

    private final SftpConnection sftpConnection;
    private final Label hostLabel = new Label(I18n.get("sftp.label.host"));
    private final Label portLabel = new Label(I18n.get("sftp.label.port"));
    private final Label userLabel = new Label(I18n.get("sftp.label.user"));
    private final Label passwordLabel = new Label(I18n.get("sftp.label.password"));
    private final Label targetDirectoryLabel = new Label(I18n.get("sftp.label.targetDirectory"));

    private final Label hostErrorLabel = new Label();
    private final Label portErrorLabel = new Label();
    private final Label userErrorLabel = new Label();
    private final Label passwordErrorLabel = new Label();

    private final List<Label> errorLabelList = List.of(
            hostErrorLabel,
            portErrorLabel,
            userErrorLabel,
            passwordLabel
    );

    private final TreeAttributeWrapper treeAttributeWrapper;
    private final TextField hostField;
    private final TextField portField;
    private final TextField userField;
    private final TextField passwordField;
    private final TextField targetDirectoryField;

    private final List<ValidationProperty> validationProperties = new ArrayList<>();

    public SftpConnectionFormData(TreeAttributeWrapper item) {
        this.sftpConnection = item.getFormData().getSftpConnection();
        this.treeAttributeWrapper = item;
        this.hostField = new TextField(sftpConnection.getHost());
        this.portField = new TextField(String.valueOf(sftpConnection.getPort()));
        this.userField = new TextField(sftpConnection.getUser());
        this.passwordField = new TextField(sftpConnection.getPassword());
        this.targetDirectoryField = new TextField(item.getFormData().getTargetDirectory());
        validationProperties.addAll(
                List.of(new ValidationProperty(
                                hostField,
                                hostErrorLabel,
                                "connection.view.error.database.server"),
                        new ValidationProperty(
                                portField,
                                portErrorLabel,
                                "connection.view.error.port.number"),
                        new ValidationProperty(
                                userField,
                                userErrorLabel,
                                "connection.view.error.user.name"),
                        new ValidationProperty(
                                passwordField,
                                passwordErrorLabel,
                                "connection.view.error.user.password")
                )
        );
    }

    public Tab getSftpConnectionTab() {
        Tab sftpTab = new Tab(I18n.get("sftp.tab.title"));
        sftpTab.setClosable(false);

        VBox sftpForm = getFormLayout();
        sftpTab.setContent(sftpForm);

        return sftpTab;
    }

    private VBox getFormLayout() {
        VBox sftpFormLayout = new VBox(10);

        VBox layout = getLabelLayout();
        layout.getChildren().add(getBrowseBox());
        layout.getChildren().add(getSaveButton());

        // file copy tab
        sftpFormLayout.getChildren().add(layout);

        return sftpFormLayout;
    }

    private VBox getLabelLayout() {
        VBox formLayout = new VBox(10);
        formLayout.getChildren().addAll(
                hostLabel, hostField, hostErrorLabel,
                portLabel, portField, portErrorLabel,
                userLabel, userField, userErrorLabel,
                passwordLabel, passwordField, passwordErrorLabel
        );

        formLayout.setPadding(new Insets(30));
        return formLayout;
    }

    private HBox getBrowseBox() {
        HBox directoryBox = new HBox(20);
        targetDirectoryField.setPromptText(I18n.get("fileDownload.noDirectorySelected"));
        targetDirectoryField.setPrefWidth(500);

        Button browseButton = new Button(I18n.get("fileDownload.button.browse"));

        browseButton.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(I18n.get("fileDownload.selectDirectory.title"));

            File selectedDirectory = directoryChooser.showDialog(directoryBox.getScene().getWindow());

            if (selectedDirectory != null) {
                targetDirectoryField.setText(selectedDirectory.getAbsolutePath());
            }
        });

        directoryBox.getChildren().add(targetDirectoryField);
        directoryBox.getChildren().add(browseButton);
        return directoryBox;
    }

    private Button getSaveButton() {
        Button saveButton = new Button(I18n.get("button.save"));
        saveButton.setOnAction(e -> {
            hideAllValidationLabel();
            if (validated()) {
                boolean connectionChecked = checkConnection(
                        SftpConnection.builder()
                                .host(hostField.getText())
                                .port(Integer.parseInt(portField.getText()))
                                .user(userField.getText())
                                .password(passwordField.getText())
                                .build()
                );
                if (connectionChecked) {

                    String targetDirectoryFieldText = targetDirectoryField.getText();
                    SftpConnection sftpConnection = SftpConnection.builder()
                            .host(hostField.getText())
                            .port(Integer.parseInt(portField.getText()))
                            .user(userField.getText())
                            .password(passwordField.getText())
                            .ready(true)
                            .build();
                    FormData formData = FormData.builder()
                            .targetDirectory(targetDirectoryFieldText)
                            .sftp(true)
                            .copy(false)
                            .sftpConnection(sftpConnection)
                            .build();

                    treeAttributeWrapper.getFormData().updateConnectionData(formData);
                    ((Stage) saveButton.getScene().getWindow()).close();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(I18n.get("sftp.connectionFailed.title"));
                    alert.setHeaderText(null);
                    alert.setContentText(I18n.get("sftp.connectionFailed.message"));
                    alert.showAndWait();
                }
            } else {
                sftpConnection.setReady(false);
            }
        });

        return saveButton;
    }

    private void hideAllValidationLabel() {
        errorLabelList.forEach(l -> l.setVisible(false));
    }

    private boolean validated() {
        boolean allValidated = true;
        for (ValidationProperty validationProperty : validationProperties) {
            validationProperty.reset();
            boolean validate = validationProperty.validate();
            if (!validate) allValidated = false;
        }
        return allValidated && portValidated();
    }

    private boolean portValidated() {
        Validator<String> numericValueValidator = Validator.SHOULD_HAVE_NUMERIC_VALUE_VALIDATOR;
        boolean isNumericValue = numericValueValidator.getIsValidCheck().test(portField.getText());
        if (!isNumericValue) {
            portErrorLabel.setText(numericValueValidator.getMessage().getText());
            portErrorLabel.setVisible(true);
        }
        return isNumericValue;
    }

}
