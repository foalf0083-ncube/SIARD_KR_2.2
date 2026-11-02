// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.archive.browser;

import ch.admin.bar.siard2.cmd.utils.ByteFormatter;
import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import ch.admin.bar.siardsuite.framework.i18n.keys.I18nKey;
import ch.admin.bar.siardsuite.model.TreeAttributeWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;

/**
 * 선택한 대상의 크기 합계. 트리에서 값을 읽어 라벨에 적고, 다음 화면이 쓸 수 있도록 마지막 값을 남긴다.
 */
public final class SelectedSizeSummary {

    private static final I18nKey NO_SELECTION = I18nKey.of("browser.size.noSelection");
    private static final I18nKey UNAVAILABLE = I18nKey.of("browser.size.unavailable");

    private static long selectedTotalSize = 0L;

    private SelectedSizeSummary() {
    }

    /** 마지막으로 계산한 합계. 다음 화면(파일 다운로드)이 표기에 쓴다. */
    public static long getSelectedTotalSize() {
        return selectedTotalSize;
    }

    /** 트리를 훑어 합계를 다시 세고 라벨에 적는다. */
    public static void refresh(TreeItem<TreeAttributeWrapper> root, Label label) {
        long totalSize = calculateTotalSize(root);
        selectedTotalSize = totalSize;
        if (label != null) {
            updateLabel(label, totalSize, countSelectedTransferable(root));
        }
    }

    /** 선택된 이관 대상 개수. 크기 0 이 '미산정'인지 '없음'인지 가르는 데 쓴다. */
    private static int countSelectedTransferable(TreeItem<TreeAttributeWrapper> root) {
        if (root == null || root.getChildren().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TreeItem<TreeAttributeWrapper> child : root.getChildren()) {
            TreeAttributeWrapper value = child.getValue();
            if (value != null && value.isTransferable() && value.isSelected()) {
                count++;
            }
            count += countSelectedTransferable(child);
        }
        return count;
    }

    private static long calculateTotalSize(TreeItem<TreeAttributeWrapper> root) {
        if (root == null || root.getChildren().isEmpty()) {
            return 0L;
        }
        long totalSize = 0L;
        for (TreeItem<TreeAttributeWrapper> child : root.getChildren()) {
            TreeAttributeWrapper value = child.getValue();
            if (value != null && value.isTransferable() && value.isSelected()) {
                totalSize += value.getSize();
            }
            totalSize += calculateTotalSize(child);
        }
        return totalSize;
    }

    /**
     * 합계 0 은 세 가지 뜻이 될 수 있어 그대로 '0 Bytes' 로 두면 안 된다.
     *
     * <ul>
     *   <li>아무것도 고르지 않았다 → 크기를 말할 대상이 없다</li>
     *   <li>골랐는데 크기가 산정되지 않았다 → CUBRID 처럼 카탈로그가 크기를 안 알려주는 경우</li>
     *   <li>골랐고 크기가 정말 0 이다 → '0 Bytes' 가 맞다</li>
     * </ul>
     */
    private static void updateLabel(Label label, long totalSize, int selectedCount) {
        if (selectedCount == 0) {
            label.setText(DisplayableText.of(NO_SELECTION).getText());
            return;
        }
        if (totalSize == 0L) {
            label.setText(DisplayableText.of(UNAVAILABLE).getText());
            return;
        }
        label.setText(ByteFormatter.convertToBestFitUnit(totalSize));
    }
}
