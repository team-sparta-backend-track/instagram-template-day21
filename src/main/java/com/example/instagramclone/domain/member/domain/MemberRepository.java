package com.example.instagramclone.domain.member.domain;

import com.example.instagramclone.domain.member.infrastructure.MemberRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);

    Optional<Member> findByEmail(String email);
    Optional<Member> findByUsername(String username);
    Optional<Member> findByPhone(String phone);

    /** 멘션용 IN 조회 — 실제 존재하는 유저만 필터링 */
    List<Member> findAllByUsernameIn(Collection<String> usernames);
}
