// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.framework.errors;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.function.Predicate;

@Value
@Builder
public class TypeMatcher implements Predicate<Throwable> {
    @NonNull Class expectedExceptionType;

    @Override
    public boolean test(Throwable throwable) {
        // 정확히 같은 클래스만 보면 하위형을 놓친다. 드라이버는 같은 사유라도 저마다
        // 하위 예외를 던지므로(SQLServerException · SQLInvalidAuthorizationSpecException 등)
        // equals 로는 하나도 걸리지 않아 분류가 통째로 죽어 있었다.
        return expectedExceptionType.isInstance(throwable);
    }
}
