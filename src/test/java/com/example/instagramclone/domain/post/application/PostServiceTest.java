package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.core.exception.PostErrorCode;
import com.example.instagramclone.core.exception.PostException;
import com.example.instagramclone.core.util.FileStore;
import com.example.instagramclone.domain.hashtag.application.HashtagService;
import com.example.instagramclone.domain.comment.domain.CommentRepository;
import com.example.instagramclone.domain.member.application.MemberService;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.post.api.PostDetailResponse;
import com.example.instagramclone.domain.post.api.PostCreateRequest;
import com.example.instagramclone.domain.post.api.PostImageResponse;
import com.example.instagramclone.domain.post.api.PostResponse;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostLikeRepository;
import com.example.instagramclone.domain.post.domain.PostImage;
import com.example.instagramclone.domain.post.domain.PostImageRepository;
import com.example.instagramclone.domain.post.domain.PostRepository;
import com.example.instagramclone.domain.post.infrastructure.PostFeedRow;
import com.example.instagramclone.domain.post.infrastructure.PostMapper;
import com.example.instagramclone.domain.post.infrastructure.PrevNextPostIds;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * PostService 단위 테스트
 *
 * [테스트 범위]
 * - create(): 이미지 유무, imgOrder 순서 검증, getReferenceById 위임 검증, IOException 래핑
 * - getFeed(): QueryDSL findFeedWithLiked, 빈 피드, 이미지 그룹핑, liked
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @InjectMocks
    private PostService postService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostImageRepository postImageRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private FileStore fileStore;

    @Mock
    private HashtagService hashtagService;

    /**
     * PostMapper는 default 메서드(toResponse, toProfilePostResponse)와
     * MapStruct가 생성한 구현(toImageResponses)을 함께 사용하므로,
     * 단순 Mock이 아니라 실제 구현체를 Spy로 주입한다.
     */
    @Spy
    private PostMapper postMapper = Mappers.getMapper(PostMapper.class);

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
    // create()
    // ============================================================

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("성공 - 이미지 없이(null) 게시물 생성, PostImage 저장 미호출")
        void success_without_images_null() throws IOException {
            PostCreateRequest request = new PostCreateRequest("게시물 내용");
            Member writer = buildMockMember(1L, "testuser");
            Post savedPost = buildMockPost(10L, "게시물 내용", writer);

            given(memberService.getReferenceById(1L)).willReturn(writer);
            given(postRepository.save(any(Post.class))).willReturn(savedPost);

            Long result = postService.create(request, null, 1L);

            assertThat(result).isEqualTo(10L);
            then(postRepository).should().save(any(Post.class));
            then(postImageRepository).shouldHaveNoInteractions();
            then(hashtagService).should().syncHashtagsForPost(anyLong(), anyString());
        }

        @Test
        @DisplayName("성공 - 이미지 없이(빈 리스트) 게시물 생성, PostImage 저장 미호출")
        void success_without_images_empty_list() throws IOException {
            PostCreateRequest request = new PostCreateRequest("내용");
            Member writer = buildMockMember(1L, "testuser");
            Post savedPost = buildMockPost(10L, "내용", writer);

            given(memberService.getReferenceById(1L)).willReturn(writer);
            given(postRepository.save(any(Post.class))).willReturn(savedPost);

            Long result = postService.create(request, Collections.emptyList(), 1L);

            assertThat(result).isEqualTo(10L);
            then(postImageRepository).shouldHaveNoInteractions();
            then(hashtagService).should().syncHashtagsForPost(anyLong(), anyString());
        }

        @Test
        @DisplayName("성공 - 이미지 포함 게시물 생성, PostImage.saveAll 호출 및 imgOrder 1부터 순서대로 검증")
        void success_with_images_sets_imgOrder() throws IOException {
            PostCreateRequest request = new PostCreateRequest("이미지 게시물");
            Member writer = buildMockMember(1L, "testuser");
            Post savedPost = buildMockPost(10L, "이미지 게시물", writer);

            MultipartFile mockFile1 = mock(MultipartFile.class);
            MultipartFile mockFile2 = mock(MultipartFile.class);
            MultipartFile mockFile3 = mock(MultipartFile.class);

            given(memberService.getReferenceById(1L)).willReturn(writer);
            given(postRepository.save(any(Post.class))).willReturn(savedPost);
            given(fileStore.storeFile(mockFile1)).willReturn("/img/uuid1.jpg");
            given(fileStore.storeFile(mockFile2)).willReturn("/img/uuid2.jpg");
            given(fileStore.storeFile(mockFile3)).willReturn("/img/uuid3.jpg");

            postService.create(request, List.of(mockFile1, mockFile2, mockFile3), 1L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<PostImage>> captor = ArgumentCaptor.forClass(List.class);
            then(postImageRepository).should().saveAll(captor.capture());

            List<PostImage> saved = captor.getValue();
            assertThat(saved).hasSize(3);
            assertThat(saved.get(0).getImageUrl()).isEqualTo("/img/uuid1.jpg");
            assertThat(saved.get(0).getImgOrder()).isEqualTo(1);
            assertThat(saved.get(1).getImageUrl()).isEqualTo("/img/uuid2.jpg");
            assertThat(saved.get(1).getImgOrder()).isEqualTo(2);
            assertThat(saved.get(2).getImageUrl()).isEqualTo("/img/uuid3.jpg");
            assertThat(saved.get(2).getImgOrder()).isEqualTo(3);
            then(hashtagService).should().syncHashtagsForPost(anyLong(), anyString());
        }

        @Test
        @DisplayName("성공 - 이미지 포함 시 각 PostImage의 post 참조가 저장된 Post로 설정된다")
        void success_with_images_sets_post_reference() throws IOException {
            PostCreateRequest request = new PostCreateRequest("내용");
            Member writer = buildMockMember(1L, "testuser");
            Post savedPost = buildMockPost(10L, "내용", writer);

            MultipartFile mockFile = mock(MultipartFile.class);

            given(memberService.getReferenceById(1L)).willReturn(writer);
            given(postRepository.save(any(Post.class))).willReturn(savedPost);
            given(fileStore.storeFile(mockFile)).willReturn("/img/test.jpg");

            postService.create(request, List.of(mockFile), 1L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<PostImage>> captor = ArgumentCaptor.forClass(List.class);
            then(postImageRepository).should().saveAll(captor.capture());

            assertThat(captor.getValue().get(0).getPost()).isSameAs(savedPost);
            then(hashtagService).should().syncHashtagsForPost(anyLong(), anyString());
        }

        @Test
        @DisplayName("성공 - 저장된 Post의 writer가 getReferenceById Proxy로 설정된다 (DB SELECT 없음)")
        void writer_set_via_getReferenceById_not_findById() throws IOException {
            PostCreateRequest request = new PostCreateRequest("내용");
            Member proxyWriter = buildMockMember(5L, "proxyuser");
            Post savedPost = buildMockPost(10L, "내용", proxyWriter);

            given(memberService.getReferenceById(5L)).willReturn(proxyWriter);
            given(postRepository.save(any(Post.class))).willReturn(savedPost);

            postService.create(request, null, 5L);

            then(memberService).should().getReferenceById(5L);
            then(memberService).should(never()).findById(anyLong());
            then(hashtagService).should().syncHashtagsForPost(anyLong(), anyString());
        }

        @Test
        @DisplayName("실패 - fileStore.storeFile() IOException 발생 시 PostException(FILE_UPLOAD_ERROR)로 래핑")
        void fail_fileStore_throws_IOException_is_wrapped() throws IOException {
            PostCreateRequest request = new PostCreateRequest("내용");
            Member writer = buildMockMember(1L, "testuser");
            Post savedPost = buildMockPost(10L, "내용", writer);
            MultipartFile mockFile = mock(MultipartFile.class);

            given(memberService.getReferenceById(1L)).willReturn(writer);
            given(postRepository.save(any(Post.class))).willReturn(savedPost);
            given(fileStore.storeFile(mockFile)).willThrow(new IOException("디스크 쓰기 실패"));

            assertThatThrownBy(() -> postService.create(request, List.of(mockFile), 1L))
                    .isInstanceOf(PostException.class)
                    .hasMessage(PostErrorCode.FILE_UPLOAD_ERROR.getMessage())
                    .hasCauseInstanceOf(IOException.class);
            then(hashtagService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("성공 - create()는 저장된 Post의 id를 반환한다")
        void returns_saved_post_id() throws IOException {
            PostCreateRequest request = new PostCreateRequest("내용");
            Member writer = buildMockMember(1L, "testuser");
            Post savedPost = buildMockPost(42L, "내용", writer);

            given(memberService.getReferenceById(1L)).willReturn(writer);
            given(postRepository.save(any(Post.class))).willReturn(savedPost);

            Long result = postService.create(request, null, 1L);

            assertThat(result).isEqualTo(42L);
            then(hashtagService).should().syncHashtagsForPost(anyLong(), anyString());
        }
    }

    // ============================================================
    // getFeed()
    // ============================================================

    @Nested
    @DisplayName("getFeed()")
    class GetFeed {

        @Test
        @DisplayName("성공 - 게시물이 없으면 빈 리스트, 이미지·PostLike 조회 없음")
        void empty_feed_returns_immediately_without_image_query() {
            Pageable pageable = PageRequest.of(0, 10);
            Slice<PostFeedRow> emptySlice = new SliceImpl<>(Collections.emptyList(), pageable, false);
            given(postRepository.findFeedWithLiked(pageable, 1L)).willReturn(emptySlice);

            SliceResponse<PostResponse> response = postService.getFeed(pageable, 1L);

            assertThat(response.items()).isEmpty();
            assertThat(response.hasNext()).isFalse();
            then(postImageRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("성공 - 게시물이 있지만 이미지가 없으면 PostResponse의 images는 빈 리스트")
        void post_without_images_returns_empty_image_list() {
            Pageable pageable = PageRequest.of(0, 10);
            Member writer = buildMockMember(1L, "testuser");
            Post post = buildMockPost(100L, "이미지 없는 글", writer);

            Slice<PostFeedRow> slice = new SliceImpl<>(List.of(new PostFeedRow(post, false)), pageable, false);
            given(postRepository.findFeedWithLiked(pageable, 1L)).willReturn(slice);
            given(postImageRepository.findByPostIn(List.of(post))).willReturn(Collections.emptyList());
            given(commentRepository.countCommentsByPostIds(List.of(100L))).willReturn(Collections.emptyMap());

            SliceResponse<PostResponse> response = postService.getFeed(pageable, 1L);

            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).images()).isEmpty();
        }

        @Test
        @DisplayName("성공 - PostResponse에 content, username, profileImageUrl이 정확히 매핑된다")
        void post_fields_correctly_mapped_to_response() {
            Pageable pageable = PageRequest.of(0, 10);
            Member writer = Member.builder()
                    .username("mapped_user")
                    .password("pw")
                    .email("mapped@test.com")
                    .name("Mapped User")
                    .profileImageUrl("https://cdn.example.com/profile.jpg")
                    .build();
            ReflectionTestUtils.setField(writer, "id", 1L);

            Post post = buildMockPost(100L, "매핑 테스트 내용", writer);

            Slice<PostFeedRow> slice = new SliceImpl<>(List.of(new PostFeedRow(post, false)), pageable, false);
            given(postRepository.findFeedWithLiked(pageable, 1L)).willReturn(slice);
            given(postImageRepository.findByPostIn(List.of(post))).willReturn(Collections.emptyList());
            given(commentRepository.countCommentsByPostIds(List.of(100L))).willReturn(Collections.emptyMap());

            SliceResponse<PostResponse> response = postService.getFeed(pageable, 1L);

            PostResponse postResponse = response.items().get(0);
            assertThat(postResponse.id()).isEqualTo(100L);
            assertThat(postResponse.content()).isEqualTo("매핑 테스트 내용");
            assertThat(postResponse.username()).isEqualTo("mapped_user");
            assertThat(postResponse.profileImageUrl()).isEqualTo("https://cdn.example.com/profile.jpg");
        }

        @Test
        @DisplayName("성공 - 이미지가 imgOrder 기준 오름차순으로 정렬되어 반환된다")
        void images_sorted_by_imgOrder_ascending() {
            Pageable pageable = PageRequest.of(0, 10);
            Member writer = buildMockMember(1L, "sortuser");
            Post post = buildMockPost(100L, "정렬 테스트", writer);

            PostImage image3 = PostImage.builder().post(post).imageUrl("/img/3.jpg").imgOrder(3).build();
            PostImage image1 = PostImage.builder().post(post).imageUrl("/img/1.jpg").imgOrder(1).build();
            PostImage image2 = PostImage.builder().post(post).imageUrl("/img/2.jpg").imgOrder(2).build();

            Slice<PostFeedRow> slice = new SliceImpl<>(List.of(new PostFeedRow(post, false)), pageable, false);
            given(postRepository.findFeedWithLiked(pageable, 1L)).willReturn(slice);
            given(postImageRepository.findByPostIn(List.of(post))).willReturn(List.of(image3, image1, image2));
            given(commentRepository.countCommentsByPostIds(List.of(100L))).willReturn(Collections.emptyMap());

            SliceResponse<PostResponse> response = postService.getFeed(pageable, 1L);

            List<PostImageResponse> images = response.items().get(0).images();
            assertThat(images).extracting(PostImageResponse::imageOrder).containsExactly(1, 2, 3);
            assertThat(images).extracting(PostImageResponse::imageUrl)
                    .containsExactly("/img/1.jpg", "/img/2.jpg", "/img/3.jpg");
        }

        @Test
        @DisplayName("성공 - 여러 게시물의 이미지가 각 게시물별로 정확히 그룹핑된다")
        void images_correctly_grouped_per_post() {
            Pageable pageable = PageRequest.of(0, 10);
            Member writer = buildMockMember(1L, "groupuser");
            Post post1 = buildMockPost(10L, "첫 번째 글", writer);
            Post post2 = buildMockPost(20L, "두 번째 글", writer);

            PostImage imgA = PostImage.builder().post(post1).imageUrl("/img/a.jpg").imgOrder(1).build();
            PostImage imgB = PostImage.builder().post(post2).imageUrl("/img/b.jpg").imgOrder(1).build();
            PostImage imgC = PostImage.builder().post(post2).imageUrl("/img/c.jpg").imgOrder(2).build();

            Slice<PostFeedRow> slice = new SliceImpl<>(
                    List.of(new PostFeedRow(post1, false), new PostFeedRow(post2, false)), pageable, false);
            given(postRepository.findFeedWithLiked(pageable, 1L)).willReturn(slice);
            given(postImageRepository.findByPostIn(List.of(post1, post2)))
                    .willReturn(List.of(imgA, imgB, imgC));
            given(commentRepository.countCommentsByPostIds(List.of(10L, 20L))).willReturn(Collections.emptyMap());

            SliceResponse<PostResponse> response = postService.getFeed(pageable, 1L);

            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).images()).hasSize(1);
            assertThat(response.items().get(1).images()).hasSize(2);
        }

        @Test
        @DisplayName("성공 - Slice의 hasNext가 true이면 SliceResponse.hasNext도 true")
        void hasNext_propagated_from_slice() {
            Pageable pageable = PageRequest.of(0, 2);
            Member writer = buildMockMember(1L, "user");
            Post post1 = buildMockPost(1L, "첫 번째", writer);
            Post post2 = buildMockPost(2L, "두 번째", writer);

            Slice<PostFeedRow> sliceWithNext = new SliceImpl<>(
                    List.of(new PostFeedRow(post1, false), new PostFeedRow(post2, false)), pageable, true);
            given(postRepository.findFeedWithLiked(pageable, 1L)).willReturn(sliceWithNext);
            given(postImageRepository.findByPostIn(any())).willReturn(Collections.emptyList());

            SliceResponse<PostResponse> response = postService.getFeed(pageable, 1L);

            assertThat(response.hasNext()).isTrue();
        }

        @Test
        @DisplayName("성공 - Slice의 hasNext가 false이면 SliceResponse.hasNext도 false")
        void hasNext_false_propagated_from_slice() {
            Pageable pageable = PageRequest.of(0, 10);
            Member writer = buildMockMember(1L, "user");
            Post post = buildMockPost(1L, "마지막 글", writer);

            Slice<PostFeedRow> lastSlice = new SliceImpl<>(List.of(new PostFeedRow(post, false)), pageable, false);
            given(postRepository.findFeedWithLiked(pageable, 1L)).willReturn(lastSlice);
            given(postImageRepository.findByPostIn(any())).willReturn(Collections.emptyList());

            SliceResponse<PostResponse> response = postService.getFeed(pageable, 1L);

            assertThat(response.hasNext()).isFalse();
        }

        @Test
        @DisplayName("QueryDSL 1쿼리 결과: 두 번째 글만 liked true (findFeedWithLiked가 반영)")
        void feed_liked_from_single_query_row() {
            Long loginId = 7L;
            Pageable pageable = PageRequest.of(0, 10);
            Member writer = buildMockMember(1L, "writer");
            Post post1 = buildMockPost(10L, "글1", writer);
            Post post2 = buildMockPost(20L, "글2", writer);

            Slice<PostFeedRow> slice = new SliceImpl<>(
                    List.of(new PostFeedRow(post1, false), new PostFeedRow(post2, true)), pageable, false);
            given(postRepository.findFeedWithLiked(pageable, loginId)).willReturn(slice);
            given(postImageRepository.findByPostIn(List.of(post1, post2))).willReturn(Collections.emptyList());

            SliceResponse<PostResponse> response = postService.getFeed(pageable, loginId);

            assertThat(response.items().get(0).likeStatus().liked()).isFalse();
            assertThat(response.items().get(1).likeStatus().liked()).isTrue();
            then(postRepository).should().findFeedWithLiked(pageable, loginId);
        }

        @Test
        @DisplayName("피드 likeCount 비정규화, liked는 QueryDSL EXISTS 결과")
        void likeStatus_likeCount_from_post() {
            Pageable pageable = PageRequest.of(0, 10);
            Member writer = buildMockMember(1L, "testuser");
            Post post = buildMockPost(1L, "글", writer);
            ReflectionTestUtils.setField(post, "likeCount", 42);

            Slice<PostFeedRow> slice = new SliceImpl<>(List.of(new PostFeedRow(post, true)), pageable, false);
            given(postRepository.findFeedWithLiked(pageable, 1L)).willReturn(slice);
            given(postImageRepository.findByPostIn(any())).willReturn(Collections.emptyList());

            SliceResponse<PostResponse> response = postService.getFeed(pageable, 1L);

            PostResponse postResponse = response.items().get(0);
            assertThat(postResponse.likeStatus().liked()).isTrue();
            assertThat(postResponse.likeStatus().likeCount()).isEqualTo(42);
            assertThat(postResponse.commentCount()).isZero();
        }
    }

    // ============================================================
    // getPostDetail()
    // ============================================================

    @Nested
    @DisplayName("getPostDetail()")
    class GetPostDetail {

        @BeforeEach
        void stubHashtagBatch() {
            given(hashtagService.findHashtagNamesByPostIds(anyList())).willReturn(Collections.emptyMap());
        }

        @Test
        @DisplayName("성공 - context=profile일 때 prev/next와 이미지 정렬이 매핑된다")
        void success_profile_context_prevNext_and_images_sorted() {
            Long postId = 100L;
            Long writerId = 10L;

            Member writer = buildMockMember(writerId, "writer_user");
            ReflectionTestUtils.setField(writer, "profileImageUrl", "https://cdn.example.com/writer.jpg");

            Post post = buildMockPost(postId, "post content", writer);

            PostImage img2 = PostImage.builder().post(post).imageUrl("/img/2.jpg").imgOrder(2).build();
            PostImage img1 = PostImage.builder().post(post).imageUrl("/img/1.jpg").imgOrder(1).build();

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(postImageRepository.findByPostIn(List.of(post))).willReturn(List.of(img2, img1)); // 반환은 역순
            given(postRepository.findPrevAndNextPostIdByProfile(writerId, postId))
                    .willReturn(new PrevNextPostIds(501L, 502L));

            PostDetailResponse response = postService.getPostDetail(postId, "profile");

            assertThat(response.postId()).isEqualTo(postId);
            assertThat(response.content()).isEqualTo("post content");
            assertThat(response.writer().memberId()).isEqualTo(writerId);
            assertThat(response.writer().username()).isEqualTo("writer_user");

            // imgOrder 기준 오름차순 정렬 검증
            assertThat(response.imageUrls()).containsExactly("/img/1.jpg", "/img/2.jpg");

            assertThat(response.prevPostId()).isEqualTo(501L);
            assertThat(response.nextPostId()).isEqualTo(502L);

            then(postRepository).should().findPrevAndNextPostIdByProfile(writerId, postId);
        }

        @Test
        @DisplayName("성공 - context가 profile이 아니면 prev/next는 null이고 네비게이션 쿼리는 호출되지 않는다")
        void success_non_profile_context_prevNext_null_and_no_call() {
            Long postId = 100L;
            Long writerId = 10L;

            Member writer = buildMockMember(writerId, "writer_user");
            Post post = buildMockPost(postId, "post content", writer);

            given(postRepository.findById(postId)).willReturn(Optional.of(post));
            given(postImageRepository.findByPostIn(any())).willReturn(Collections.emptyList());

            PostDetailResponse response = postService.getPostDetail(postId, "feed");

            assertThat(response.prevPostId()).isNull();
            assertThat(response.nextPostId()).isNull();
            then(postRepository).should(never()).findPrevAndNextPostIdByProfile(anyLong(), anyLong());
        }

        // memberId 파라미터가 없어졌기 때문에 null 케이스 테스트는 제거합니다.
    }
}
