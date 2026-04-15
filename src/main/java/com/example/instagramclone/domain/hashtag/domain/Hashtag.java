package com.example.instagramclone.domain.hashtag.domain;

import com.example.instagramclone.core.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 해시태그 마스터. {@code name} 은 정규화된 저장 값(예: 소문자)으로 유니크합니다.
 *
 * <p>Day 16: {@code Post}·{@code Hashtag} 양방향 컬렉션 없이 {@link PostHashtag} 만으로 연결합니다.
 */
@Entity
@Table(
        name = "hashtags",
        uniqueConstraints = @UniqueConstraint(name = "uk_hashtag_name", columnNames = "name"),
        indexes = @Index(name = "idx_hashtag_name", columnList = "name")  // 검색 성능을 위한 명시적 인덱스 추가
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hashtag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 이 태그가 붙은 게시물 수 (비정규화). {@link com.example.instagramclone.domain.hashtag.domain.PostHashtag} 생성/삭제 시 동기화.
     */
    @Column(nullable = false)
    private long postCount = 0L;

    @Builder
    public Hashtag(String name) {
        this.name = name.toLowerCase().trim(); // 정규화 필수! (소문자 + 앞뒤 공백 제거)
    }

    public static Hashtag create(String normalizedName) {
        return Hashtag.builder().name(normalizedName).build();
    }

    /** 게시물에 태그 연결이 추가될 때 */
    public void incrementPostCount() {
        this.postCount++;
    }

    /** 게시물에서 태그 연결이 제거될 때 */
    public void decrementPostCount() {
        this.postCount = Math.max(0L, this.postCount - 1);
    }
}