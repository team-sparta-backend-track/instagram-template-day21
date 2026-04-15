package com.example.instagramclone.domain.post.domain;

import com.example.instagramclone.domain.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostImage 엔티티 단위 테스트
 *
 * [목적]
 * Spring Context 없이 순수 Java로 PostImage 엔티티의 빌더를 검증합니다.
 *
 * [보호해야 할 불변 규칙]
 * 1. imgOrder는 1부터 시작하며 게시물 내 이미지 순서를 나타낸다.
 * 2. post 참조는 생성 시 반드시 지정되어야 한다 (optional = false).
 * 3. id는 DB 저장 전까지 null이다.
 */
class PostImageTest {

    private Member buildMember(String username) {
        return Member.builder()
                .username(username)
                .password("encoded_pw")
                .email(username + "@test.com")
                .name("테스터")
                .build();
    }

    private Post buildPost(String content) {
        return Post.builder()
                .content(content)
                .writer(buildMember("writer"))
                .build();
    }

    // ============================================================
    // Builder
    // ============================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("imageUrl, imgOrder, post 참조가 정확히 설정된다")
        void builder_sets_all_fields() {
            Post post = buildPost("게시물");
            PostImage image = PostImage.builder()
                    .imageUrl("/img/test.jpg")
                    .imgOrder(1)
                    .post(post)
                    .build();

            assertThat(image.getImageUrl()).isEqualTo("/img/test.jpg");
            assertThat(image.getImgOrder()).isEqualTo(1);
            assertThat(image.getPost()).isSameAs(post);
        }

        @Test
        @DisplayName("DB 저장 전 id는 null이다")
        void id_is_null_before_save() {
            PostImage image = PostImage.builder()
                    .imageUrl("/img/img.jpg")
                    .imgOrder(1)
                    .post(buildPost("글"))
                    .build();

            assertThat(image.getId()).isNull();
        }

        @Test
        @DisplayName("같은 Post에 여러 PostImage를 각기 다른 imgOrder로 생성할 수 있다")
        void multiple_images_for_same_post_with_different_orders() {
            Post post = buildPost("이미지 여러 장 게시물");

            PostImage image1 = PostImage.builder().imageUrl("/img/1.jpg").imgOrder(1).post(post).build();
            PostImage image2 = PostImage.builder().imageUrl("/img/2.jpg").imgOrder(2).post(post).build();
            PostImage image3 = PostImage.builder().imageUrl("/img/3.jpg").imgOrder(3).post(post).build();

            assertThat(image1.getImgOrder()).isEqualTo(1);
            assertThat(image2.getImgOrder()).isEqualTo(2);
            assertThat(image3.getImgOrder()).isEqualTo(3);
            assertThat(image1.getPost()).isSameAs(image2.getPost());
            assertThat(image1.getPost()).isSameAs(image3.getPost());
        }

        @Test
        @DisplayName("서로 다른 Post의 이미지는 각각 별개의 post 참조를 가진다")
        void images_from_different_posts_have_different_references() {
            Post post1 = buildPost("첫 번째 글");
            Post post2 = buildPost("두 번째 글");

            PostImage imageOfPost1 = PostImage.builder().imageUrl("/img/a.jpg").imgOrder(1).post(post1).build();
            PostImage imageOfPost2 = PostImage.builder().imageUrl("/img/b.jpg").imgOrder(1).post(post2).build();

            assertThat(imageOfPost1.getPost()).isNotSameAs(imageOfPost2.getPost());
        }
    }
}
