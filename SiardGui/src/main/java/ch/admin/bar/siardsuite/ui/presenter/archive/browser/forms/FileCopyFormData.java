// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.archive.browser.forms;

import ch.admin.bar.siardsuite.util.I18n;
import ch.admin.bar.siard2.api.ext.form.FormData;
import ch.admin.bar.siardsuite.model.TreeAttributeWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;

public class FileCopyFormData {

    private final TextField targetDirectoryField = new TextField();
    private final TreeAttributeWrapper treeAttributeWrapper;

    public FileCopyFormData(TreeAttributeWrapper treeAttributeWrapper) {
        this.treeAttributeWrapper = treeAttributeWrapper;
    }

    public Tab getFileCopyTab() {
        Tab fileCopyTab = new Tab(I18n.get("fileDownload.tab.fileCopy"));
        VBox fileCopyLayout = new VBox(10);
        fileCopyLayout.setPadding(new Insets(30));
        fileCopyLayout.getChildren().addAll(getBrowseBox(), getSaveButton());
        fileCopyTab.setClosable(false);
        fileCopyTab.setContent(fileCopyLayout);
        return fileCopyTab;
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
            String targetDirectoryFieldText = targetDirectoryField.getText();
            FormData formData = FormData.builder()
                    .sftpConnection(null)
                    .sftp(false)
                    .copy(true)
                    .targetDirectory(targetDirectoryFieldText)
                    .build();
            treeAttributeWrapper.getFormData().updateConnectionData(formData);
            ((Stage) saveButton.getScene().getWindow()).close();
        });
        return saveButton;
    }

}
