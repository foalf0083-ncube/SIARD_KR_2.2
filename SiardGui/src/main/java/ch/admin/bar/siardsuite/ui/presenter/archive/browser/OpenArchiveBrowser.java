// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter.archive.browser;

import ch.admin.bar.siard2.api.Archive;
import ch.admin.bar.siardsuite.ui.component.ButtonBox;
import ch.admin.bar.siardsuite.framework.dialogs.Dialogs;
import ch.admin.bar.siardsuite.framework.ServicesFacade;
import ch.admin.bar.siardsuite.framework.navigation.Navigator;
import ch.admin.bar.siardsuite.model.Tuple;
import ch.admin.bar.siardsuite.ui.View;
import ch.admin.bar.siardsuite.model.database.SiardArchive;
import ch.admin.bar.siardsuite.service.ArchiveHandler;
import ch.admin.bar.siardsuite.util.OptionalHelper;
import ch.admin.bar.siardsuite.framework.view.LoadedView;
import ch.admin.bar.siardsuite.framework.errors.ErrorHandler;
import ch.admin.bar.siardsuite.framework.i18n.DisplayableText;
import ch.admin.bar.siardsuite.framework.i18n.keys.I18nKey;
import javafx.scene.Node;
import lombok.val;

import static ch.admin.bar.siardsuite.ui.component.ButtonBox.Type.OPEN_PREVIEW;
import static ch.admin.bar.siardsuite.ui.presenter.archive.browser.GenericArchiveBrowserPresenter.*;

public class OpenArchiveBrowser {
    private static final I18nKey TITLE = I18nKey.of("open.siard.archive.preview.title");
    private static final I18nKey TEXT = I18nKey.of("open.siard.archive.preview.text");

    private LoadedView<GenericArchiveBrowserPresenter> loadedView;

    public void init(
            final Archive archive,
            final Dialogs dialogs,
            final Navigator navigator,
            final ErrorHandler errorHandler,
            final ArchiveHandler archiveHandler
    ) {
        val archiveBrowserView = TreeBuilder.builder()
                .siardArchive(new SiardArchive(archive.getFile().getName(), archive, false))
                .readonly(false)
                .columnSelectable(false)
                .build();

        val buttonsBox = new ButtonBox().make(OPEN_PREVIEW);
        // 「재현」도 화면을 떠난다. 검사 없이 나가면 편집이 사라진다.
        buttonsBox.cancel().setOnAction(event -> this.loadedView.getController().runIfNoUnsavedChanges(
                () -> dialogs.open(
                View.RECENT_CONNECTIONS_FOR_UPLOAD,
                optionalRecentDbConnection -> OptionalHelper.when(optionalRecentDbConnection)
                        .isPresent(recentDbConnection -> navigator.navigate(
                                View.UPLOAD_STEPPER_WITH_RECENT_CONNECTION,
                                new Tuple<>(
                                        archive,
                                        recentDbConnection
                                )))
                        .orElse(() -> navigator.navigate(
                                View.UPLOAD_STEPPER,
                                archive))

        )));
        buttonsBox.previous().setOnAction(event -> dialogs.open(
                View.EXPORT_SELECT_TABLES,
                archive
        ));
        buttonsBox.next().setOnAction(event -> {
            // 저장하지 않은 편집이 있으면 먼저 확인한다.
            // 이 경로에는 검사가 없어 편집 내용이 경고 없이 사라졌다.
            final GenericArchiveBrowserPresenter presenter = this.loadedView.getController();
            presenter.runIfNoUnsavedChanges(() -> {
                archiveHandler.save(archive, archive.getFile());
                navigator.navigate(View.START);
            });
        });

        this.loadedView = GenericArchiveBrowserPresenter.load(
                dialogs,
                errorHandler,
                navigator,
                DisplayableText.of(TITLE),
                DisplayableText.of(TEXT),
                buttonsBox,
                archiveBrowserView.customCreateRootItem(),
                archiveBrowserView,
                ArchiveStep.OPEN_ARCHIVE);
    }

    public Node getView() {
        //폼 가장자리의 Schema Total Size: 0 비활성화하기 - 그냥 SIARD File 이라고 출력
        GenericArchiveBrowserPresenter presenter = loadedView.getController();
        presenter.removeTableSize();
        return loadedView.getNode();
    }

    public static LoadedView<OpenArchiveBrowser> load(
            final Archive data,
            final ServicesFacade servicesFacade
    ) {
        val browser = new OpenArchiveBrowser();
        browser.init(
                data,
                servicesFacade.dialogs(),
                servicesFacade.navigator(),
                servicesFacade.errorHandler(),
                servicesFacade.getService(ArchiveHandler.class));
        return new LoadedView<>(browser::getView, browser);
    }
}
