package com.example.instagramclone.domain.member.infrastructure;

import com.example.instagramclone.domain.member.api.MemberProfileResponse;
import com.example.instagramclone.domain.member.api.MemberSummary;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.QMember;
import com.example.instagramclone.domain.follow.domain.QFollow;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import static com.example.instagramclone.domain.follow.domain.QFollow.follow;
import static com.example.instagramclone.domain.member.domain.QMember.*;
import static com.example.instagramclone.domain.post.domain.QPost.post;

/**
 * MemberRepositoryCustom의 QueryDSL 구현체.
 *
 * [네이밍 컨벤션 필수]
 * 클래스명 = "{커스텀 인터페이스명}Impl" → MemberRepositoryCustomImpl
 * Spring Data JPA는 fragment 인터페이스명 + "Impl" 접미사로 구현체를 탐색합니다.
 * 이름이 일치하지 않으면 커스텀 구현체를 찾지 못하고,
 * searchByUsername 같은 메서드를 JPA 쿼리 파생 메서드로 해석해 버립니다.
 *
 * [JPQL vs QueryDSL 비교]
 * JPQL:    "SELECT m FROM Member m WHERE LOWER(m.username) LIKE LOWER(CONCAT('%',:kw,'%'))"
 *          → 문자열이라 오타가 컴파일 시점에 잡히지 않음
 * QueryDSL: member.username.containsIgnoreCase(keyword)
 *           → 자바 코드이므로 오타 즉시 컴파일 에러
 */
@Repository
@RequiredArgsConstructor
public class MemberRepositoryCustomImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Member> searchByUsername(String keyword) {

        return queryFactory
                .selectFrom(member)
                .where(member.username.containsIgnoreCase(keyword))
                .fetch();
    }

    
    @Override
    public MemberProfileResponse getProfileHeader(Long targetMemberId, Long loginMemberId) {

        /*
         * Native SQL(동등 로직) 예시
         *
         SELECT
                m.username,
                m.id,
                m.name,
                m.profile_image_url,
                (SELECT COUNT(*) FROM follows f WHERE f.to_member_id = m.id) AS follower_count,
                (SELECT COUNT(*) FROM follows f WHERE f.from_member_id = m.id) AS following_count,
                (SELECT COUNT(*) FROM posts p WHERE p.member_id = m.id) AS post_count,
                EXISTS (
                        SELECT 1
                        FROM follows f2
                        WHERE f2.from_member_id = :loginMemberId
                                AND f2.to_member_id = m.id
                        ) AS is_following,
                (m.id = :loginMemberId) AS is_current_user
                FROM users m
                WHERE m.id = :targetMemberId;
         
         * QueryDSL 코드의 각 Expression/exists가 위 SQL의 스칼라 서브쿼리/EXISTS에 대응됩니다.
         */

        QMember target = member;

        // 팔로워/팔로잉 수: Follow 테이블 방향(from/to)에 맞춰 COUNT
        // - followerCount: 누가 target을 팔로우 중인가? (follow.toMember.id = target.id)
        Expression<Long> followerCountExpr = JPAExpressions
                .select(follow.count())
                .from(follow)
                .where(follow.toMember.id.eq(target.id));

        // - followingCount: target이 누굴 팔로우 중인가? (follow.fromMember.id = target.id)
        Expression<Long> followingCountExpr = JPAExpressions
                .select(follow.count())
                .from(follow)
                .where(follow.fromMember.id.eq(target.id));

        // - postCount: target이 작성한 게시물 수
        Expression<Long> postCountExpr = JPAExpressions
                .select(post.count())
                .from(post)
                .where(post.writer.id.eq(target.id));

        // isFollowing: loginMember가 target을 이미 팔로우 중인가?
        QFollow followCheck = new QFollow("followCheck");
        BooleanExpression isFollowingExpr = JPAExpressions
                .selectOne()
                .from(followCheck)
                .where(
                        followCheck.fromMember.id.eq(loginMemberId),
                        followCheck.toMember.id.eq(target.id)
                )
                .exists();

        BooleanExpression isCurrentUserExpr = target.id.eq(loginMemberId);

        return queryFactory
                .select(Projections.constructor(
                        MemberProfileResponse.class,
                        target.id,
                        target.username,
                        target.name,
                        target.profileImageUrl,
                        followerCountExpr,
                        followingCountExpr,
                        postCountExpr,
                        isFollowingExpr,
                        isCurrentUserExpr
                ))
                .from(target)
                .where(target.id.eq(targetMemberId))
                .fetchOne();
    }

    @Override
    public Slice<MemberSummary> searchByUsernameByCursor(String keyword, Long cursorId, int size) {

        List<MemberSummary> items = queryFactory
                .select(Projections.constructor(
                        MemberSummary.class,
                        member.id,
                        member.username,
                        member.profileImageUrl
                ))
                .from(member)
                .where(
                        member.username.containsIgnoreCase(keyword),
                        ltMemberCursorId(cursorId)
                )
                .orderBy(member.id.desc())
                .limit(size + 1L)
                .fetch();

        boolean hasNext = items.size() > size;
        if (hasNext) {
            items = items.subList(0, size);
        }

        return new SliceImpl<>(items, Pageable.unpaged(), hasNext);
    }

    /**
     * Member용 커서 조건 — 피드의 ltCursorId와 동일한 패턴.
     */
    private BooleanExpression ltMemberCursorId(Long cursorId) {
        return cursorId != null ? member.id.lt(cursorId) : null;
    }
}
