package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.core.constant.CacheNames;
import com.example.instagramclone.core.exception.PostErrorCode;
import com.example.instagramclone.core.exception.PostException;
import com.example.instagramclone.core.util.FileStore;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import com.example.instagramclone.domain.hashtag.application.HashtagService;
import com.example.instagramclone.domain.comment.domain.CommentRepository;
import com.example.instagramclone.domain.member.api.MemberSummary;
import com.example.instagramclone.domain.member.application.MemberService;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.post.api.*;
import com.example.instagramclone.domain.post.domain.*;
import com.example.instagramclone.domain.post.infrastructure.PostFeedRow;
import com.example.instagramclone.domain.post.infrastructure.PostMapper;
import com.example.instagramclone.domain.post.infrastructure.PrevNextPostIds;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final MemberService memberService;
    private final FileStore fileStore;
    private final PostMapper postMapper;
    private final HashtagService hashtagService;

    /**
     * 게시글 생성 서비스.
     *
     * <p>게시글이 생성되면 작성자의 postCount 가 늘어나므로
     * 작성자(loginMemberId) 의 프로필 캐시를 무효화한다.
     * 다른 사람의 프로필 캐시는 건드리지 않는다.</p>
     * <p>
     * 흐름:
     * 1) 로그인 유저를 writer 로 잡고 Post 저장
     * 2) 이미지가 있으면 파일 저장 후 PostImage 엔티티 생성
     * 3) PostImage 여러 개를 한 번에 저장
     */
    @Caching(evict = {
            // 작성자의 프로필 헤더 집계 캐시 무효화 (postCount 증가)
            @CacheEvict(value = CacheNames.PROFILE_STATS, key = "#loginMemberId"),
            // 작성자의 프로필 그리드 전체 페이지 무효화
            // 새 게시물이 추가되면 0페이지부터 모든 페이지가 밀린다.
            // 키에 pageNumber 가 포함되어 있어 특정 페이지만 골라낼 수 없으므로 allEntries=true 가 불가피하다.
            @CacheEvict(value = CacheNames.PROFILE_GRID, allEntries = true)
    })
    @Transactional
    public Long create(PostCreateRequest request, List<MultipartFile> images, Long loginMemberId) throws IOException {
        // FK 연결만 필요하므로 Member 전체를 매번 조회하지 않고 참조 프록시를 받아 사용한다.
        Member writer = memberService.getReferenceById(loginMemberId);

        Post post = Post.builder()
                .content(request.content())
                .writer(writer)
                .build();

        Post savedPost = postRepository.save(post);

        // 이미지는 선택값이다. 없으면 게시글만 저장하고 끝낸다.
        if (images != null && !images.isEmpty()) {
            // images.size() 만큼 0,1,2... 인덱스를 만들고,
            // 각 파일을 PostImage 엔티티로 바꿔 리스트로 모은다.
            List<PostImage> postImages = IntStream.range(0, images.size())
                    .mapToObj(i -> {
                        try {
                            // 실제 파일 저장소(로컬)에 저장하고, 저장된 URL만 DB에 남긴다.
                            String imageUrl = fileStore.storeFile(images.get(i));
                            return PostImage.builder()
                                    .post(savedPost)
                                    .imageUrl(imageUrl)
                                    // 첫 번째 이미지를 1번으로 저장해 프론트 정렬 기준으로 활용한다.
                                    .imgOrder(i + 1)
                                    .build();
                        } catch (IOException e) {
                            throw new PostException(PostErrorCode.FILE_UPLOAD_ERROR, e);
                        }
                    })
                    .toList();

            postImageRepository.saveAll(postImages);
        }

        // 해시태그 동기화
        hashtagService.syncHashtagsForPost(savedPost.getId(), request.content());

        return savedPost.getId();
    }

    /**
     * ID로 게시글 엔티티를 조회한다. 없으면 {@link PostException}(POST_NOT_FOUND).
     */
    public Post getPostByIdOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
    }

    /**
     * 메인 피드. QueryDSL 1쿼리: Post + writer fetchJoin + EXISTS(post_like) 로 liked (과제).
     * 이미지는 기존처럼 findByPostIn 1회.
     */
    public SliceResponse<PostResponse> getFeed(Pageable pageable, Long loginMemberId) {
        // 게시글 + 작성자 + "내가 좋아요 했는지" 여부를 먼저 조회한다.
        Slice<PostFeedRow> slice = postRepository.findFeedWithLiked(pageable, loginMemberId);
        List<PostFeedRow> rows = slice.getContent();

        // 조회 결과가 없으면 빈 목록을 바로 반환한다.
        if (rows.isEmpty()) {
            return SliceResponse.of(slice.hasNext(), Collections.emptyList());
        }

        // 이미지 조회를 위해 Post 목록만 따로 뽑아낸다.
        List<Post> posts = rows.stream().map(PostFeedRow::post).toList();

        // 여러 게시글의 이미지를 한 번에 조회한 뒤, Post별로 묶어 Map으로 만든다.
        Map<Post, List<PostImage>> imageMap = groupImagesByPost(posts);

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Long> commentCountMap = commentRepository.countCommentsByPostIds(postIds);
        Map<Long, List<String>> hashtagMap = Objects.requireNonNullElse(
                hashtagService.findHashtagNamesByPostIds(postIds),
                Map.of());

        // 각 행(row)을 화면용 PostResponse DTO로 변환한다.
        List<PostResponse> responses = rows.stream()
                .map(row -> postMapper.toResponse(
                        row.post(),
                        // 같은 게시글의 이미지들만 꺼내고 imgOrder 기준으로 정렬해서 전달
                        getSortedImages(row.post(), imageMap),
                        row.liked(),
                        commentCountMap.getOrDefault(row.post().getId(), 0L),
                        hashtagMap.getOrDefault(row.post().getId(), List.of())))
                .toList();

        return SliceResponse.of(slice.hasNext(), responses);
    }

    /**
     * 커서 기반 메인 피드.
     * 기존 getFeed(Pageable, Long)과 내부 로직은 동일하고,
     * repository 호출만 커서 버전으로 바뀝니다.
     */
    public SliceResponse<PostResponse> getFeedByCursor(Long cursorId, int size, Long loginMemberId) {

        // ✅ 커서 기반 repository 메서드 호출
        Slice<PostFeedRow> slice = postRepository.findFeedWithLikedByCursor(cursorId, size, loginMemberId);
        List<PostFeedRow> rows = slice.getContent();

        if (rows.isEmpty()) {
            return SliceResponse.of(false, Collections.emptyList());
        }

        // ▼ 여기서부터는 기존 getFeed()와 100% 동일
        List<Post> posts = rows.stream().map(PostFeedRow::post).toList();
        Map<Post, List<PostImage>> imageMap = groupImagesByPost(posts);

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Long> commentCountMap = commentRepository.countCommentsByPostIds(postIds);
        Map<Long, List<String>> hashtagMap = Objects.requireNonNullElse(
                hashtagService.findHashtagNamesByPostIds(postIds),
                Map.of());

        List<PostResponse> responses = rows.stream()
                .map(row -> postMapper.toResponse(
                        row.post(),
                        getSortedImages(row.post(), imageMap),
                        row.liked(),
                        commentCountMap.getOrDefault(row.post().getId(), 0L),
                        hashtagMap.getOrDefault(row.post().getId(), List.of())))
                .toList();

        return SliceResponse.of(slice.hasNext(), responses);
    }

    /**
     * Day 15 Live Coding: 피드 상세 페이지 조회
     * 컨텍스트에 따른 선택적 네비게이션 구조 포함
     */
    public PostDetailResponse getPostDetail(Long postId, String context) {
        return getPostDetail(postId, context, null);
    }

    /**
     * Day 15 Live Coding: 피드 상세 페이지 조회
     * 컨텍스트에 따른 선택적 네비게이션 구조 포함.
     */
    public PostDetailResponse getPostDetail(Long postId, String context, Long loginMemberId) {
        // 1. Post 엔티티와 연관된 이미지를 찾고, 작성자(writer) 요약 정보를 조립하세요.
        Post post = getPostByIdOrThrow(postId);

        List<String> imageUrls = postImageRepository.findByPostIn(List.of(post)).stream()
                .sorted(Comparator.comparing(PostImage::getImgOrder))
                .map(PostImage::getImageUrl)
                .toList();

        MemberSummary writer = MemberSummary.builder()
                .memberId(post.getWriter().getId())
                .username(post.getWriter().getUsername())
                .profileImageUrl(post.getWriter().getProfileImageUrl())
                .build();

        // 2. context 파라미터가 "profile" 일 때,
        //    해당 게시물의 writer를 기준으로 prevPostId, nextPostId 를 검색하세요.
        PrevNextPostIds prevNextPostIds = null;
        if ("profile".equals(context)) {
            prevNextPostIds = postRepository.findPrevAndNextPostIdByProfile(post.getWriter().getId(), postId);
        }

        Long prevPostId = prevNextPostIds != null ? prevNextPostIds.prevPostId() : null;
        Long nextPostId = prevNextPostIds != null ? prevNextPostIds.nextPostId() : null;

        Map<Long, List<String>> tagMap = Objects.requireNonNullElse(
                hashtagService.findHashtagNamesByPostIds(List.of(postId)),
                Map.of());
        List<String> hashtagNames = tagMap.getOrDefault(postId, List.of());

        // 3. 로그인 유저 기준 좋아요 여부를 응답에 포함합니다.
        Member loginMember = memberService.getReferenceById(loginMemberId);
        boolean liked = postLikeRepository.existsByMemberAndPost(loginMember, post);
        LikeStatusResponse likeStatus = new LikeStatusResponse(liked, post.getLikeCount());

        return PostDetailResponse.of(
                post,
                writer,
                imageUrls,
                post.getCreatedAt(),
                likeStatus,
                prevPostId,
                nextPostId,
                hashtagNames
        );
    }


    /**
     * username 기반 프로필 게시글 그리드 목록 조회.
     *
     * <p>캐시 키: {@code username:pageNumber:pageSize}<br>
     * username 만으로는 부족하다 — 1페이지와 2페이지는 내용이 다르므로
     * pageNumber 와 pageSize 를 함께 포함해야 각 페이지가 독립된 캐시 엔트리로 저장된다.</p>
     *
     * <p>프론트 라우트가 /:username 이므로,
     * 프로필 페이지 진입 시에는 username → member 조회 후 writerId 로 쿼리한다.</p>
     */
    @Cacheable(
            value = CacheNames.PROFILE_GRID,
            key = "#username + ':' + #pageable.pageNumber + ':' + #pageable.pageSize"
    )
    public SliceResponse<ProfilePostResponse> getMemberPostsByUsername(String username, Pageable pageable) {
        Member member = memberService.findByUsername(username);
        Slice<ProfilePostResponse> slice = postRepository.findAllByWriterId(member.getId(), pageable);
        return SliceResponse.of(slice.hasNext(), slice.getContent());
    }


    /** 커서 기반 프로필 게시물 조회 */
    public SliceResponse<ProfilePostResponse> getMemberPostsByUsernameByCursor(String username, Long cursorId, int size) {
        Member member = memberService.findByUsername(username);
        Slice<ProfilePostResponse> slice = postRepository.findAllByWriterIdByCursor(member.getId(), cursorId, size);
        return SliceResponse.of(slice.hasNext(), slice.getContent());
    }

    /**
     * 여러 게시글의 이미지를 한 번에 조회한 뒤,
     * "어떤 이미지가 어떤 게시글 것인지" Post 기준으로 묶는다.
     * <p>
     * 결과 예시:
     * post1 -> [img1, img2]
     * post2 -> [img3]
     */
    private Map<Post, List<PostImage>> groupImagesByPost(List<Post> posts) {
        return postImageRepository.findByPostIn(posts).stream()
                .collect(Collectors.groupingBy(PostImage::getPost));
    }

    /**
     * 특정 게시글의 이미지 목록을 꺼내고 imgOrder 순서대로 정렬한다.
     * <p>
     * imageMap에 해당 게시글이 없을 수 있으므로 빈 리스트를 기본값으로 사용한다.
     */
    private List<PostImage> getSortedImages(Post post, Map<Post, List<PostImage>> imageMap) {
        return imageMap.getOrDefault(post, Collections.emptyList()).stream()
                .sorted(Comparator.comparing(PostImage::getImgOrder))
                .toList();
    }
}
