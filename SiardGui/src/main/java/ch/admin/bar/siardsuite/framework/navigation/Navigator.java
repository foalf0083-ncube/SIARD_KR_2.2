// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.framework.navigation;

import ch.admin.bar.siardsuite.framework.ServicesFacade;
import ch.admin.bar.siardsuite.framework.view.ViewDisplay;
import ch.admin.bar.siardsuite.framework.hooks.HooksCaller;
import ch.admin.bar.siardsuite.framework.view.LoadedView;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Navigator class for controlling navigation within the application.
 */
@Slf4j
@RequiredArgsConstructor
public class Navigator {

    private final ViewDisplay viewDisplay;
    private final ServicesFacade servicesFacade;

    private final HooksCaller hooksCaller = new HooksCaller();

    /** 화면 밖에서 일어나는 이탈(창 닫기 등)이 거쳐 갈 자리. {@link ExitGuard} 참고. */
    @Getter
    private final ExitGuard exitGuard = new ExitGuard();

    public void navigate(SimpleNavigationTarget target) {
        // 앞 화면이 걸어 둔 검사는 더 이상 유효하지 않다. 지우지 않으면 버려진
        // 프레젠터의 낡은 편집 상태를 보고 묻게 된다.
        // 반드시 새 화면을 만들기 '전'에 지운다 — 뒤에 지우면 새 화면이 방금 등록한 것까지 지운다.
        exitGuard.clear();
        val nextView = target.getViewSupplier().apply(servicesFacade);

        log.info("Navigate {}->{} without data",
                hooksCaller.getPreviouslyLoadedView()
                        .map(loadedView -> loadedView.getController().getClass().getSimpleName())
                        .orElse("n/a"),
                nextView.getController().getClass().getSimpleName());

        navigate(nextView);
    }

    public <T> void navigate(NavigationTarget<T> target, T data) {
        exitGuard.clear();
        val nextView = target.getViewSupplier().apply(data, servicesFacade);

        log.info("Navigate {}->{} with data {}",
                hooksCaller.getPreviouslyLoadedView()
                        .map(loadedView -> loadedView.getController().getClass().getSimpleName())
                        .orElse(""),
                nextView.getController().getClass().getSimpleName(),
                data);

        navigate(nextView);
    }

    private void navigate(final LoadedView loadedView) {
        viewDisplay.displayView(hooksCaller.nextView(loadedView).getNode());
    }
}
