package com.example.instagramclone.domain.hashtag.domain;

import com.example.instagramclone.core.common.BaseEntity;
import com.example.instagramclone.domain.post.domain.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시물–해시태그 연결 (다대다 중간 엔티티).
 *
 * <p>Day 16: {@code Post}·{@code Hashtag} 양방향 컬렉션 없이 {@link PostHashtag} 만으로 연결합니다.
 * 양방향 {@code OneToMany}는 의도적으로 배제하여 순환 참조와 N+1 문제를 원천 차단합니다.
 */
@Entity
@Table(
        name = "post_hashtags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_post_hashtag",                    // 더 직관적인 이름으로 변경
                columnNames = {"post_id", "hashtag_id"}
        ),
        indexes = {
                @Index(name = "idx_post_hashtag_post_id", columnList = "post_id"),      // 조회 성능 향상
                @Index(name = "idx_post_hashtag_hashtag_id", columnList = "hashtag_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostHashtag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hashtag_id", nullable = false)
    private Hashtag hashtag;

    /**
     * 정적 팩토리 메서드 (Hashtag 엔티티와 스타일 통일)
     */
    public static PostHashtag create(Post post, Hashtag hashtag) {
        PostHashtag link = new PostHashtag();
        link.post = post;
        link.hashtag = hashtag;
        return link;
    }
}