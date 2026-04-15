package com.example.instagramclone.core.constant;

/**
 * Spring Cache 이름 상수 모음.
 *
 * <p>캐시 이름을 문자열 리터럴로 직접 쓰면 오타가 나도 컴파일 시점에 잡히지 않는다.
 * 이 클래스에 상수로 모아 두면 IDE 자동완성 + 오타 방지 + 전체 캐시 목록 파악이 한 곳에서 가능하다.</p>
 *
 * <p>네이밍 규칙: 도메인 + 용도 (camelCase)</p>
 *
 * <pre>
 * 사용 예:
 *   {@code @Cacheable(value = CacheNames.PROFILE_STATS, key = "#loginMemberId + ':' + #username")}
 *   {@code @CacheEvict(value = CacheNames.PROFILE_STATS, key = ...)}
 * </pre>
 */
public final class CacheNames {

    /**
     * 프로필 헤더 집계 캐시.
     * 팔로워 수 · 팔로잉 수 · 게시물 수 + isFollowing + isCurrentUser 를 담은
     * {@code MemberProfileResponse} 를 TTL 동안 보관한다.
     *
     * <p>캐시 키: {@code targetMemberId} (단일 키)<br>
     * 무효화 시점:
     * <ul>
     *   <li>팔로우 / 언팔로우 발생 시 (FollowService) — loginMemberId, targetMemberId 각각 evict</li>
     *   <li>게시물 작성 시 (PostService) — loginMemberId(= 작성자) evict</li>
     * </ul>
     */
    public static final String PROFILE_STATS = "profileStats";

    /**
     * 프로필 게시글 그리드 목록 캐시.
     * {@code ProfilePostResponse} 의 {@code SliceResponse} 를 TTL 동안 보관한다.
     *
     * <p>캐시 키: {@code username:pageNumber:pageSize}<br>
     * 무효화 시점:
     * <ul>
     *   <li>게시물 작성 시 (PostService) — 작성자의 모든 페이지가 변경되므로 {@code allEntries=true}</li>
     * </ul>
     *
     * <p>왜 {@code allEntries=true} 가 여기선 정당한가?<br>
     * 새 게시물이 추가되면 0페이지·1페이지·2페이지... 모든 페이지가 영향을 받는다.
     * 키에 페이지 번호가 포함되어 있어 "이 사용자의 모든 페이지" 를 패턴으로 골라낼 수 없으므로
     * 전체를 비우는 것이 유일한 현실적 선택이다.</p>
     */
    public static final String PROFILE_GRID = "profileGrid";

    private CacheNames() {
        // 유틸리티 클래스 — 인스턴스화 방지
    }
}
