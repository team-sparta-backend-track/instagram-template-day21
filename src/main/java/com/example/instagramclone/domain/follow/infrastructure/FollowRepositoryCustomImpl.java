package com.example.instagramclone.domain.follow.infrastructure;

import com.example.instagramclone.domain.follow.api.FollowMemberResponse;
import com.example.instagramclone.domain.follow.domain.QFollow;
import com.example.instagramclone.domain.member.domain.QMember;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.instagramclone.domain.follow.domain.QFollow.follow;

/**
 * FollowRepositoryCustom의 QueryDSL 구현체.
 *
 * 핵심 목표:
 * - 팔로워/팔로잉 리스트를 최신순으로 Slice 조회한다.
 * - 각 행의 following / me 값을 DB select 절에서 함께 계산한다.
 * - 서비스 레이어의 추가 배치 조회를 제거한다.
 */
@Repository
@RequiredArgsConstructor
public class FollowRepositoryCustomImpl implements FollowRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<FollowMemberResponse> findFollowersWithStatus(Long profileOwnerId, Long loginMemberId, Pageable pageable) {
        QMember targetMember = new QMember("followerMember");
        QFollow followCheck = new QFollow("followCheck");

        BooleanExpression followingExpr = JPAExpressions
                .selectOne()
                .from(followCheck)
                .where(
                        followCheck.fromMember.id.eq(loginMemberId),
                        followCheck.toMember.id.eq(targetMember.id)
                )
                .exists();

        List<FollowMemberResponse> items = queryFactory
                .select(Projections.constructor(
                        FollowMemberResponse.class,
                        targetMember.id,
                        targetMember.username,
                        targetMember.name,
                        targetMember.profileImageUrl,
                        followingExpr,
                        targetMember.id.eq(loginMemberId)
                ))
                .from(follow)
                .join(follow.fromMember, targetMember)
                .where(follow.toMember.id.eq(profileOwnerId))
                .orderBy(follow.createdAt.desc(), follow.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        return toSlice(items, pageable);
    }

    @Override
    public Slice<FollowMemberResponse> findFollowingsWithStatus(Long profileOwnerId, Long loginMemberId, Pageable pageable) {
        QMember targetMember = new QMember("followingMember");
        QFollow followCheck = new QFollow("followCheck");

        BooleanExpression followingExpr = JPAExpressions
                .selectOne()
                .from(followCheck)
                .where(
                        followCheck.fromMember.id.eq(loginMemberId),
                        followCheck.toMember.id.eq(targetMember.id)
                )
                .exists();

        List<FollowMemberResponse> items = queryFactory
                .select(Projections.constructor(
                        FollowMemberResponse.class,
                        targetMember.id,
                        targetMember.username,
                        targetMember.name,
                        targetMember.profileImageUrl,
                        followingExpr,
                        targetMember.id.eq(loginMemberId)
                ))
                .from(follow)
                .join(follow.toMember, targetMember)
                .where(follow.fromMember.id.eq(profileOwnerId))
                .orderBy(follow.createdAt.desc(), follow.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        return toSlice(items, pageable);
    }

    @Override
    public Slice<FollowMemberResponse> findFollowersWithStatusByCursor(Long profileOwnerId, Long loginMemberId, Long cursorId, int size) {
        QMember targetMember = new QMember("followerMember");
        QFollow followCheck = new QFollow("followCheck");

        BooleanExpression followingExpr = JPAExpressions
                .selectOne()
                .from(followCheck)
                .where(
                        followCheck.fromMember.id.eq(loginMemberId),
                        followCheck.toMember.id.eq(targetMember.id)
                )
                .exists();

        var query = queryFactory
                .select(Projections.constructor(
                        FollowMemberResponse.class,
                        targetMember.id,
                        targetMember.username,
                        targetMember.name,
                        targetMember.profileImageUrl,
                        followingExpr,
                        targetMember.id.eq(loginMemberId)
                ))
                .from(follow)
                .join(follow.fromMember, targetMember)
                .where(follow.toMember.id.eq(profileOwnerId));

        if (cursorId != null) {
            query.where(follow.id.lt(cursorId));
        }

        List<FollowMemberResponse> items = query
                .orderBy(follow.id.desc())
                .limit(size + 1L)
                .fetch();

        return toSliceByCursor(items, size);
    }

    @Override
    public Slice<FollowMemberResponse> findFollowingsWithStatusByCursor(Long profileOwnerId, Long loginMemberId, Long cursorId, int size) {
        QMember targetMember = new QMember("followingMember");
        QFollow followCheck = new QFollow("followCheck");

        BooleanExpression followingExpr = JPAExpressions
                .selectOne()
                .from(followCheck)
                .where(
                        followCheck.fromMember.id.eq(loginMemberId),
                        followCheck.toMember.id.eq(targetMember.id)
                )
                .exists();

        var query = queryFactory
                .select(Projections.constructor(
                        FollowMemberResponse.class,
                        targetMember.id,
                        targetMember.username,
                        targetMember.name,
                        targetMember.profileImageUrl,
                        followingExpr,
                        targetMember.id.eq(loginMemberId)
                ))
                .from(follow)
                .join(follow.toMember, targetMember)
                .where(follow.fromMember.id.eq(profileOwnerId));

        if (cursorId != null) {
            query.where(follow.id.lt(cursorId));
        }

        List<FollowMemberResponse> items = query
                .orderBy(follow.id.desc())
                .limit(size + 1L)
                .fetch();

        return toSliceByCursor(items, size);
    }

    private Slice<FollowMemberResponse> toSlice(List<FollowMemberResponse> items, Pageable pageable) {
        boolean hasNext = items.size() > pageable.getPageSize();
        if (hasNext) {
            items = items.subList(0, pageable.getPageSize());
        }
        return new SliceImpl<>(items, pageable, hasNext);
    }

    private Slice<FollowMemberResponse> toSliceByCursor(List<FollowMemberResponse> items, int size) {
        boolean hasNext = items.size() > size;
        if (hasNext) {
            items = items.subList(0, size);
        }
        return new SliceImpl<>(items, Pageable.unpaged(), hasNext);
    }
}
