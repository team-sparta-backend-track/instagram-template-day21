package com.example.instagramclone.domain.member.application;

import com.example.instagramclone.core.constant.CacheNames;
import com.example.instagramclone.domain.member.api.MemberProfileResponse;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 조회 전용 서비스.
 *
 * <p>프로필 헤더는 MemberRepository의 QueryDSL 커스텀 쿼리로
 * (팔로워/팔로잉/게시물 수 + isFollowing + isCurrentUser)까지 한 번에 내려준다.</p>
 *
 * <h2>Day 17 캐시 설계</h2>
 *
 * <h3>캐시 키: {@code #targetMemberId} (단일 키)</h3>
 * <p>팔로워/팔로잉/게시물 수는 <b>프로필 주인(targetMember)</b> 기준 데이터다.
 * 단일 키를 쓰면 FollowService·PostService 의 {@code @CacheEvict} 가 ID 하나로 정확하게 동작한다.
 * B가 글을 써도 B의 엔트리 하나만 날아가고 나머지 사람들의 캐시는 유지된다.</p>
 *
 * <pre>
 * 캐시 엔트리 예시:
 *   profileStats::42  →  MemberProfileResponse(followerCount=100, postCount=5, ...)
 * </pre>
 *
 * <h3>⚠ isFollowing 은 캐시에서 제외해야 정확하다</h3>
 * <p>{@code isFollowing} 은 "누가 보느냐(viewer)" 에 따라 다른 값이지만,
 * 단일 키 설계에서는 프로필 주인 기준으로 하나의 값만 캐싱된다.
 * 복합 키({@code loginMemberId + ':' + targetMemberId})를 쓰면 viewer 마다 정확하지만,
 * 팔로우/게시물 이벤트마다 해당 프로필을 본 모든 viewer 의 캐시를 패턴 삭제해야 해서
 * {@code allEntries=true} 외엔 방법이 없다 → 캐시 효과 반감.
 * 따라서 실무에서는 {@code isFollowing} 을 별도로 조회하거나 TTL 을 짧게 가져가는 방식을 택한다.
 * Day 17 실습에서는 단일 키를 유지하고 이 한계를 인지한 채 진행한다.</p>
 *
 * <h3>self-invocation 함정</h3>
 * <p>{@code getProfileByUsername} 이 같은 빈의 {@code getProfileById} 를 직접 호출하면
 * Spring AOP 프록시를 우회해 {@code @Cacheable} 이 동작하지 않는다.
 * → {@link #self} self-주입으로 해결한다 (라이브에서 시연).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberProfileService {

    private final MemberService memberService;
    private final MemberRepository memberRepository;

    // ================================================================
    // TODO [Day 17 Live - Step 4-A] self-invocation 함정 시연
    //
    // 1단계 (일부러 잘못 작성):
    //   getProfileByUsername 에서 아래처럼 this.getProfileById() 를 직접 호출한다.
    //   → 캐시가 전혀 동작하지 않는다. (프록시 우회)
    //
    // 2단계 (원인 파악):
    //   @Cacheable 은 Spring AOP 프록시를 통해 동작한다.
    //   같은 클래스 내 직접 호출은 프록시를 거치지 않아 애너테이션이 무시된다.
    //
    // 3단계 (수정):
    //   self 필드를 통해 프록시 빈을 거쳐 호출한다. (아래 @Lazy @Autowired 참고)
    // ================================================================

    /**
     * self-invocation 해결용 self 주입.
     *
     * <p>{@code @Lazy}: 순환 참조 방지 — 빈이 완전히 초기화된 후에 주입된다.<br>
     * {@code @Autowired}: {@code @RequiredArgsConstructor} 는 {@code final} 필드만 다루므로,
     * non-final 인 self 는 별도 필드 주입으로 받는다.</p>
     */
    @Lazy
    @Autowired
    private MemberProfileService self;

    /**
     * username 기반 프로필 조회 (공개 진입점).
     *
     * <p>username → targetMemberId 변환 후 {@link #getProfileById} 로 위임한다.
     * {@code @Cacheable} 은 {@code getProfileById} 에만 붙어 있으므로,
     * 반드시 {@code self.getProfileById()} 를 통해 프록시를 거쳐야 캐시가 동작한다.</p>
     */
    public MemberProfileResponse getProfileByUsername(Long loginMemberId, String username) {
        // 1. username → targetMember (존재 여부 검증 포함)
        Member targetMember = memberService.findByUsername(username);

        // 2. 프록시 빈(self)을 통해 호출해야 @Cacheable 이 동작한다.
        //    this.getProfileById(...)  ← X (프록시 우회, 캐시 미동작)
        //    self.getProfileById(...) ← O (프록시 경유, 캐시 동작)
        return self.getProfileById(loginMemberId, targetMember.getId());
    }

    /**
     * targetMemberId 기반 프로필 조회 (캐시 대상 메서드).
     *
     * <p>캐시 키: {@code targetMemberId} 단일 키<br>
     * 직접 호출 금지 — 반드시 {@code self.getProfileById()} 또는 외부 빈에서 호출해야 한다.</p>
     *
     * <p><b>무효화 시점</b>
     * <ul>
     *   <li>팔로우/언팔로우: FollowService → loginMemberId, targetMemberId 각각 evict</li>
     *   <li>게시물 작성: PostService → loginMemberId(= 작성자) evict</li>
     * </ul></p>
     */
    @Cacheable(value = CacheNames.PROFILE_STATS, key = "#targetMemberId")
    public MemberProfileResponse getProfileById(Long loginMemberId, Long targetMemberId) {
        return memberRepository.getProfileHeader(targetMemberId, loginMemberId);
    }
}
