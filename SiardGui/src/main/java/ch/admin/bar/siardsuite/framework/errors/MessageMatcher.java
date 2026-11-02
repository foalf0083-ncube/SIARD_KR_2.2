// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.framework.errors;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.function.Predicate;

/**
 * 예외 문구만 보고 판정한다.
 *
 * <p>{@link TypeAndMessageMatcher} 는 예외 타입이 정확히 같아야 한다. 그런데 드라이버 예외는
 * RuntimeException 등으로 감싸여 올라오는 경우가 많아 타입으로는 잡히지 않는다.
 * 또 같은 사유라도 데이터베이스마다 문구가 다르다(비밀번호 오류만 해도 다섯 가지다).
 * 그래서 타입을 묻지 않고 문구 조각 여러 개 중 하나라도 걸리면 참으로 본다.</p>
 */
@Value
@Builder
public class MessageMatcher implements Predicate<Throwable> {

    @NonNull @Singular List<String> textFragments;

    @Override
    public boolean test(final Throwable throwable) {
        final String message = throwable.getMessage();
        if (message == null) return false;
        final String lower = message.toLowerCase();
        return textFragments.stream()
                .anyMatch(fragment -> lower.contains(fragment.toLowerCase()));
    }
}
