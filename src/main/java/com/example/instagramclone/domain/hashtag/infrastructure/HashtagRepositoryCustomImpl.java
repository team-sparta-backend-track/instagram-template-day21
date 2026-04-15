package com.example.instagramclone.domain.hashtag.infrastructure;

import com.example.instagramclone.domain.hashtag.api.HashtagMetaResponse;
import com.example.instagramclone.domain.post.api.ProfilePostResponse;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.infrastructure.PostGridQueryHelper;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import static com.example.instagramclone.domain.hashtag.domain.QHashtag.hashtag;
import static com.example.instagramclone.domain.hashtag.domain.QPostHashtag.postHashtag;
import static com.example.instagramclone.domain.post.domain.QPost.post;

import java.util.List;

/**
 * 해시태그 기준 QueryDSL 조회 (JPQL {@code @Query} 문자열 없음).
 *
 * <p>그리드 Tuple·Slice 조립은 {@link PostGridQueryHelper}에 위임하고,
 * 이 클래스는 {@code Post}·{@code PostHashtag}·{@code Hashtag} 조인과 WHERE 만 담당합니다.
 */
@Repository
@RequiredArgsConstructor
public class HashtagRepositoryCustomImpl implements HashtagRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final PostGridQueryHelper postGridQueryHelper;

    @Override
    public Slice<ProfilePostResponse> findProfilePostSliceByHashtagName(String normalizedHashtagName, Pageable pageable) {
        JPQLQuery<Post> baseQuery = queryFactory
                .selectFrom(post)
                .innerJoin(postHashtag).on(postHashtag.post.eq(post))
                .innerJoin(hashtag).on(postHashtag.hashtag.eq(hashtag))
                .where(hashtagNameEq(normalizedHashtagName))
                .orderBy(post.id.desc());

        return postGridQueryHelper.findProfilePostSlice(baseQuery, pageable);
    }

    @Override
    public Slice<ProfilePostResponse> findProfilePostSliceByHashtagNameByCursor(String normalizedHashtagName, Long cursorId, int size) {
        JPQLQuery<Post> baseQuery = queryFactory
                .selectFrom(post)
                .innerJoin(postHashtag).on(postHashtag.post.eq(post))
                .innerJoin(hashtag).on(postHashtag.hashtag.eq(hashtag))
                .where(hashtagNameEq(normalizedHashtagName))
                .orderBy(post.id.desc());

        return postGridQueryHelper.findProfilePostSliceByCursor(baseQuery, cursorId, size);
    }

    @Override
    public List<HashtagMetaResponse> findTopSuggestions(String prefix, int limit) {
        BooleanBuilder where = new BooleanBuilder();
        if (prefix != null && !prefix.isBlank()) {
            where.and(hashtag.name.startsWith(prefix));
        }

        return queryFactory
                .select(Projections.constructor(
                        HashtagMetaResponse.class,
                        hashtag.name,
                        hashtag.postCount          // 🔥 비정규화 컬럼 직접 사용!
                ))
                .from(hashtag)
                .where(where)
                .orderBy(hashtag.postCount.desc(), hashtag.name.asc())
                .limit(limit)
                .fetch();
    }

    /**
     * 정규화된 태그명으로 필터 — 이후 공개 범위·삭제 여부 등과 AND 로 묶기 쉽게 한곳에 둔다.
     */
    private static BooleanExpression hashtagNameEq(String normalizedHashtagName) {
        return hashtag.name.eq(normalizedHashtagName);
    }
}
