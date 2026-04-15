package com.example.instagramclone.domain.follow.application;

import com.example.instagramclone.core.constant.CacheNames;
import com.example.instagramclone.core.exception.FollowErrorCode;
import com.example.instagramclone.core.exception.FollowException;
import com.example.instagramclone.domain.follow.api.FollowMemberResponse;
import com.example.instagramclone.domain.follow.api.FollowStatusResponse;
import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.domain.follow.domain.Follow;
import com.example.instagramclone.domain.follow.domain.FollowRepository;
import com.example.instagramclone.domain.member.application.MemberService;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 팔로우 API.
     *
     * <p>팔로우 성공 시 두 사람의 카운트가 바뀐다.
     * loginMember(followingCount↑), targetMember(followerCount↑) 각각의 캐시를 무효화한다.</p>
     * <p>
     * 큰 흐름:
     * 1) 로그인 유저와 대상 유저를 조회한다.
     * 2) 자기 자신을 팔로우하려는지 검사한다.
     * 3) 이미 팔로우 중인지 검사한다.
     * 4) Follow 엔티티를 생성해 저장한다.
     * 5) 대상 유저의 최신 팔로워 수를 다시 조회해 응답에 담는다.
     * <p>
     * 왜 countByToMember()를 다시 호출할까?
     * - 프론트는 이 응답만으로 버튼 상태뿐 아니라 프로필 상단 팔로워 수도 즉시 갱신하고 싶어 한다.
     * - 그래서 "팔로우 성공 여부"만이 아니라 "현재 팔로워 수"까지 함께 돌려준다.
     * <p>
     * 왜 loginMember는 getReferenceById(), targetMember는 findById()를 사용할까?
     * - loginMemberId는 이미 인증 필터가 검증한 JWT에서 꺼낸 값이므로 비교적 신뢰할 수 있다.
     * 따라서 반복 호출이 많은 팔로우 API에서는 매번 SELECT 하지 않고 프록시로 받아 성능을 아낀다.
     * - 반면 targetMemberId는 클라이언트가 URL로 보내는 값이므로 신뢰할 수 없다.
     * 그래서 실제 존재 여부를 즉시 검증하기 위해 findById()로 조회한다.
     * - 즉, "로그인 유저는 성능 최적화", "대상 유저는 정합성 검증"이라는 절충안이다.
     */
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PROFILE_STATS, key = "#loginMemberId"),  // followingCount 변경
            @CacheEvict(value = CacheNames.PROFILE_STATS, key = "#targetMemberId")  // followerCount 변경
    })
    @Transactional
    public FollowStatusResponse follow(Long loginMemberId, Long targetMemberId) {
        // fromMember = 로그인 유저(팔로우를 거는 사람)
        // JWT를 통과한 회원 ID이므로 비교적 신뢰할 수 있어 프록시로 받아 성능을 아낀다.
        Member loginMember = memberService.getReferenceById(loginMemberId);

        // toMember = 팔로우 대상 유저(팔로우를 받는 사람)
        // 클라이언트가 URL로 보내는 값이므로 실제 존재 여부를 즉시 검증한다.
        Member targetMember = memberService.findById(targetMemberId);

        // 자기 자신을 팔로우하는 것은 비즈니스 규칙상 허용하지 않는다.
        if (loginMember.getId().equals(targetMember.getId())) {
            throw new FollowException(FollowErrorCode.CANNOT_FOLLOW_SELF);
        }

        // 같은 (fromMember, toMember) 관계가 이미 있으면 중복 팔로우이므로 예외 처리한다.
        if (followRepository.existsByFromMemberAndToMember(loginMember, targetMember)) {
            throw new FollowException(FollowErrorCode.ALREADY_FOLLOWING);
        }

        // 셀프 조인 관계를 엔티티 한 건으로 표현한다.
        Follow follow = Follow.create(loginMember, targetMember);
        followRepository.save(follow);

        // ✅ 이벤트 발행 — 팔로우 알림
        eventPublisher.publishEvent(new NotificationEvent(
                NotificationType.FOLLOW,
                targetMemberId,          // receiver = 팔로우 당한 사람
                loginMemberId,           // sender = 팔로우 한 사람
                null,                    // target = 없음 (팔로우는 대상이 사람)
                null
        ));

        // 저장 직후 대상 유저의 팔로워 수를 다시 계산해 프론트가 화면 숫자를 즉시 갱신할 수 있게 한다.
        long followerCount = followRepository.countByToMember(targetMember);
        return FollowStatusResponse.of(targetMember.getId(), true, followerCount);
    }

    /**
     * 언팔로우 API.
     *
     * <p>언팔로우 성공 시 두 사람의 카운트가 바뀐다.
     * follow() 와 동일하게 loginMemberId, targetMemberId 각각의 캐시를 무효화한다.</p>
     * <p>
     * 큰 흐름:
     * 1) 로그인 유저와 대상 유저를 조회한다.
     * 2) 실제로 팔로우 관계가 존재하는지 확인한다.
     * 3) 있으면 삭제한다.
     * 4) 삭제 후 대상 유저의 최신 팔로워 수를 다시 조회해 응답에 담는다.
     * <p>
     * 언팔로우도 같은 전략을 따른다.
     * - 로그인 유저는 JWT 기준 프록시 사용
     * - 대상 유저는 실제 존재 검증을 위해 findById() 사용
     */
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PROFILE_STATS, key = "#loginMemberId"),  // followingCount 변경
            @CacheEvict(value = CacheNames.PROFILE_STATS, key = "#targetMemberId")  // followerCount 변경
    })
    @Transactional
    public FollowStatusResponse unfollow(Long loginMemberId, Long targetMemberId) {
        // 언팔로우도 follow()와 동일한 조회 전략을 사용한다.
        Member loginMember = memberService.getReferenceById(loginMemberId);
        Member targetMember = memberService.findById(targetMemberId);

        // 존재하지 않는 관계를 지우려 하면 "삭제할 대상이 없다"는 의미의 예외를 던진다.
        if (!followRepository.existsByFromMemberAndToMember(loginMember, targetMember)) {
            throw new FollowException(FollowErrorCode.FOLLOW_NOT_FOUND);
        }

        followRepository.deleteByFromMemberAndToMember(loginMember, targetMember);

        // 언팔로우 후 줄어든 팔로워 수를 프론트가 바로 반영할 수 있게 함께 내려준다.
        long followerCount = followRepository.countByToMember(targetMember);
        return FollowStatusResponse.of(targetMember.getId(), false, followerCount);
    }

    /**
     * 특정 유저의 팔로워 목록 조회.
     * <p>
     * 예:
     * - B의 프로필에서 "팔로워" 버튼을 눌렀을 때
     * - B를 팔로우하는 사람들(A, C, D...)을 조회한다.
     * <p>
     * 방향:
     * - toMember   = 프로필 주인(B)
     * - fromMember = B를 팔로우하는 사람들(A, C, D...)
     * <p>
     * 페이징:
     * - Follow.createdAt DESC 기준으로 잘라서 조회한다.
     * - 즉, "가장 최근에 팔로우한 사람"이 리스트 맨 위에 온다.
     */
    public SliceResponse<FollowMemberResponse> getFollowers(Long loginMemberId, Long memberId, Pageable pageable) {
        memberService.findById(memberId);
        Slice<FollowMemberResponse> followerSlice = followRepository.findFollowersWithStatus(memberId, loginMemberId, pageable);
        return SliceResponse.of(followerSlice.hasNext(), followerSlice.getContent());
    }

    /** 커서 기반 팔로워 목록 조회 */
    public SliceResponse<FollowMemberResponse> getFollowersByCursor(Long loginMemberId, Long memberId, Long cursorId, int size) {
        memberService.findById(memberId);
        Slice<FollowMemberResponse> slice = followRepository.findFollowersWithStatusByCursor(memberId, loginMemberId, cursorId, size);
        return SliceResponse.of(slice.hasNext(), slice.getContent());
    }

    /**
     * 특정 유저의 팔로잉 목록 조회.
     * <p>
     * 예:
     * - B의 프로필에서 "팔로잉" 버튼을 눌렀을 때
     * - B가 팔로우하고 있는 사람들(X, Y, Z...)을 조회한다.
     * <p>
     * 방향:
     * - fromMember = 프로필 주인(B)
     * - toMember   = B가 팔로우하는 사람들(X, Y, Z...)
     * <p>
     * 페이징:
     * - Follow.createdAt DESC 기준으로 잘라서 조회한다.
     * - 즉, 프로필 주인이 "가장 최근에 팔로우한 사람"이 리스트 맨 위에 온다.
     */
    public SliceResponse<FollowMemberResponse> getFollowings(Long loginMemberId, Long memberId, Pageable pageable) {
        memberService.findById(memberId);
        Slice<FollowMemberResponse> followingSlice = followRepository.findFollowingsWithStatus(memberId, loginMemberId, pageable);
        return SliceResponse.of(followingSlice.hasNext(), followingSlice.getContent());
    }

    /** 커서 기반 팔로잉 목록 조회 */
    public SliceResponse<FollowMemberResponse> getFollowingsByCursor(Long loginMemberId, Long memberId, Long cursorId, int size) {
        memberService.findById(memberId);
        Slice<FollowMemberResponse> slice = followRepository.findFollowingsWithStatusByCursor(memberId, loginMemberId, cursorId, size);
        return SliceResponse.of(slice.hasNext(), slice.getContent());
    }

    /**
     * 로그인 유저가 특정 대상 유저를 팔로우 중인지 여부를 계산한다.
     * <p>
     * 이 메서드는 "팔로우 상태만 필요할 때" 재사용한다.
     * 예: 프로필 헤더의 팔로우 버튼 상태, 검색 결과 카드의 팔로우 버튼 상태.
     * <p>
     * 파라미터를 Member 엔티티로 받는 이유:
     * - 상위 서비스에서 이미 회원을 조회해 둔 경우 그대로 재사용할 수 있다.
     * - 같은 회원을 findById()로 두 번 조회하는 중복 쿼리를 피할 수 있다.
     */
    public boolean isFollowing(Member loginMember, Member targetMember) {
        // 자기 자신은 "팔로우 중" 개념을 사용하지 않으므로 false 처리한다.
        if (loginMember.getId().equals(targetMember.getId())) {
            return false;
        }

        // 핵심 쿼리: (로그인 유저 -> 대상 유저) 팔로우 행이 실제로 존재하는지 확인
        return followRepository.existsByFromMemberAndToMember(loginMember, targetMember);
    }

}
