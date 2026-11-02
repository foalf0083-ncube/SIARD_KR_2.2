// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.archive.browser;

import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import ch.admin.bar.siardsuite.model.TreeAttributeWrapper;
import ch.admin.bar.siardsuite.ui.component.rendering.model.RenderableForm;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.COLUMN;
import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.SCHEMA;
import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.SCHEMA_TITLE;
import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.TABLE;
import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.TABLE_TITLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 목록 칸은 화면에 보이는 수만큼만 만들어 두고 스크롤할 때 다시 쓴다. 다시 쓸 때 앞서 보여 주던 줄의 선택이
 * 남아 다른 줄로 번지지 않아야 한다.
 */
class CheckBoxTreeCellReuseTest {

    private static final RenderableForm<String> FORM = RenderableForm.<String>builder()
            .dataSupplier(() -> "dummy")
            .build();

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        if (!ready.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX 툴킷이 30초 안에 준비되지 않았다");
        }
    }

    private static void onFxThread(Runnable action) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("화면 작업이 30초 안에 끝나지 않았다");
        }
        Throwable t = failure.get();
        if (t instanceof RuntimeException) throw (RuntimeException) t;
        if (t instanceof Error) throw (Error) t;
        if (t != null) throw new IllegalStateException(t);
    }

    private static TreeItem<TreeAttributeWrapper> item(
            String name, TreeAttributeWrapper.DatabaseAttribute attribute, boolean checkBox, boolean transferable) {
        return new TreeItem<>(TreeAttributeWrapper.builder()
                .name(DisplayableText.of(name))
                .viewTitle(DisplayableText.of(name))
                .renderableForm(FORM)
                .databaseAttribute(attribute)
                .shouldHaveCheckBox(checkBox)
                .transferable(transferable)
                .build());
    }

    private static CustomCheckBoxTreeCell tableCell(TreeView<TreeAttributeWrapper> treeView) {
        CustomCheckBoxTreeCell cell = (CustomCheckBoxTreeCell) new TableCheckBoxTreeCellFactory().call(treeView);
        cell.updateTreeView(treeView);
        return cell;
    }

    private static ColumnCheckBoxTreeCell columnCell(TreeView<TreeAttributeWrapper> treeView) {
        ColumnCheckBoxTreeCell cell = (ColumnCheckBoxTreeCell) new ColumnCheckBoxTreeCellFactory().call(treeView);
        cell.updateTreeView(treeView);
        return cell;
    }

    /** 목록 칸에 항목을 하나 그린다. 실제 목록이 스크롤할 때 하는 일과 같다. */
    private static void render(CustomCheckBoxTreeCell cell, TreeItem<TreeAttributeWrapper> treeItem) {
        cell.updateTreeItem(treeItem);
        cell.updateItem(treeItem.getValue(), false);
    }

    private static void render(ColumnCheckBoxTreeCell cell, TreeItem<TreeAttributeWrapper> treeItem) {
        cell.updateTreeItem(treeItem);
        cell.updateItem(treeItem.getValue(), false);
    }

    @Test
    void reusedCellDoesNotCarrySelectionToTheNextRow() throws InterruptedException {
        TreeItem<TreeAttributeWrapper> root = item("archive", null, false, false);
        TreeItem<TreeAttributeWrapper> tableA = item("T_A", TABLE, true, true);
        TreeItem<TreeAttributeWrapper> tableB = item("T_B", TABLE, true, true);
        root.getChildren().addAll(tableA, tableB);

        onFxThread(() -> {
            TreeView<TreeAttributeWrapper> treeView = new TreeView<>(root);
            CustomCheckBoxTreeCell cell = tableCell(treeView);

            render(cell, tableA);
            render(cell, tableB);   // 같은 칸을 다음 줄에 다시 쓴다

            // 앞서 보여 주던 줄이 바뀌어도 지금 줄은 그대로여야 한다
            tableA.getValue().setSelected(true);
            assertFalse(tableB.getValue().isSelected());
            CheckBox shown = assertInstanceOf(CheckBox.class, cell.getGraphic());
            assertFalse(shown.isSelected());

            // 지금 줄을 누르면 지금 줄만 바뀐다
            shown.setSelected(true);
            assertTrue(tableB.getValue().isSelected());
        });
    }

    @Test
    void reusedColumnCellDoesNotSpreadSelectionToAnotherTable() throws InterruptedException {
        TreeItem<TreeAttributeWrapper> root = item("archive", null, false, false);
        TreeItem<TreeAttributeWrapper> tableA = item("T_A", TABLE, false, false);
        TreeItem<TreeAttributeWrapper> columnsA = item("columns", COLUMN, true, false);
        TreeItem<TreeAttributeWrapper> columnA = item("C_A", COLUMN, true, true);
        TreeItem<TreeAttributeWrapper> tableB = item("T_B", TABLE, false, false);
        TreeItem<TreeAttributeWrapper> columnsB = item("columns", COLUMN, true, false);
        TreeItem<TreeAttributeWrapper> columnB = item("C_B", COLUMN, true, true);

        columnsA.getChildren().add(columnA);
        tableA.getChildren().add(columnsA);
        columnsB.getChildren().add(columnB);
        tableB.getChildren().add(columnsB);
        root.getChildren().addAll(tableA, tableB);

        onFxThread(() -> {
            TreeView<TreeAttributeWrapper> treeView = new TreeView<>(root);
            ColumnCheckBoxTreeCell cell = columnCell(treeView);

            render(cell, columnsA);
            render(cell, columnsB);   // 같은 칸을 다른 테이블의 컬럼 묶음에 다시 쓴다

            // 앞 테이블의 묶음을 켜도 뒤 테이블의 컬럼이 따라 켜지면 안 된다
            columnsA.getValue().setSelected(true);
            assertFalse(columnB.getValue().isSelected());
            assertFalse(columnsB.getValue().isSelected());
        });
    }

    @Test
    void cellsDoNotInstallSelectionRules() throws InterruptedException {
        TreeItem<TreeAttributeWrapper> root = item("archive", null, false, false);
        TreeItem<TreeAttributeWrapper> schemas = item("schemas", SCHEMA_TITLE, false, false);
        TreeItem<TreeAttributeWrapper> schema = item("APP", SCHEMA, true, true);
        TreeItem<TreeAttributeWrapper> tables = item("tables", TABLE_TITLE, false, false);
        TreeItem<TreeAttributeWrapper> table = item("T_A", TABLE, true, true);
        tables.getChildren().add(table);
        schema.getChildren().add(tables);
        schemas.getChildren().add(schema);
        root.getChildren().add(schemas);

        onFxThread(() -> {
            TreeView<TreeAttributeWrapper> treeView = new TreeView<>(root);
            for (int i = 0; i < 5; i++) {
                render(tableCell(treeView), schema);
            }

            // 연동은 목록 칸이 아니라 모형이 가진다. 칸을 아무리 만들어도 규칙이 붙지 않는다.
            schema.getValue().setSelected(true);
            assertFalse(table.getValue().isSelected());
        });
    }

    @Test
    void rowWithoutCheckBoxShowsNone() throws InterruptedException {
        TreeItem<TreeAttributeWrapper> root = item("archive", null, false, false);
        TreeItem<TreeAttributeWrapper> table = item("T_A", TABLE, true, true);
        TreeItem<TreeAttributeWrapper> columns = item("columns", COLUMN, false, false);
        table.getChildren().add(columns);
        root.getChildren().add(table);

        onFxThread(() -> {
            TreeView<TreeAttributeWrapper> treeView = new TreeView<>(root);
            CustomCheckBoxTreeCell cell = tableCell(treeView);

            render(cell, table);
            assertInstanceOf(CheckBox.class, cell.getGraphic());

            render(cell, columns);
            assertNull(cell.getGraphic());
        });
    }
}
