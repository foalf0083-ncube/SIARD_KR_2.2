// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.archive.browser;

import ch.admin.bar.siardsuite.model.TreeAttributeWrapper;
import ch.admin.bar.siardsuite.ui.presenter.archive.browser.forms.FileCopyFormData;
import ch.admin.bar.siardsuite.ui.presenter.archive.browser.forms.SftpConnectionFormData;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;

/**
 * <p>{@link CheckBoxTreeCell} 을 상속하는 클래스.</p>
 * 파일 다운로드할 컬럼 선택 화면의 목록 칸이다. 표시와 두 번 눌렀을 때의 창 띄우기만 맡는다.
 *
 * <p>체크박스를 만들어 항목에 묶고 다시 푸는 일은 상위 클래스가 이미 올바르게 한다. 여기서 체크박스를 따로
 * 만들면 목록 칸이 다시 쓰일 때 버려진 체크박스가 예전 항목에 묶인 채 남아 다른 테이블의 컬럼까지 켜고 껐다.
 * 선택이 아래로 퍼지는 일은 모형에 건다({@link ch.admin.bar.siardsuite.model.TreeSelectionBinder}).
 */
public class ColumnCheckBoxTreeCell extends CheckBoxTreeCell<TreeAttributeWrapper> {

    private ColumnCheckBoxTreeCell(Callback<TreeItem<TreeAttributeWrapper>, ObservableValue<Boolean>> callback) {
        super(callback);
    }

    public static ColumnCheckBoxTreeCell initCheckBoxTree(
            Callback<TreeItem<TreeAttributeWrapper>, ObservableValue<Boolean>> callback) {
        return new ColumnCheckBoxTreeCell(callback);
    }

    @Override
    public void updateItem(TreeAttributeWrapper item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setOnMouseClicked(null);
            return;
        }

        setText(getDisplayText(item));

        // 테이블을 두 번 누르면 전송 설정 창을 띄운다. 다른 줄에 다시 쓰일 때 남지 않도록 매번 다시 건다.
        if (item.isTableForSftpConnection()) {
            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !isEmpty()) {
                    showPopupForm(item);
                }
            });
        } else {
            setOnMouseClicked(null);
        }

        if (!hasCheckBox(item)) {
            setGraphic(null);
        }
    }

    private boolean hasCheckBox(TreeAttributeWrapper item) {
        return item.shouldHaveCheckBox() && item.isTransferable(item.getDatabaseAttribute());
    }

    /**
     * SFTP, file Copy 탭 표기
     *
     * @param item
     */
    private void showPopupForm(TreeAttributeWrapper item) {
        TabPane tabPane = new TabPane();

        Tab sftpConnectionTab = new SftpConnectionFormData(item).getSftpConnectionTab();
        Tab fileCopyTab = new FileCopyFormData(item).getFileCopyTab();

        tabPane.getTabs().addAll(sftpConnectionTab, fileCopyTab);

        VBox mainLayout = new VBox(tabPane);
        Scene scene = new Scene(mainLayout, 800, 600);

        Stage popupStage = new Stage();
        popupStage.setScene(scene);
        popupStage.showAndWait();
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
