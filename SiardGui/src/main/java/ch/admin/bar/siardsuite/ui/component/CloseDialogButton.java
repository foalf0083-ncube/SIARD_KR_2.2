// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.component;

import ch.admin.bar.siardsuite.util.I18n;
import ch.admin.bar.siardsuite.framework.dialogs.DialogCloser;
import io.github.palexdev.materialfx.controls.MFXButton;

// understands a button that can close a dialog...
public class CloseDialogButton extends MFXButton {
    public CloseDialogButton(DialogCloser dialogCloser) {
        this(dialogCloser, "button.cancel");
    }

    /**
     * 되돌릴 것이 없는 대화상자는 「취소」가 아니라 「닫기」로 부른다.
     */
    public CloseDialogButton(DialogCloser dialogCloser, String labelKey) {
        I18n.bind(this.textProperty(), labelKey);
        this.getStyleClass().setAll("button", "secondary");
        this.setManaged(true);
        this.setOnAction(event -> dialogCloser.closeDialog());
    }
}
