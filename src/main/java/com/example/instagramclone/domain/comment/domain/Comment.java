package com.example.instagramclone.domain.comment.domain;

import com.example.instagramclone.core.common.BaseEntity;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.post.domain.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글에 달리는 댓글 엔티티 (원댓글·대댓글 동일 테이블, Self-Join).
 *
 * <p>Day 14 라이브 코딩에서 다룰 내용
 * <ul>
 *   <li>{@code parent == null} → 원댓글, {@code parent != null} → 대댓글</li>
 *   <li>2-Depth 제한: 대댓글의 부모는 반드시 원댓글이어야 함 (3단 이상 금지)</li>
 *   <li>양방향 {@code @OneToMany} children 컬렉션은 두지 않고, 조회는 QueryDSL로만 처리</li>
 * </ul>
 */
@Entity
@Table(
        name = "comments",
        // post_id: 게시글별 원댓글/대댓글 목록 조회 성능 향상용 인덱스
        // parent_id: 특정 댓글의 대댓글 목록(=replies) 조회를 빠르게 하기 위한 인덱스
        indexes = {
                @Index(name = "idx_comments_post_id", columnList = "post_id"),
                @Index(name = "idx_comments_parent_id", columnList = "parent_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member writer;

    /**
     * 부모 댓글. null이면 원댓글(Root).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    public static Comment create(Post post, Member writer, String content, Comment parent) {
        Comment comment = new Comment();
        comment.post = post;
        comment.writer = writer;
        comment.content = content;
        comment.parent = parent;
        return comment;
    }
}
