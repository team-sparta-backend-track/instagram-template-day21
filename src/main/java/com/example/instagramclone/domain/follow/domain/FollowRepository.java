package com.example.instagramclone.domain.follow.domain;

import com.example.instagramclone.domain.follow.infrastructure.FollowRepositoryCustom;
import com.example.instagramclone.domain.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Follow 엔티티 전용 Repository.
 *
 * Day 13에서는 "팔로우 관계를 저장/삭제하는 쿼리"와
 * "프로필/리스트 화면에 뿌릴 조회 쿼리"를 함께 다루게 된다.
 * Follow는 Member -> Member 셀프 조인이므로,
 * 메서드 이름만 보고도 from / to 방향이 바로 읽히도록 설계하는 것이 중요.
 */
public interface FollowRepository extends JpaRepository<Follow, Long>, FollowRepositoryCustom {

    /** 로그인 유저(fromMember)가 대상 유저(toMember)를 이미 팔로우 중인지 확인한다. */
    boolean existsByFromMemberAndToMember(Member fromMember, Member toMember);

    /** 언팔로우 시, 정확히 한 건의 팔로우 관계(from -> to)를 삭제한다. */
    void deleteByFromMemberAndToMember(Member fromMember, Member toMember);

    /** 특정 유저가 "몇 명을 팔로우하고 있는지" 세는 쿼리. 즉, 팔로잉 수. */
    long countByFromMember(Member fromMember);

    /** 특정 유저를 "몇 명이 팔로우하고 있는지" 세는 쿼리. 즉, 팔로워 수. */
    long countByToMember(Member toMember);
}
