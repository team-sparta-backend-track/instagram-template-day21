package com.example.instagramclone.domain.post.domain;

import com.example.instagramclone.core.common.BaseEntity;
import com.example.instagramclone.domain.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시물 좋아요 연결 엔티티 (Member - Post N:M).
 *
 * [Day 12 Step 1] 
 * - (member_id, post_id) 쌍의 유일 제약 또는 복합키 적용
 * - 단방향 @ManyToOne만 사용 (양방향 연관관계 미사용)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_post_like_member_post",
        columnNames = {"member_id", "post_id"}
    )
})
public class PostLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /**
     * [Day 12 Step 1] 단방향 연관관계만 세팅하는 팩토리 메서드.
     * JPA는 기본 생성자로 엔티티를 만들므로, 필드 주입용으로 사용합니다.
     */
    public static PostLike create(Member member, Post post) {
        PostLike postLike = new PostLike();
        postLike.member = member;
        postLike.post = post;
        return postLike;
    }
}
