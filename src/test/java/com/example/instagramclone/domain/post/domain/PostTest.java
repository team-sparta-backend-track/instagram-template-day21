package com.example.instagramclone.domain.post.domain;

import com.example.instagramclone.domain.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Post 엔티티 단위 테스트
 *
 * [목적]
 * Spring Context 없이 순수 Java로 Post 엔티티의 빌더를 검증합니다.
 *
 * [보호해야 할 불변 규칙]
 * 1. content는 nullable이며 TEXT 타입으로 저장된다.
 * 2. writer는 Post 생성 시 반드시 지정되어야 한다 (optional = false).
 * 3. id는 DB 저장 전까지 null이다.
 */
class PostTest {

    private Member buildMember(String username) {
        return Member.builder()
                .username(username)
                .password("encoded_pw")
                .email(username + "@test.com")
                .name("테스터")
                .build();
    }

    // ============================================================
    // Builder
    // ============================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("content와 writer가 정확히 설정된다")
        void builder_sets_content_and_writer() {
            Member writer = buildMember("postuser");
            Post post = Post.builder()
                    .content("테스트 게시물 내용")
                    .writer(writer)
                    .build();

            assertThat(post.getContent()).isEqualTo("테스트 게시물 내용");
            assertThat(post.getWriter()).isSameAs(writer);
        }

        @Test
        @DisplayName("DB 저장 전 id는 null이다")
        void id_is_null_before_save() {
            Post post = Post.builder()
                    .content("내용")
                    .writer(buildMember("user"))
                    .build();

            assertThat(post.getId()).isNull();
        }

        @Test
        @DisplayName("content는 null로 생성 가능하다 (nullable)")
        void builder_with_null_content() {
            Post post = Post.builder()
                    .content(null)
                    .writer(buildMember("user"))
                    .build();

            assertThat(post.getContent()).isNull();
        }

        @Test
        @DisplayName("긴 텍스트 content도 설정 가능하다 (TEXT 타입)")
        void builder_with_long_content() {
            String longContent = "가".repeat(500);
            Post post = Post.builder()
                    .content(longContent)
                    .writer(buildMember("user"))
                    .build();

            assertThat(post.getContent()).isEqualTo(longContent);
            assertThat(post.getContent()).hasSize(500);
        }

        @Test
        @DisplayName("서로 다른 Post 인스턴스는 참조 동등성을 공유하지 않는다")
        void two_different_post_instances_are_not_same() {
            Member writer = buildMember("user");
            Post post1 = Post.builder().content("내용").writer(writer).build();
            Post post2 = Post.builder().content("내용").writer(writer).build();

            assertThat(post1).isNotSameAs(post2);
        }
    }
}
