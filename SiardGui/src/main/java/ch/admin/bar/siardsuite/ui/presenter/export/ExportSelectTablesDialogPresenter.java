// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.export;

import ch.admin.bar.siard2.api.Archive;
import ch.admin.bar.siardsuite.framework.errors.ErrorHandler;
import ch.admin.bar.siardsuite.framework.ServicesFacade;
import ch.admin.bar.siardsuite.framework.dialogs.Dialogs;
import ch.admin.bar.siardsuite.ui.View;
import ch.admin.bar.siardsuite.ui.presenter.archive.browser.forms.utils.ListAssembler;
import ch.admin.bar.siardsuite.service.TableExporterService;
import ch.admin.bar.siardsuite.util.I18n;
import ch.admin.bar.siardsuite.framework.view.FXMLLoadHelper;
import ch.admin.bar.siardsuite.framework.view.LoadedView;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import javafx.util.Callback;
import lombok.val;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExportSelectTablesDialogPresenter {
    @FXML
    public Label title;

    @FXML
    public MFXButton closeButton;

    @FXML
    public Text text;

    @FXML
    public HBox buttonBox;

    @FXML
    public MFXButton cancelButton;

    @FXML
    public MFXButton saveButton;

    @FXML
    public TreeView tableSelector;

    private Archive archive;

    private Dialogs dialogs;

    public void init(
            final Archive archive,
            final Dialogs dialogs
    ) {
        this.archive = archive;
        this.dialogs = dialogs;

        this.title.textProperty().bind(I18n.createStringBinding("export.select-tables.dialog.title"));
        this.text.textProperty().bind(I18n.createStringBinding("export.select-tables.dialog.text"));

        EventHandler<ActionEvent> closeEvent = event -> dialogs.closeDialog();
        this.closeButton.setOnAction(closeEvent);

        this.cancelButton.setOnAction(closeEvent);
        this.cancelButton.textProperty().bind(I18n.createStringBinding("button.cancel"));

        this.saveButton.setOnAction(this::handleSaveClicked);
        this.saveButton.textProperty().bind(I18n.createStringBinding("export.choose-location.text"));
        val root = createTree();
        // 체크가 바뀌는 즉시 안내문을 되돌린다. 저장을 다시 누를 때까지 기다리면
        // 고른 것이 인정되지 않은 것처럼 보인다. 이 사건은 하위 항목에서 올라온다.
        root.addEventHandler(
                CheckBoxTreeItem.<String>checkBoxSelectionChangedEvent(),
                event -> restoreDefaultMessage());
        this.tableSelector.setRoot(root);
        this.tableSelector.setShowRoot(false);
        tableSelector.setCellFactory((Callback<TreeView<String>, TreeCell<String>>) param -> new CheckBoxTreeCell<>());
    }

    private static final String NO_SELECTION_STYLE = "error-message";

    /** 선택이 비었음을 안내한다. 다시 고르면 {@link #restoreDefaultMessage()} 가 되돌린다. */
    private void showNoSelectionMessage() {
        this.text.textProperty().unbind();
        this.text.setText(DisplayableText.of(
                ch.admin.bar.siardsuite.framework.i18n.keys.I18nKey.of("export.noSelection.text")).getText());
        if (!this.text.getStyleClass().contains(NO_SELECTION_STYLE)) {
            this.text.getStyleClass().add(NO_SELECTION_STYLE);
        }
    }

    /**
     * 원래 안내문과 모양으로 되돌린다.
     *
     * <p>되돌리지 않으면 테이블을 다시 골라도 「하나 이상 선택하십시오」가 붉은 채로 남아,
     * 고른 것이 인정되지 않은 것처럼 보인다.</p>
     */
    private void restoreDefaultMessage() {
        if (this.text.textProperty().isBound()) {
            return;
        }
        this.text.getStyleClass().remove(NO_SELECTION_STYLE);
        this.text.textProperty().bind(I18n.createStringBinding("export.select-tables.dialog.text"));
    }

    private void handleSaveClicked(ActionEvent actionEvent) {
        // 아무것도 고르지 않아도 진행되어 빈 폴더만 남던 것을 막는다.
        if (findCheckedItems().isEmpty()) {
            showNoSelectionMessage();
            return;
        }
        restoreDefaultMessage();
        final DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(I18n.get("export.choose-location.text"));
        File file = directoryChooser.showDialog(title.getScene().getWindow());
        if (Objects.nonNull(file)) {
            val namesOfSelectedTables = findCheckedItems().stream()
                    .map(TreeItem::getValue)
                    .collect(Collectors.toSet());

            // 진행 다이얼로그 열기: 선택된 테이블 목록 전달
            val allSelected = ListAssembler.assemble(archive.getSchemas(), archive::getSchema).stream()
                    .flatMap(schema -> ListAssembler.assemble(schema.getTables(), schema::getTable).stream())
                    .map(table -> table.getMetaTable().getName())
                    .filter(namesOfSelectedTables::contains)
                    .collect(Collectors.toList());
            dialogs.open(View.EXPORT_IN_PROGRESS, allSelected);

            val exporter = TableExporterService.builder()
                    .exportDir(file)
                    .schemas(ListAssembler.assemble(archive.getSchemas(), archive::getSchema))
                    .shouldBeExportedFilter(table -> namesOfSelectedTables.contains(
                            table.getMetaTable().getName()))
                    .build();

            exporter.setOnProgress(progress -> {
                ExportInProgressDialogPresenter controller = ExportInProgressDialogPresenter.getInstance();
                if (controller != null) controller.updateProgress(progress, "");
            });
            exporter.setOnTableExported(tableName -> {
                ExportInProgressDialogPresenter controller = ExportInProgressDialogPresenter.getInstance();
                if (controller != null) controller.updateProgress(-1, tableName);
            });

            // 시작 직후 0%를 즉시 반영하도록 호출
            {
                ExportInProgressDialogPresenter controller = ExportInProgressDialogPresenter.getInstance();
                if (controller != null) controller.updateProgress(0.0, "");
            }

            new Thread(exporter::export, "table-exporter-thread").start();
        }
    }

    private CheckBoxTreeItem<String> createTree() {
        val root = new CheckBoxTreeItem<>("root");
        root.setExpanded(true);

        val items = ListAssembler.assemble(archive.getSchemas(), archive::getSchema).stream()
                .map(schema -> {
                    val schemaItem = new CheckBoxTreeItem<>(schema.getMetaSchema().getName());
                    schemaItem.setExpanded(true);

                    val tableItems = ListAssembler.assemble(schema.getTables(), schema::getTable).stream()
                            .map(table -> new CheckBoxTreeItem<>(table.getMetaTable().getName()))
                            .collect(Collectors.toList());

                    schemaItem.getChildren().setAll(tableItems);

                    return schemaItem;
                })
                .collect(Collectors.toList());
        root.getChildren().setAll(items);

        return root;
    }

    private List<CheckBoxTreeItem<String>> findCheckedItems() {
        return findCheckedItems((CheckBoxTreeItem<String>) this.tableSelector.getRoot())
                .collect(Collectors.toList());
    }

    private Stream<CheckBoxTreeItem<String>> findCheckedItems(CheckBoxTreeItem<String> item) {
        val checkedItem = Stream.of(item)
                .filter(CheckBoxTreeItem::isSelected);

        val checkedChildItems = item.getChildren().stream()
                .map(child -> (CheckBoxTreeItem<String>) child)
                .flatMap(this::findCheckedItems);

        return Stream.concat(
                checkedItem,
                checkedChildItems
        );
    }

    public static LoadedView<ExportSelectTablesDialogPresenter> load(
            final Archive data,
            final ServicesFacade servicesFacade
    ) {
        val loaded = FXMLLoadHelper.<ExportSelectTablesDialogPresenter>load("fxml/export/export-select-tables-dialog.fxml");
        loaded.getController().init(
                data,
                servicesFacade.dialogs()
        );

        return loaded;
    }
}


