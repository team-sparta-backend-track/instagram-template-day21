package com.example.instagramclone.domain.post.domain;

import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.post.api.ProfilePostResponse;
import com.example.instagramclone.domain.post.infrastructure.PostGridQueryHelper;
import com.example.instagramclone.infrastructure.persistence.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostRepository / PostImageRepository 통합 테스트 (@DataJpaTest - H2 인메모리 DB)
 *
 * [테스트 범위]
 * - PostRepository.save(): 기본 저장 및 writer 연관관계
 * - PostRepository.findAllByWriterId(): 특정 작성자 게시글 조회, 최신순 페이징
 * - PostImageRepository.findByPostIn(): IN 쿼리 그룹 조회, 다른 게시물 이미지 미포함
 * - PostImageRepository: 기본 저장 및 연관관계
 */
@DataJpaTest
@Import({QueryDslConfig.class, PostGridQueryHelper.class})
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Member savedMember;

    @BeforeEach
    void setUp() {
        savedMember = memberRepository.save(Member.builder()
                .username("writer")
                .password("encoded_pw")
                .email("writer@test.com")
                .name("작성자")
                .build());
    }

    // ============================================================
    // PostRepository.save()
    // ============================================================

    @Nested
    @DisplayName("PostRepository.save()")
    class SavePost {

        @Test
        @DisplayName("게시물 저장 성공 - ID 생성, content 및 writer 확인")
        void save_post_with_writer() {
            Post post = Post.builder()
                    .content("테스트 게시물")
                    .writer(savedMember)
                    .build();

            Post saved = postRepository.save(post);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getContent()).isEqualTo("테스트 게시물");
            assertThat(saved.getWriter().getId()).isEqualTo(savedMember.getId());
        }

        @Test
        @DisplayName("content가 null인 게시물도 저장 가능하다 (nullable)")
        void save_post_with_null_content() {
            Post post = Post.builder()
                    .content(null)
                    .writer(savedMember)
                    .build();

            Post saved = postRepository.save(post);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getContent()).isNull();
        }
    }

    // ============================================================
    // PostRepository.findAllByWriterId()
    // ============================================================

    @Nested
    @DisplayName("PostRepository.findAllByWriterId()")
    class FindAllByWriterId {

        @Test
        @DisplayName("게시물이 없으면 빈 Slice 반환")
        void returns_empty_slice_when_no_posts() {
            Pageable pageable = PageRequest.of(0, 10);
            Slice<ProfilePostResponse> result = postRepository.findAllByWriterId(savedMember.getId(), pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("특정 writerId로 조회하면 그 작성자의 글만 최신순으로 반환")
        void returns_only_posts_of_target_writer_in_desc_order() {
            Member otherMember = memberRepository.save(Member.builder()
                    .username("other")
                    .password("encoded_pw")
                    .email("other@test.com")
                    .name("다른 작성자")
                    .build());

            Post oldPost = postRepository.save(Post.builder().content("내 첫 글").writer(savedMember).build());
            Post newPost = postRepository.save(Post.builder().content("내 최신 글").writer(savedMember).build());
            postRepository.save(Post.builder().content("남의 글").writer(otherMember).build());
            entityManager.flush();
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 10);
            Slice<ProfilePostResponse> result = postRepository.findAllByWriterId(savedMember.getId(), pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent())
                    .extracting(ProfilePostResponse::postId)
                    .containsExactly(newPost.getId(), oldPost.getId());
        }

        @Test
        @DisplayName("Pageable size 이상 게시물이 있으면 hasNext가 true")
        void hasNext_true_when_more_pages_exist() {
            postRepository.save(Post.builder().content("글1").writer(savedMember).build());
            postRepository.save(Post.builder().content("글2").writer(savedMember).build());
            postRepository.save(Post.builder().content("글3").writer(savedMember).build());
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 2);
            Slice<ProfilePostResponse> result = postRepository.findAllByWriterId(savedMember.getId(), pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("마지막 페이지에서는 hasNext가 false")
        void hasNext_false_on_last_page() {
            postRepository.save(Post.builder().content("유일한 글").writer(savedMember).build());
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 10);
            Slice<ProfilePostResponse> result = postRepository.findAllByWriterId(savedMember.getId(), pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.hasNext()).isFalse();
        }
    }

    // ============================================================
    // PostImageRepository
    // ============================================================

    @Nested
    @DisplayName("PostImageRepository.save()")
    class SavePostImage {

        @Test
        @DisplayName("PostImage 저장 성공 - imageUrl, imgOrder, post 참조 확인")
        void save_postImage_with_post_reference() {
            Post post = postRepository.save(Post.builder().content("글").writer(savedMember).build());

            PostImage image = PostImage.builder()
                    .post(post)
                    .imageUrl("/img/test.jpg")
                    .imgOrder(1)
                    .build();

            PostImage saved = postImageRepository.save(image);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getImageUrl()).isEqualTo("/img/test.jpg");
            assertThat(saved.getImgOrder()).isEqualTo(1);
            assertThat(saved.getPost().getId()).isEqualTo(post.getId());
        }
    }

    // ============================================================
    // PostImageRepository.findByPostIn()
    // ============================================================

    @Nested
    @DisplayName("PostImageRepository.findByPostIn()")
    class FindByPostIn {

        @Test
        @DisplayName("여러 게시물에 속한 이미지를 IN 쿼리 한 번으로 모두 조회한다")
        void returns_all_images_for_given_posts() {
            Post post1 = postRepository.save(Post.builder().content("글1").writer(savedMember).build());
            Post post2 = postRepository.save(Post.builder().content("글2").writer(savedMember).build());

            postImageRepository.save(PostImage.builder().post(post1).imageUrl("/img/p1a.jpg").imgOrder(1).build());
            postImageRepository.save(PostImage.builder().post(post1).imageUrl("/img/p1b.jpg").imgOrder(2).build());
            postImageRepository.save(PostImage.builder().post(post2).imageUrl("/img/p2a.jpg").imgOrder(1).build());

            List<PostImage> result = postImageRepository.findByPostIn(List.of(post1, post2));

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("이미지가 없는 게시물에 대해서는 빈 리스트 반환")
        void returns_empty_when_posts_have_no_images() {
            Post post = postRepository.save(Post.builder().content("이미지 없는 글").writer(savedMember).build());

            List<PostImage> result = postImageRepository.findByPostIn(List.of(post));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("조회 대상 게시물에 속하지 않은 이미지는 포함되지 않는다")
        void does_not_return_images_from_other_posts() {
            Post targetPost = postRepository.save(Post.builder().content("대상 글").writer(savedMember).build());
            Post otherPost = postRepository.save(Post.builder().content("다른 글").writer(savedMember).build());

            postImageRepository.save(PostImage.builder().post(targetPost).imageUrl("/img/target.jpg").imgOrder(1).build());
            postImageRepository.save(PostImage.builder().post(otherPost).imageUrl("/img/other.jpg").imgOrder(1).build());

            List<PostImage> result = postImageRepository.findByPostIn(List.of(targetPost));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getImageUrl()).isEqualTo("/img/target.jpg");
        }

        @Test
        @DisplayName("빈 리스트로 조회하면 빈 리스트 반환")
        void returns_empty_for_empty_post_list() {
            Post post = postRepository.save(Post.builder().content("글").writer(savedMember).build());
            postImageRepository.save(PostImage.builder().post(post).imageUrl("/img/img.jpg").imgOrder(1).build());

            List<PostImage> result = postImageRepository.findByPostIn(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("게시물의 이미지 여러 장이 imgOrder와 함께 정확히 조회된다")
        void returns_images_with_correct_imgOrder() {
            Post post = postRepository.save(Post.builder().content("글").writer(savedMember).build());

            postImageRepository.save(PostImage.builder().post(post).imageUrl("/img/first.jpg").imgOrder(1).build());
            postImageRepository.save(PostImage.builder().post(post).imageUrl("/img/second.jpg").imgOrder(2).build());
            postImageRepository.save(PostImage.builder().post(post).imageUrl("/img/third.jpg").imgOrder(3).build());

            List<PostImage> result = postImageRepository.findByPostIn(List.of(post));

            assertThat(result).hasSize(3);
            assertThat(result).extracting(PostImage::getImgOrder).containsExactlyInAnyOrder(1, 2, 3);
        }
    }
}
