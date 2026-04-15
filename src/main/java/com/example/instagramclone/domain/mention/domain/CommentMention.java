package com.example.instagramclone.domain.mention.domain;

import com.example.instagramclone.core.common.BaseEntity;
import com.example.instagramclone.domain.comment.domain.Comment;
import com.example.instagramclone.domain.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글 본문의 @username 멘션을 저장하는 조인 테이블 (comment ↔ member).
 * PostHashtag(post ↔ hashtag)와 동일한 구조입니다.
 */
@Entity
@Table(
    name = "comment_mentions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_comment_mention",
        columnNames = {"comment_id", "mentioned_member_id"}
    ),
    indexes = {
        @Index(name = "idx_comment_mention_comment_id", columnList = "comment_id"),
        @Index(name = "idx_comment_mention_member_id", columnList = "mentioned_member_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentMention extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentioned_member_id", nullable = false)
    private Member mentionedMember;

    public static CommentMention create(Comment comment, Member mentionedMember) {
        CommentMention mention = new CommentMention();
        mention.comment = comment;
        mention.mentionedMember = mentionedMember;
        return mention;
    }
}
