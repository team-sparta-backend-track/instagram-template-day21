package com.example.instagramclone.domain.member.infrastructure;

import com.example.instagramclone.domain.member.api.MemberProfileResponse;
import com.example.instagramclone.domain.member.api.MemberSummary;
import com.example.instagramclone.domain.member.domain.Member;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * MemberRepository에 QueryDSL 기반 커스텀 쿼리를 추가하기 위한 인터페이스.
 *
 * [커스텀 리포지토리 패턴]
 * Spring Data JPA(save, findById 등)와 QueryDSL 커스텀 쿼리를
 * 하나의 MemberRepository로 합치기 위한 중간 인터페이스입니다.
 *
 * MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom
 */
public interface MemberRepositoryCustom {

    /**
     * username에 keyword가 포함된 회원을 대소문자 구분 없이 검색합니다.
     * SQL: WHERE LOWER(username) LIKE LOWER('%keyword%')
     */
    List<Member> searchByUsername(String keyword);

    /**
     * Day 15 Live Coding: 프로필 헤더 통계 조회
     * 팔로워/팔로잉/게시물 수, 팔로우 여부를 한 번에 조회합니다.
     */
    MemberProfileResponse getProfileHeader(Long targetMemberId, Long loginMemberId);

    /** 커서 기반 유저 검색 — MemberSummary DTO projection */
    Slice<MemberSummary> searchByUsernameByCursor(String keyword, Long cursorId, int size);
}
