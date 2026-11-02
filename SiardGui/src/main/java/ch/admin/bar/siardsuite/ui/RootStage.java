// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui;

import ch.admin.bar.siardsuite.framework.dialogs.DialogDisplay;
import ch.admin.bar.siardsuite.framework.view.ViewDisplay;
import ch.admin.bar.siardsuite.framework.ServicesFacade;
import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import ch.admin.bar.siardsuite.framework.i18n.keys.I18nKeyArg;
import ch.admin.bar.siardsuite.service.FilesService;
import ch.admin.bar.siardsuite.service.InstallationService;
import ch.admin.bar.siardsuite.service.LogService;
import ch.admin.bar.siardsuite.ui.common.Icon;
import ch.admin.bar.siardsuite.ui.presenter.DialogPresenter;
import ch.admin.bar.siardsuite.ui.presenter.RootPresenter;
import ch.enterag.utils.ProgramInfo;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
public class RootStage extends Stage implements ViewDisplay, DialogDisplay {

    private static final I18nKeyArg<String> WINDOW_TITLE = I18nKeyArg.of("window.title");
    private static ServicesFacade servicesFacade;

    private final BorderPane rootPane;
    private final BorderPane dialogPane;

    /**
     * 실제 종료. Platform.exit() 이 자바FX 스레드를 내리면서 이어지는 문장이 실행되지
     * 않을 수 있어, 프로세스 종료를 먼저 예약해 둔다. 예약하지 않으면 창만 사라지고
     * JVM 이 남는다(실측).
     */
    private static void shutdown() {
        Runtime.getRuntime().halt(0);
    }

    public RootStage() {
        setOnCloseRequest(t -> {
            // 저장하지 않은 편집이 있으면 먼저 묻는다. 창 닫기 X 와 주메뉴 「종료」가 모두 여기로 온다.
            //
            // 일단 이 닫기 요청은 삼킨다. 물어볼 것이 없으면 곧바로 아래에서 다시 닫는다.
            // 삼키지 않으면 대화상자를 띄우는 사이에 창이 먼저 닫혀 버린다.
            t.consume();
            RootStage.servicesFacade.navigator().getExitGuard().runIfSafe(RootStage::shutdown);
        });
        titleProperty().bind(DisplayableText.of(WINDOW_TITLE, ProgramInfo.getProgramInfo().getVersion()).bindable());

        val servicesFacade = new ServicesFacadeBuilder().build(this);
        RootStage.servicesFacade = servicesFacade;

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> servicesFacade.errorHandler().handle(ex));

        rootPane = RootPresenter.load(
                        servicesFacade.dialogs(),
                        servicesFacade.getService(InstallationService.class),
                        servicesFacade.getService(FilesService.class),
                        servicesFacade.getService(LogService.class),
                        servicesFacade.errorHandler(),
                        () -> fireEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSE_REQUEST))
                )
                .getNode();

        dialogPane = DialogPresenter.load()
                .getNode();

        dialogPane.setVisible(false);

        // load start view
        servicesFacade.navigator()
                .navigate(View.START);

        // set overall stack pane
        StackPane stackPane = new StackPane(rootPane, dialogPane);

        Scene scene = new Scene(stackPane);
        scene.getRoot().getStyleClass().add("root");
        scene.setFill(null);

        this.setMaximized(true);
        this.initStyle(StageStyle.DECORATED);
        //this.getIcons().add(Icon.ARCHIVE_RED.toImage());
        this.getIcons().add(Icon.ARCHIVE_LOGO.toImage());
        this.setScene(scene);
        this.show();
    }

    @Override
    public void displayView(final Node node) {
        rootPane.setCenter(node);
    }

    @Override
    public void displayDialog(final Node node) {
        dialogPane.setCenter(node);
        dialogPane.setVisible(true);
    }

    @Override
    public void closeDialog() {
        dialogPane.setVisible(false);
    }
    
    public static ServicesFacade getServicesFacade() {
        return servicesFacade;
    }
}
