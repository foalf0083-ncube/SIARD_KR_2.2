// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter;

import ch.admin.bar.siardsuite.framework.ServicesFacade;
import ch.admin.bar.siardsuite.framework.dialogs.DialogCloser;
import ch.admin.bar.siardsuite.framework.errors.Failure;
import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import ch.admin.bar.siardsuite.framework.i18n.keys.I18nKey;
import ch.admin.bar.siardsuite.framework.view.FXMLLoadHelper;
import ch.admin.bar.siardsuite.framework.view.LoadedView;
import ch.admin.bar.siardsuite.ui.common.Icon;
import ch.admin.bar.siardsuite.util.OptionalHelper;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import lombok.val;

import java.util.Optional;

public class ErrorDialogPresenter {

    private static final I18nKey SHOW_DETAILS = I18nKey.of("error.button.details.show");
    private static final I18nKey HIDE_DETAILS = I18nKey.of("error.button.details.hide");

    @FXML
    public Label title;

    @FXML
    public MFXButton closeButton;

    @FXML
    public Text message;

    @FXML
    public TextArea stacktraceTextArea;

    @FXML
    public ImageView iconImageView;

    @FXML
    public Label detailsLabel;

    /**
     * 예외 연쇄에서 사람이 읽을 사유만 뽑는다. 중복은 걸러낸다.
     */
    private static String describeCauses(final Throwable throwable) {
        final StringBuilder sb = new StringBuilder();
        final java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Throwable t = throwable; t != null && seen.size() < 5; t = t.getCause()) {
            final String message = t.getMessage();
            if (message == null || message.isBlank()) continue;
            if (seen.add(message.trim())) {
                if (sb.length() > 0) sb.append(System.lineSeparator());
                sb.append(message.trim());
            }
        }
        return sb.length() > 0 ? sb.toString() : throwable.getClass().getSimpleName();
    }

    public void init(
            final DisplayableText titleText,
            final DisplayableText messageText,
            final Icon icon,
            final Optional<Throwable> optionalThrowable,
            final DialogCloser dialogCloser
    ) {
        detailsLabel.setText(DisplayableText.of(SHOW_DETAILS).getText());
        iconImageView.setImage(icon.toImage());


        title.textProperty().bind(titleText.bindable());
        message.textProperty().bind(messageText.bindable());

        // 상세에는 사유만 싣는다. 스택 트레이스는 내부 구조(패키지·클래스·행번호)를
        // 그대로 드러내고 사용자가 취할 조치를 알려주지도 못한다. 전문은 로그에 남는다.
        OptionalHelper.when(optionalThrowable.map(ErrorDialogPresenter::describeCauses))
                .isPresent(stacktrace -> {
                    detailsLabel.setVisible(true);
                    detailsLabel.setManaged(true);

                    stacktraceTextArea.setText(stacktrace);
                })
                .orElse(() -> {
                    detailsLabel.setVisible(false);
                    detailsLabel.setManaged(false);
                });

        closeButton.setOnAction(event -> dialogCloser.closeDialog());

        detailsLabel.setOnMouseClicked(event -> {
            if (stacktraceTextArea.isVisible()) {
                detailsLabel.setText(DisplayableText.of(SHOW_DETAILS).getText());
                stacktraceTextArea.setVisible(false);
                stacktraceTextArea.setManaged(false);
            } else {
                detailsLabel.setText(DisplayableText.of(HIDE_DETAILS).getText());
                stacktraceTextArea.setVisible(true);
                stacktraceTextArea.setManaged(true);
            }
        });
    }

    public static LoadedView<ErrorDialogPresenter> load(
            final Failure data,
            final ServicesFacade servicesFacade
    ) {
        val loaded = FXMLLoadHelper.<ErrorDialogPresenter>load("fxml/error-dialog.fxml");
        loaded.getController().init(
                data.getTitle(),
                data.getMessage(),
                getIcon(data.getType()),
                data.getThrowable(),
                servicesFacade.dialogs()
        );

        return loaded;
    }

    private static Icon getIcon(final Failure.Type failureType) {
        switch (failureType) {
            case ERROR:
                return Icon.CIRCLE_ERROR;
            case WARNING:
                return Icon.CIRCLE_WARN;
        }

        throw new IllegalArgumentException("Not supported: " + failureType);
    }
}
