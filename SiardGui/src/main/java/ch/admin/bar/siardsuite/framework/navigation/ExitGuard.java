// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.framework.navigation;

import java.util.function.Consumer;

/**
 * 화면을 떠나기 전에 물어봐야 할 것이 있으면 여기에 등록한다.
 *
 * <p>창 닫기처럼 화면 밖에서 일어나는 이탈은 그 화면의 프레젠터를 알지 못한다.
 * 그래서 프레젠터가 스스로 「나를 거쳐 가라」고 등록해 둔다.</p>
 *
 * <p>등록한 것은 {@link Navigator} 가 다른 화면으로 옮길 때 자동으로 지운다.
 * 지우지 않으면 이미 버려진 프레젠터의 낡은 편집 상태를 보고 묻게 된다.</p>
 */
public class ExitGuard {

    private static final Consumer<Runnable> RUN_DIRECTLY = Runnable::run;

    private Consumer<Runnable> guard = RUN_DIRECTLY;

    public void register(final Consumer<Runnable> guard) {
        this.guard = guard == null ? RUN_DIRECTLY : guard;
    }

    public void clear() {
        this.guard = RUN_DIRECTLY;
    }

    /** 등록된 것이 없으면 그냥 실행한다. */
    public void runIfSafe(final Runnable action) {
        guard.accept(action);
    }
}
