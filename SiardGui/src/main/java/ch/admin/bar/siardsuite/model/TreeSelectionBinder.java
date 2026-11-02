// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.model;

import javafx.scene.control.TreeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static ch.admin.bar.siardsuite.model.TreeAttributeWrapper.DatabaseAttribute.TABLE;

/**
 * 선택 상태의 연동을 트리(모형)에 건다.
 *
 * <p>종전에는 목록 칸(TreeCell)이 그려질 때마다 체크박스를 새로 만들어 항목에 묶고 그 체크박스에 감시자를 달았다.
 * 목록 칸은 화면에 보이는 수만큼만 만들어 두고 스크롤할 때 다시 쓰기 때문에, 버려진 체크박스가 예전 항목에 묶인 채
 * 남아 다른 줄의 선택을 건드렸다. 연동은 화면이 아니라 모형에 한 번만 걸어야 한다.
 *
 * <p>같은 트리에 두 번 걸지 않도록 뿌리 항목에 표시를 남긴다.
 */
public final class TreeSelectionBinder {

    private TreeSelectionBinder() {
    }

    /**
     * 테이블 선택 화면의 연동.
     *
     * <ul>
     *   <li>스키마를 켜고 끄면 그 아래 테이블이 따라간다.</li>
     *   <li>체크박스가 있는 항목의 선택이 바뀌면 {@code onSelectionChanged} 를 부른다. 크기 합계 갱신에 쓴다.</li>
     * </ul>
     */
    public static void bindTableSelection(TreeItem<TreeAttributeWrapper> root, Runnable onSelectionChanged) {
        if (!markInstalled(root)) {
            return;
        }
        forEachItem(root, item -> {
            TreeAttributeWrapper value = item.getValue();
            if (value == null) {
                return;
            }
            if (value.isSchemaAttr()) {
                List<TreeItem<TreeAttributeWrapper>> tables =
                        TreeItemUtil.findChildrenTreeItemList(item, new ArrayList<>(), TABLE);
                value.selectedProperty().addListener((observable, wasSelected, isNowSelected) ->
                        tables.forEach(table -> setSelected(table.getValue(), isNowSelected)));
            }
            if (onSelectionChanged != null && value.shouldHaveCheckBox()) {
                value.selectedProperty()
                        .addListener((observable, wasSelected, isNowSelected) -> onSelectionChanged.run());
            }
        });
    }

    /**
     * 파일 다운로드할 컬럼 선택 화면의 연동. 묶음 항목(컬럼 묶음)을 켜고 끄면 그 아래 항목이 따라간다.
     */
    public static void bindColumnSelection(TreeItem<TreeAttributeWrapper> root) {
        if (!markInstalled(root)) {
            return;
        }
        forEachItem(root, item -> {
            TreeAttributeWrapper value = item.getValue();
            if (value == null || !value.shouldHaveCheckBox() || item.getChildren().isEmpty()) {
                return;
            }
            value.selectedProperty().addListener((observable, wasSelected, isNowSelected) ->
                    propagateToChildren(item, isNowSelected));
        });
    }

    /** 아래 항목에 선택을 퍼뜨린다. 더 아래로 내려갈지는 항목이 정한다. */
    private static void propagateToChildren(TreeItem<TreeAttributeWrapper> parent, boolean isSelected) {
        for (TreeItem<TreeAttributeWrapper> child : parent.getChildren()) {
            TreeAttributeWrapper childValue = child.getValue();
            if (childValue == null) {
                continue;
            }
            if (childValue.isTransferable()) {
                setSelected(childValue, isSelected);
            }
            if (childValue.shouldPropagate()) {
                propagateToChildren(child, isSelected);
            }
        }
    }

    private static void setSelected(TreeAttributeWrapper value, boolean isSelected) {
        if (value != null) {
            value.setSelected(isSelected);
        }
    }

    /** 아직 걸지 않은 트리이면 표시를 남기고 참을 돌려준다. */
    private static boolean markInstalled(TreeItem<TreeAttributeWrapper> root) {
        if (root == null) {
            return false;
        }
        TreeAttributeWrapper value = root.getValue();
        if (value == null) {
            return true;
        }
        if (value.selectionBindingsInstalled()) {
            return false;
        }
        value.markSelectionBindingsInstalled();
        return true;
    }

    private static void forEachItem(TreeItem<TreeAttributeWrapper> item, Consumer<TreeItem<TreeAttributeWrapper>> action) {
        if (item == null) {
            return;
        }
        action.accept(item);
        for (TreeItem<TreeAttributeWrapper> child : item.getChildren()) {
            forEachItem(child, action);
        }
    }
}
