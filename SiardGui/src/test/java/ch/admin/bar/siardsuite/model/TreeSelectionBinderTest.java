// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.model;

import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import ch.admin.bar.siardsuite.ui.component.rendering.model.RenderableForm;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.COLUMN;
import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.SCHEMA;
import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.SCHEMA_TITLE;
import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.TABLE;
import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.TABLE_TITLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 선택 연동이 모형에 걸리는지 확인한다. 화면(목록 칸)은 필요 없다.
 */
class TreeSelectionBinderTest {

    private static final RenderableForm<String> FORM = RenderableForm.<String>builder()
            .dataSupplier(() -> "dummy")
            .build();

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

    /** 뿌리 - 스키마 묶음 - 스키마 - 테이블 묶음 - 테이블 둘. 테이블 선택 화면과 같은 모양이다. */
    private static TreeItem<TreeAttributeWrapper> tableTree(
            TreeItem<TreeAttributeWrapper>[] out) {
        TreeItem<TreeAttributeWrapper> root = item("archive", null, false, false);
        TreeItem<TreeAttributeWrapper> schemas = item("schemas", SCHEMA_TITLE, false, false);
        TreeItem<TreeAttributeWrapper> schema = item("APP", SCHEMA, true, true);
        TreeItem<TreeAttributeWrapper> tables = item("tables", TABLE_TITLE, false, false);
        TreeItem<TreeAttributeWrapper> tableA = item("T_A", TABLE, true, true);
        TreeItem<TreeAttributeWrapper> tableB = item("T_B", TABLE, true, true);

        tables.getChildren().addAll(tableA, tableB);
        schema.getChildren().add(tables);
        schemas.getChildren().add(schema);
        root.getChildren().add(schemas);

        out[0] = schema;
        out[1] = tableA;
        out[2] = tableB;
        return root;
    }

    /** 테이블 - 컬럼 묶음 - 컬럼 둘. 파일 다운로드할 컬럼 선택 화면과 같은 모양이다. */
    private static TreeItem<TreeAttributeWrapper> columnTree(TreeItem<TreeAttributeWrapper>[] out) {
        TreeItem<TreeAttributeWrapper> root = item("archive", null, false, false);
        TreeItem<TreeAttributeWrapper> table = item("T_A", TABLE, false, false);
        TreeItem<TreeAttributeWrapper> columns = item("columns", COLUMN, true, false);
        TreeItem<TreeAttributeWrapper> columnA = item("C_A", COLUMN, true, true);
        TreeItem<TreeAttributeWrapper> columnB = item("C_B", COLUMN, true, true);

        columns.getChildren().addAll(columnA, columnB);
        table.getChildren().add(columns);
        root.getChildren().add(table);

        out[0] = columns;
        out[1] = columnA;
        out[2] = columnB;
        return root;
    }

    @Test
    void schemaSelectionReachesItsTables() {
        @SuppressWarnings("unchecked")
        TreeItem<TreeAttributeWrapper>[] nodes = new TreeItem[3];
        TreeItem<TreeAttributeWrapper> root = tableTree(nodes);
        TreeSelectionBinder.bindTableSelection(root, null);

        nodes[0].getValue().setSelected(true);
        assertTrue(nodes[1].getValue().isSelected());
        assertTrue(nodes[2].getValue().isSelected());

        nodes[0].getValue().setSelected(false);
        assertFalse(nodes[1].getValue().isSelected());
        assertFalse(nodes[2].getValue().isSelected());
    }

    @Test
    void tableSelectionDoesNotReachOtherTables() {
        @SuppressWarnings("unchecked")
        TreeItem<TreeAttributeWrapper>[] nodes = new TreeItem[3];
        TreeItem<TreeAttributeWrapper> root = tableTree(nodes);
        TreeSelectionBinder.bindTableSelection(root, null);

        nodes[1].getValue().setSelected(true);
        assertTrue(nodes[1].getValue().isSelected());
        assertFalse(nodes[2].getValue().isSelected());
        assertFalse(nodes[0].getValue().isSelected());
    }

    @Test
    void selectionChangeIsReportedOncePerChange() {
        @SuppressWarnings("unchecked")
        TreeItem<TreeAttributeWrapper>[] nodes = new TreeItem[3];
        TreeItem<TreeAttributeWrapper> root = tableTree(nodes);
        AtomicInteger calls = new AtomicInteger();

        // 같은 트리에 두 번 걸어도 연동은 한 벌만 남아야 한다.
        TreeSelectionBinder.bindTableSelection(root, calls::incrementAndGet);
        TreeSelectionBinder.bindTableSelection(root, calls::incrementAndGet);

        nodes[1].getValue().setSelected(true);
        assertEquals(1, calls.get());
    }

    @Test
    void columnGroupSelectionReachesItsColumns() {
        @SuppressWarnings("unchecked")
        TreeItem<TreeAttributeWrapper>[] nodes = new TreeItem[3];
        TreeItem<TreeAttributeWrapper> root = columnTree(nodes);
        TreeSelectionBinder.bindColumnSelection(root);

        nodes[0].getValue().setSelected(true);
        assertTrue(nodes[1].getValue().isSelected());
        assertTrue(nodes[2].getValue().isSelected());

        nodes[0].getValue().setSelected(false);
        assertFalse(nodes[1].getValue().isSelected());
        assertFalse(nodes[2].getValue().isSelected());
    }

    @Test
    void singleColumnSelectionStaysInItsOwnRow() {
        @SuppressWarnings("unchecked")
        TreeItem<TreeAttributeWrapper>[] nodes = new TreeItem[3];
        TreeItem<TreeAttributeWrapper> root = columnTree(nodes);
        TreeSelectionBinder.bindColumnSelection(root);

        nodes[1].getValue().setSelected(true);
        assertTrue(nodes[1].getValue().isSelected());
        assertFalse(nodes[2].getValue().isSelected());
        assertFalse(nodes[0].getValue().isSelected());
    }
}
