package com.example.instagramclone.domain.post.domain;

import com.example.instagramclone.core.common.BaseEntity;
import com.example.instagramclone.domain.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;


@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member writer;

    /**
     * [Day 12 Step 3] 비정규화: 피드/프로필 조회 시 post_like COUNT(*) 대신 이 값 사용.
     * 토글 시 +1/-1 갱신. (동시 폭주 시 lost update 가능 → Day 17 락으로 보완)
     */
    @Column(nullable = false)
    private long likeCount = 0L;

    @Builder
    public Post(String content, Member writer) {
        this.content = content;
        this.writer = writer;
    }

    /**
     * [Day 12 Step 3] 좋아요 추가 시 +1, 취소 시 -1.
     * 취소 후 음수 방지(비정규화/실패 시나리오에서 최소 0 유지).
     */
    public void changeLikeCountBy(int delta) {
        this.likeCount = Math.max(0, this.likeCount + delta);
    }

}
