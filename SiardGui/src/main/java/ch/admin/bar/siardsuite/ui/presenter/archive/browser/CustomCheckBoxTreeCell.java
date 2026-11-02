// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.archive.browser;

import ch.admin.bar.siardsuite.model.TreeAttributeWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TreeItem;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.util.Callback;

/**
 * <p>{@link CheckBoxTreeCell} 을 상속하는 클래스.</p>
 * 스키마와 테이블 선택 화면의 목록 칸이다. 표시만 맡는다.
 *
 * <p>체크박스를 만들어 항목에 묶고 다시 푸는 일은 상위 클래스가 이미 올바르게 한다. 여기서 체크박스를 따로
 * 만들면 목록 칸이 다시 쓰일 때(스크롤 · 펼침) 버려진 체크박스가 예전 항목에 묶인 채 남아 다른 줄의 선택을
 * 건드린다. 그래서 체크박스가 필요 없는 줄에서 그림을 지우는 일만 한다.
 *
 * <p>선택이 퍼지는 방식(스키마에서 테이블로)과 크기 합계 갱신은 화면이 아니라 모형에 건다.
 * {@link ch.admin.bar.siardsuite.model.TreeSelectionBinder} 참고.
 */
public class CustomCheckBoxTreeCell extends CheckBoxTreeCell<TreeAttributeWrapper> {

    private CustomCheckBoxTreeCell(Callback<TreeItem<TreeAttributeWrapper>, ObservableValue<Boolean>> callback) {
        super(callback);
    }

    public static CustomCheckBoxTreeCell initCheckBoxTree(
            Callback<TreeItem<TreeAttributeWrapper>, ObservableValue<Boolean>> callback) {
        return new CustomCheckBoxTreeCell(callback);
    }

    @Override
    public void updateItem(TreeAttributeWrapper item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        setText(getDisplayText(item));

        if (!hasCheckBox(item)) {
            setGraphic(null);
        }
    }

    private boolean hasCheckBox(TreeAttributeWrapper item) {
        return item.shouldHaveCheckBox() && item.isTransferable(item.getDatabaseAttribute());
    }

    /**
     * 엔티티, 스키마 별로 용량 표기
     *
     * @param item
     * @return
     */
    private String getDisplayText(TreeAttributeWrapper item) {
        String displayName = item.getDisplayName();
        String formattedSize = getFormattedSize(item);
        return formattedSize == null ? displayName : displayName + formattedSize;
    }

    private String getFormattedSize(TreeAttributeWrapper item) {
        String formatted = item.getFormattedSize();
        return formatted == null || formatted.isBlank() ? null : " (" + item.getFormattedSize() + ")";
    }
}
