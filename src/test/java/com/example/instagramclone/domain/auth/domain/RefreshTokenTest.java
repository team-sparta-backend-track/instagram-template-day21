package com.example.instagramclone.domain.auth.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RefreshToken 엔티티 단위 테스트
 *
 * [목적]
 * 엔티티의 비즈니스 메서드(updateToken)가 올바르게 동작하는지 검증합니다.
 * Spring Context 없이 순수 Java로 실행하므로 매우 빠릅니다.
 * updateToken() 메서드의 동작이 변경될 경우 RTR 정책 전체가 무너지므로 반드시 보호합니다.
 */
class RefreshTokenTest {

    @Test
    @DisplayName("빌더로 생성 시 memberId, token 필드가 정확하게 설정된다")
    void builder_creates_entity_with_correct_fields() {
        // given & when
        RefreshToken token = RefreshToken.builder()
                .memberId(42L)
                .token("initial.refresh.token")
                .build();

        // then
        assertThat(token.getMemberId()).isEqualTo(42L);
        assertThat(token.getToken()).isEqualTo("initial.refresh.token");
        assertThat(token.getId()).isNull(); // DB에 저장되기 전이므로 id는 null
    }

    @Test
    @DisplayName("updateToken() 호출 시 토큰 값이 새 값으로 교체된다 (RTR 핵심 동작)")
    void updateToken_changes_token_value() {
        // given
        RefreshToken token = RefreshToken.builder()
                .memberId(1L)
                .token("old.refresh.token")
                .build();
        assertThat(token.getToken()).isEqualTo("old.refresh.token"); // 변경 전 확인

        // when
        token.updateToken("new.refresh.token");

        // then
        assertThat(token.getToken()).isEqualTo("new.refresh.token");
    }

    @Test
    @DisplayName("updateToken() 연속 호출 시 항상 마지막 토큰으로 교체된다")
    void updateToken_multiple_times_keeps_last_value() {
        // given
        RefreshToken token = RefreshToken.builder()
                .memberId(1L)
                .token("first.token")
                .build();

        // when
        token.updateToken("second.token");
        token.updateToken("third.token");

        // then
        assertThat(token.getToken()).isEqualTo("third.token");
    }

    @Test
    @DisplayName("memberId는 updateToken() 이후에도 변경되지 않는다 (불변 필드 보호)")
    void memberId_remains_unchanged_after_updateToken() {
        // given
        RefreshToken token = RefreshToken.builder()
                .memberId(7L)
                .token("some.token")
                .build();

        // when
        token.updateToken("another.token");

        // then
        assertThat(token.getMemberId()).isEqualTo(7L);
    }
}
