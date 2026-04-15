package com.example.instagramclone.domain.post.infrastructure;

import com.example.instagramclone.domain.post.api.ProfilePostResponse;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.QPostImage;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static com.example.instagramclone.domain.comment.domain.QComment.comment;
import static com.example.instagramclone.domain.post.domain.QPost.post;

/**
 * 프로필 그리드 · 태그 피드 ·(향후 좋아요·북마크 그리드)에서 공통으로 쓰는 QueryDSL 조각.
 * <p>
 * {@code XXXRepositoryCustomImpl}끼리 서로 주입하지 않고, 이 헬퍼만 바라보게 해
 * Repository 계층의 책임·순환 참조 위험을 줄입니다.
 */
@Component
public class PostGridQueryHelper {

    /**
     * 호출자가 {@code selectFrom(post)}·조인·{@code where}·{@code orderBy} 까지 구성한 뒤 넘기면,
     * 썸네일·다중 이미지·원댓글 수 서브쿼리와 Tuple 매핑·Slice({@code limit+1}) 처리를 한 번에 수행합니다.
     */
    public Slice<ProfilePostResponse> findProfilePostSlice(JPQLQuery<Post> baseQuery, Pageable pageable) {
        QPostImage pi = QPostImage.postImage;

        Expression<String> thumbnailExpr = thumbnailUrlSubquery(pi);
        BooleanExpression multipleImagesExpr = multipleImagesExists(pi);
        Expression<Long> commentCountExpr = rootCommentCountSubquery();

        List<Tuple> tuples = baseQuery
                .select(post, thumbnailExpr, multipleImagesExpr, commentCountExpr)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        if (tuples.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }

        boolean hasNext = tuples.size() > pageable.getPageSize();
        if (hasNext) {
            tuples = tuples.subList(0, pageable.getPageSize());
        }

        List<ProfilePostResponse> content = tuples.stream()
                .map(t -> {
                    Post p = t.get(post);
                    String thumbnailUrl = t.get(1, String.class);
                    Boolean multipleImages = t.get(2, Boolean.class);
                    Long cc = t.get(3, Long.class);

                    long commentCountLong = cc != null ? cc : 0L;

                    return new ProfilePostResponse(
                            p.getId(),
                            thumbnailUrl,
                            Boolean.TRUE.equals(multipleImages),
                            p.getLikeCount(),
                            commentCountLong
                    );
                })
                .toList();

        return new SliceImpl<>(content, pageable, hasNext);
    }

    /**
     * 커서 기반 프로필 그리드 조회.
     * baseQuery에 이미 where·orderBy(post.id.desc())가 설정되어 있어야 합니다.
     */
    public Slice<ProfilePostResponse> findProfilePostSliceByCursor(JPQLQuery<Post> baseQuery, Long cursorId, int size) {
        QPostImage pi = QPostImage.postImage;

        Expression<String> thumbnailExpr = thumbnailUrlSubquery(pi);
        BooleanExpression multipleImagesExpr = multipleImagesExists(pi);
        Expression<Long> commentCountExpr = rootCommentCountSubquery();

        if (cursorId != null) {
            baseQuery.where(post.id.lt(cursorId));
        }

        List<Tuple> tuples = baseQuery
                .select(post, thumbnailExpr, multipleImagesExpr, commentCountExpr)
                .limit(size + 1L)
                .fetch();

        if (tuples.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), Pageable.unpaged(), false);
        }

        boolean hasNext = tuples.size() > size;
        if (hasNext) {
            tuples = tuples.subList(0, size);
        }

        List<ProfilePostResponse> content = tuples.stream()
                .map(t -> {
                    Post p = t.get(post);
                    String thumbnailUrl = t.get(1, String.class);
                    Boolean multipleImages = t.get(2, Boolean.class);
                    Long cc = t.get(3, Long.class);

                    long commentCountLong = cc != null ? cc : 0L;

                    return new ProfilePostResponse(
                            p.getId(),
                            thumbnailUrl,
                            Boolean.TRUE.equals(multipleImages),
                            p.getLikeCount(),
                            commentCountLong
                    );
                })
                .toList();

        return new SliceImpl<>(content, Pageable.unpaged(), hasNext);
    }

    private static Expression<String> thumbnailUrlSubquery(QPostImage pi) {
        return JPAExpressions
                .select(pi.imageUrl)
                .from(pi)
                .where(
                        pi.post.id.eq(post.id),
                        pi.imgOrder.eq(1)
                )
                .limit(1);
    }

    private static BooleanExpression multipleImagesExists(QPostImage pi) {
        return JPAExpressions
                .selectOne()
                .from(pi)
                .where(
                        pi.post.id.eq(post.id),
                        pi.imgOrder.gt(1)
                )
                .exists();
    }

    private static Expression<Long> rootCommentCountSubquery() {
        return JPAExpressions
                .select(comment.id.count())
                .from(comment)
                .where(
                        comment.post.id.eq(post.id),
                        comment.parent.isNull()
                );
    }
}
