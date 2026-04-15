package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.core.exception.PostErrorCode;
import com.example.instagramclone.core.exception.PostException;
import com.example.instagramclone.domain.member.application.MemberService;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.post.api.LikeStatusResponse;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostLike;
import com.example.instagramclone.domain.post.domain.PostLikeRepository;
import com.example.instagramclone.domain.post.domain.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * PostLikeService.toggleLike() 단위 테스트.
 *
 * - 게시물 없음 → POST_NOT_FOUND 예외
 * - 아직 좋아요 안 함 → 추가 후 liked=true, count 반환
 * - 이미 좋아요 함 → 삭제 후 liked=false, count 반환
 * - 비정규화: 토글 후 응답 likeCount = post.likeCount (COUNT 쿼리 없음)
 */
@ExtendWith(MockitoExtension.class)
class PostLikeServiceTest {

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private MemberService memberService;

    @InjectMocks
    private PostLikeService postLikeService;

    // ============================================================
    // 테스트 픽스처 (Helper)
    // ============================================================

    private Member buildMockMember(Long id, String username) {
        Member member = Member.builder()
                .username(username)
                .password("encoded_pw")
                .email(username + "@test.com")
                .name("테스트 유저")
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Post buildMockPost(Long id, String content, Member writer) {
        Post post = Post.builder()
                .content(content)
                .writer(writer)
                .build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    // ============================================================
    // toggleLike()
    // ============================================================

    @Nested
    @DisplayName("toggleLike()")
    class ToggleLike {

        @Test
        @DisplayName("실패 - 게시물이 없으면 PostException(POST_NOT_FOUND) 발생")
        void fail_post_not_found() {
            // given: 존재하지 않는 postId
            Long loginMemberId = 1L;
            Long postId = 999L;
            given(postRepository.findByIdWithLock(postId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> postLikeService.toggleLike(loginMemberId, postId))
                    .isInstanceOf(PostException.class)
                    .hasMessage(PostErrorCode.POST_NOT_FOUND.getMessage());

            then(postLikeRepository).shouldHaveNoInteractions();
            then(memberService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("성공 - 아직 좋아요 안 했으면 추가 후 liked=true, likeCount 반환")
        void success_add_like_returns_liked_true() {
            // given
            Long loginMemberId = 1L;
            Long postId = 10L;
            Member member = buildMockMember(loginMemberId, "testuser");
            Post post = buildMockPost(postId, "글 내용", member);

            given(postRepository.findByIdWithLock(postId)).willReturn(Optional.of(post));
            given(memberService.getReferenceById(loginMemberId)).willReturn(member);
            given(postLikeRepository.existsByMemberAndPost(member, post)).willReturn(false);

            // when
            LikeStatusResponse response = postLikeService.toggleLike(loginMemberId, postId);

            // then - 비정규화 +1
            assertThat(response.liked()).isTrue();
            assertThat(response.likeCount()).isEqualTo(1);
            assertThat(post.getLikeCount()).isEqualTo(1);

            ArgumentCaptor<PostLike> captor = ArgumentCaptor.forClass(PostLike.class);
            then(postLikeRepository).should().save(captor.capture());
            assertThat(captor.getValue().getMember()).isSameAs(member);
            assertThat(captor.getValue().getPost()).isSameAs(post);

            then(postLikeRepository).should(never()).deleteByMemberAndPost(any(), any());
            then(postLikeRepository).should(never()).countByPost(any());
        }

        @Test
        @DisplayName("성공 - 이미 좋아요 했으면 삭제 후 liked=false, likeCount 반환")
        void success_remove_like_returns_liked_false() {
            // given: 이미 좋아요한 상태
            Long loginMemberId = 1L;
            Long postId = 10L;
            Member member = buildMockMember(loginMemberId, "testuser");
            Post post = buildMockPost(postId, "글 내용", member);
            ReflectionTestUtils.setField(post, "likeCount", 1);

            given(postRepository.findByIdWithLock(postId)).willReturn(Optional.of(post));
            given(memberService.getReferenceById(loginMemberId)).willReturn(member);
            given(postLikeRepository.existsByMemberAndPost(member, post)).willReturn(true);

            LikeStatusResponse response = postLikeService.toggleLike(loginMemberId, postId);

            assertThat(response.liked()).isFalse();
            assertThat(response.likeCount()).isZero();
            assertThat(post.getLikeCount()).isZero();

            then(postLikeRepository).should().deleteByMemberAndPost(member, post);
            then(postLikeRepository).should(never()).save(any(PostLike.class));
            then(postLikeRepository).should(never()).countByPost(any());
        }

        @Test
        @DisplayName("성공 - 기존 likeCount 6에서 추가 시 응답·엔티티 모두 7")
        void success_likeCount_incremented_from_denormalized_base() {
            Long loginMemberId = 1L;
            Long postId = 10L;
            Member member = buildMockMember(loginMemberId, "testuser");
            Post post = buildMockPost(postId, "글 내용", member);
            ReflectionTestUtils.setField(post, "likeCount", 6);

            given(postRepository.findByIdWithLock(postId)).willReturn(Optional.of(post));
            given(memberService.getReferenceById(loginMemberId)).willReturn(member);
            given(postLikeRepository.existsByMemberAndPost(member, post)).willReturn(false);

            LikeStatusResponse response = postLikeService.toggleLike(loginMemberId, postId);

            assertThat(response.liked()).isTrue();
            assertThat(response.likeCount()).isEqualTo(7);
            assertThat(post.getLikeCount()).isEqualTo(7);
        }
    }
}
