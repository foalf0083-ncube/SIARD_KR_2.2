// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.archive;

import ch.admin.bar.siardsuite.ui.common.Workflow;
import ch.admin.bar.siardsuite.ui.component.ButtonBox;
import ch.admin.bar.siardsuite.framework.ServicesFacade;
import ch.admin.bar.siardsuite.framework.dialogs.Dialogs;
import ch.admin.bar.siardsuite.framework.navigation.Navigator;
import ch.admin.bar.siardsuite.framework.steps.StepperNavigator;
import ch.admin.bar.siardsuite.ui.View;
import ch.admin.bar.siardsuite.ui.presenter.archive.model.DbmsWithInitialValue;
import ch.admin.bar.siardsuite.service.database.DbmsRegistry;
import ch.admin.bar.siardsuite.framework.view.FXMLLoadHelper;
import ch.admin.bar.siardsuite.framework.view.LoadedView;
import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import ch.admin.bar.siardsuite.framework.i18n.keys.I18nKey;
import io.github.palexdev.materialfx.controls.MFXRadioButton;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import lombok.val;

import static ch.admin.bar.siardsuite.ui.component.ButtonBox.Type.DEFAULT;

public class ArchiveChooseDbmsPresenter {

    private static final I18nKey TITLE = I18nKey.of("archiveDb.view.title");
    private static final I18nKey TEXT = I18nKey.of("archiveDb.view.text");
    private static final I18nKey ERROR = I18nKey.of("archiveDb.view.error");

    // 재현 흐름이 이 화면을 그대로 쓴다. 문구까지 공유하면 재현 중에
    // 「데이터베이스를 추출하려고 합니다」가 나온다. 문맥별로 갈라 쓴다.
    private static final I18nKey UPLOAD_TITLE = I18nKey.of("uploadDb.view.title");
    private static final I18nKey UPLOAD_TEXT = I18nKey.of("uploadDb.view.text");

    private I18nKey titleKey = TITLE;
    private I18nKey textKey = TEXT;

    @FXML
    public Text title;
    @FXML
    public Text text;
    @FXML
    public VBox leftVBox;
    @FXML
    public VBox rightVBox;
    @FXML
    public Label errorMessage;
    @FXML
    public BorderPane borderPane;
    @FXML
    private ButtonBox buttonsBox;

    private final ToggleGroup toggleGroup = new ToggleGroup();

    private boolean next = true;

    public void init(
            final StepperNavigator<DbmsWithInitialValue> stepperNavigator,
            final Navigator navigator,
            final Dialogs dialogs,
            final DbmsRegistry dbmsRegistry
    ) {
        title.textProperty().bind(DisplayableText.of(this.titleKey).bindable());
        text.textProperty().bind(DisplayableText.of(this.textKey).bindable());
        errorMessage.textProperty().bind(DisplayableText.of(ERROR).bindable());

        this.errorMessage.setVisible(false);

        DbmsRegistry.getSupportedDbms()
                .forEach(this::createRadioToVBox);

        this.buttonsBox = new ButtonBox().make(DEFAULT);
        this.borderPane.setBottom(buttonsBox);

        this.buttonsBox.next().setOnAction((event) -> {
            MFXRadioButton selected = (MFXRadioButton) toggleGroup.getSelectedToggle();
            if (selected != null) {
                val selectedDbms = DbmsRegistry.findDbmsByName(selected.getText());
                this.errorMessage.setVisible(false);

                stepperNavigator.next(DbmsWithInitialValue.builder()
                        .dbms(selectedDbms)
                        .build());
            } else {
                this.errorMessage.setVisible(true);
            }
        });
        this.buttonsBox.previous().setOnAction((event) -> navigator
                .navigate(View.START_WITH_WORKFLOW, Workflow.ARCHIVE));
        this.buttonsBox.cancel().setOnAction((event) -> dialogs
                .open(View.ARCHIVE_ABORT_DIALOG));
    }

    private void createRadioToVBox(String s) {
        VBox vBox = next ? leftVBox : rightVBox;
        next = !next;
        MFXRadioButton radioButton = new MFXRadioButton(s);
        radioButton.setToggleGroup(toggleGroup);
        vBox.getChildren().add(radioButton);
        VBox.setMargin(radioButton, new Insets(0, 0, 25, 0));
    }

    /** 재현 흐름용 — 화면은 같고 문구만 다르다. */
    public static LoadedView<ArchiveChooseDbmsPresenter> loadForUpload(
            final StepperNavigator<DbmsWithInitialValue> navigator,
            final ServicesFacade servicesFacade
    ) {
        val loaded = load(navigator, servicesFacade);
        loaded.getController().useUploadTexts();
        return loaded;
    }

    private void useUploadTexts() {
        this.titleKey = UPLOAD_TITLE;
        this.textKey = UPLOAD_TEXT;
        title.textProperty().unbind();
        text.textProperty().unbind();
        title.textProperty().bind(DisplayableText.of(UPLOAD_TITLE).bindable());
        text.textProperty().bind(DisplayableText.of(UPLOAD_TEXT).bindable());
    }

    public static LoadedView<ArchiveChooseDbmsPresenter> load(
            final StepperNavigator<DbmsWithInitialValue> navigator,
            final ServicesFacade servicesFacade
    ) {
        val loaded = FXMLLoadHelper.<ArchiveChooseDbmsPresenter>load("fxml/archive/archive-choose-dbms.fxml");
        loaded.getController().init(
                navigator,
                servicesFacade.navigator(),
                servicesFacade.dialogs(),
                servicesFacade.getService(DbmsRegistry.class)
        );

        return loaded;
    }
}
