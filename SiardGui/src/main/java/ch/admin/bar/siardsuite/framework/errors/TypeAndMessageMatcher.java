// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.framework.errors;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.function.Predicate;

@Value
@Builder
public class TypeAndMessageMatcher implements Predicate<Throwable> {
    @NonNull Class expectedExceptionType;
    @NonNull String expectedTextFragment;

    @Override
    public boolean test(Throwable throwable) {
        // equals 대신 isInstance 를 쓴다. 이유는 TypeMatcher 와 같다 —
        // 드라이버가 던지는 하위 예외를 못 잡아 ACCESS_DENIED · OBJECT_EXISTS 가 죽어 있었다.
        return expectedExceptionType.isInstance(throwable) &&
                throwable.getMessage() != null &&
                throwable.getMessage().contains(expectedTextFragment);
    }
}