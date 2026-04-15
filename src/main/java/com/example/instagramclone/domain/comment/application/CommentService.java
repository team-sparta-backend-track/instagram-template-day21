package com.example.instagramclone.domain.comment.application;

import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.core.exception.CommentErrorCode;
import com.example.instagramclone.core.exception.CommentException;
import com.example.instagramclone.domain.comment.api.CommentCreateRequest;
import com.example.instagramclone.domain.comment.api.CommentResponse;
import com.example.instagramclone.domain.comment.domain.Comment;
import com.example.instagramclone.domain.comment.domain.CommentRepository;
import com.example.instagramclone.domain.comment.infrastructure.RootCommentListRow;
import com.example.instagramclone.domain.member.application.MemberService;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.mention.application.MentionService;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.event.NotificationEvent;
import com.example.instagramclone.domain.post.application.PostService;
import com.example.instagramclone.domain.post.domain.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 댓글 유스케이스 (작성·조회).
 *
 * <p>게시글 존재 여부는 {@link PostService}에 위임하여 <strong>PostRepository를 이 클래스에서 직접 참조하지 않는다.</strong>
 * (도메인 경계: 댓글 애플리케이션은 Post 애플리케이션 서비스만 의존)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    /** 게시글 조회·검증은 Post 도메인 서비스로만 위임 (리포지토리 직접 주입 금지) */
    private final PostService postService;
    /** 댓글 작성자 FK 연결용 — {@link MemberService#getReferenceById(Long)} (토큰 신뢰, 조회 비용 최소화) */
    private final MemberService memberService;
    private final MentionService mentionService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 댓글 또는 대댓글을 저장합니다.
     *
     * <ol>
     *   <li>게시글({@code postId})이 존재해야 합니다.</li>
     *   <li>작성자는 로그인한 회원({@code loginMemberId})입니다.</li>
     *   <li>{@code parentId}가 없으면 <strong>원댓글</strong>, 있으면 그 id를 부모로 하는 <strong>대댓글</strong>입니다.</li>
     *   <li>대댓글인 경우: 부모 댓글이 반드시 존재하고, 같은 게시글에 속하며, 부모는 <strong>원댓글</strong>({@code parent.parent == null})이어야 합니다.
     *       그렇지 않으면 3-depth 이상이 되어 인스타그램식 2-depth 정책에 어긋납니다.</li>
     * </ol>
     */
    @Transactional
    public CommentResponse createComment(Long postId, CommentCreateRequest request, Long loginMemberId) {
        // 1) Post 도메인: 해당 id의 게시글이 없으면 PostException(POST_NOT_FOUND) — 여기서는 엔티티만 필요
        Post post = postService.getPostByIdOrThrow(postId);

        // 2) Member 도메인: 토큰을 신뢰하고 프록시 참조 사용 (엔티티 전체 조회 대신)
        Member writer = memberService.getReferenceById(loginMemberId);

        // 3) 대댓글이면 부모 댓글 검증(존재·동일 게시글·부모는 원댓글만). 원댓글이면 parent == null
        Comment parent = resolveParentOrThrow(postId, request.parentId());

        // 4) 도메인 팩토리로 엔티티 생성 후 저장
        Comment comment = Comment.create(post, writer, request.content(), parent);
        Comment saved = commentRepository.save(comment);

        // 5) 멘션 동기화 — 댓글 본문에서 @username 추출 → CommentMention 저장
        mentionService.syncMentionsForComment(saved, request.content());

        // ✅ 이벤트 발행 — 댓글 알림 (게시물 작성자에게)
        eventPublisher.publishEvent(new NotificationEvent(
                NotificationType.COMMENT,
                post.getWriter().getId(),    // receiver = 게시물 작성자
                loginMemberId,               // sender = 댓글 작성자
                postId,                      // target = 게시물
                null
        ));

        // 6) API 응답 DTO (원댓글은 replyCount=0, 대댓글은 null 등 규칙은 CommentResponse.from 참고)
        return CommentResponse.from(saved);
    }

    /**
     * {@code parentId}가 null이면 원댓글이므로 부모 없음.
     * 값이 있으면 부모 댓글을 조회하고, 게시글 일치 및 2-depth(부모는 원댓글만)를 검증합니다.
     */
    private Comment resolveParentOrThrow(Long postId, Long parentId) {
        // 원댓글이면 early return
        if (parentId == null) {
            return null;
        }

        // 부모 id에 해당하는 원댓글이 없으면 잘못된 요청
        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new CommentException(CommentErrorCode.COMMENT_NOT_FOUND));

        // 부모 댓글이 다른 게시글에 달린 것이면 경로(postId)와 본문이 맞지 않음
        if (!parent.getPost().getId().equals(postId)) {
            throw new CommentException(CommentErrorCode.INVALID_POST_FOR_COMMENT);
        }

        // 부모가 이미 "대댓글"이면 여기에 또 달면 3-depth → 정책 위반
        if (parent.getParent() != null) {
            throw new CommentException(CommentErrorCode.PARENT_NOT_ROOT_COMMENT);
        }

        return parent;
    }

    /**
     * 게시글의 원댓글 목록 (replyCount 포함).
     *
     * <ol>
     *   <li>게시글이 없으면 {@link PostService#getPostByIdOrThrow(Long)} 에서 예외.</li>
     *   <li>QueryDSL로 원댓글 Slice + {@code replyCount} 를 <strong>한 쿼리</strong>(상관 서브쿼리 {@code COUNT})로 조회 —
     *       {@link com.example.instagramclone.domain.comment.infrastructure.CommentRepositoryCustom}.</li>
     *   <li>각 행을 {@link CommentResponse#fromRootListItem(Comment, long)} 로 변환.</li>
     * </ol>
     *
     * @param loginMemberId 향후 "내가 쓴 댓글" 하이라이트 등에 사용 (현재는 미사용)
     */
    public SliceResponse<CommentResponse> getRootComments(Long postId, Pageable pageable, Long loginMemberId) {
        postService.getPostByIdOrThrow(postId);
        Slice<RootCommentListRow> slice = commentRepository.findRootCommentsWithReplyCountByPostId(postId, pageable);
        List<RootCommentListRow> rows = slice.getContent();
        if (rows.isEmpty()) {
            return SliceResponse.of(slice.hasNext(), Collections.emptyList());
        }
        List<CommentResponse> items = rows.stream()
                .map(r -> CommentResponse.fromRootListItem(r.comment(), r.replyCount()))
                .toList();
        return SliceResponse.of(slice.hasNext(), items);
    }

    /** 커서 기반 원댓글 목록 조회 */
    public SliceResponse<CommentResponse> getRootCommentsByCursor(Long postId, Long cursorId, int size, Long loginMemberId) {
        postService.getPostByIdOrThrow(postId);
        Slice<RootCommentListRow> slice = commentRepository.findRootCommentsWithReplyCountByPostIdByCursor(postId, cursorId, size);
        List<RootCommentListRow> rows = slice.getContent();
        if (rows.isEmpty()) {
            return SliceResponse.of(false, Collections.emptyList());
        }
        List<CommentResponse> items = rows.stream()
                .map(r -> CommentResponse.fromRootListItem(r.comment(), r.replyCount()))
                .toList();
        return SliceResponse.of(slice.hasNext(), items);
    }

    /**
     * 특정 원댓글에 달린 대댓글만 Slice 로 조회합니다 (답글 더보기).
     *
     * <ol>
     *   <li>게시글 존재: {@link PostService#getPostByIdOrThrow(Long)}</li>
     *   <li>선검증(QueryDSL): {@code rootCommentId} 가 이 {@code postId} 의 <strong>원댓글</strong>인지 —
     *       아니면 {@link CommentErrorCode#COMMENT_NOT_FOUND} (다른 글의 댓글·대댓글 id 포함)</li>
     *   <li>대댓글 Slice: {@link CommentRepository#findRepliesByRootComment(Long, Long, Pageable)}</li>
     *   <li>응답: {@link CommentResponse#from(Comment)} — 대댓글 목록에서는 {@code replyCount} 를 쓰지 않으므로 {@code null}</li>
     * </ol>
     *
     * @param loginMemberId 향후 차단 사용자 필터 등에 사용 (현재는 미사용)
     */
    public SliceResponse<CommentResponse> getReplies(Long postId, Long rootCommentId, Pageable pageable, Long loginMemberId) {
        postService.getPostByIdOrThrow(postId);
        if (!commentRepository.existsRootCommentForReplies(postId, rootCommentId)) {
            throw new CommentException(CommentErrorCode.COMMENT_NOT_FOUND);
        }
        Slice<Comment> slice = commentRepository.findRepliesByRootComment(postId, rootCommentId, pageable);
        List<CommentResponse> items = slice.getContent().stream()
                .map(CommentResponse::from)
                .toList();
        return SliceResponse.of(slice.hasNext(), items);
    }

    /** 커서 기반 대댓글 목록 조회 */
    public SliceResponse<CommentResponse> getRepliesByCursor(Long postId, Long rootCommentId, Long cursorId, int size, Long loginMemberId) {
        postService.getPostByIdOrThrow(postId);
        if (!commentRepository.existsRootCommentForReplies(postId, rootCommentId)) {
            throw new CommentException(CommentErrorCode.COMMENT_NOT_FOUND);
        }
        Slice<Comment> slice = commentRepository.findRepliesByRootCommentByCursor(postId, rootCommentId, cursorId, size);
        List<CommentResponse> items = slice.getContent().stream()
                .map(CommentResponse::from)
                .toList();
        return SliceResponse.of(slice.hasNext(), items);
    }
}
