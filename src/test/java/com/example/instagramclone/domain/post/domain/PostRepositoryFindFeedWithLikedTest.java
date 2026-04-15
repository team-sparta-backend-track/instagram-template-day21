package com.example.instagramclone.domain.post.domain;

import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.post.infrastructure.PostFeedRow;
import com.example.instagramclone.domain.post.infrastructure.PostGridQueryHelper;
import com.example.instagramclone.infrastructure.persistence.QueryDslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryDSL findFeedWithLiked — EXISTS 서브쿼리로 liked (과제 통합 테스트).
 */
@DataJpaTest
@Import({QueryDslConfig.class, PostGridQueryHelper.class})
class PostRepositoryFindFeedWithLikedTest {

    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("findFeedWithLiked: 좋아요 넣은 글만 liked true, 한 SELECT에 post+exists")
    void feed_with_liked_subquery() {
        Member writer = memberRepository.save(Member.builder()
                .username("w").password("p").email("w@test.com").name("W").build());
        Member liker = memberRepository.save(Member.builder()
                .username("l").password("p").email("l@test.com").name("L").build());

        Post p1 = postRepository.save(Post.builder().content("글1").writer(writer).build());
        Post p2 = postRepository.save(Post.builder().content("글2").writer(writer).build());
        postLikeRepository.save(PostLike.create(liker, p2));

        Slice<PostFeedRow> slice = postRepository.findFeedWithLiked(PageRequest.of(0, 10), liker.getId());

        assertThat(slice.getContent()).hasSize(2);
        // id desc → p2 먼저
        assertThat(slice.getContent().get(0).post().getId()).isEqualTo(p2.getId());
        assertThat(slice.getContent().get(0).liked()).isTrue();
        assertThat(slice.getContent().get(1).post().getId()).isEqualTo(p1.getId());
        assertThat(slice.getContent().get(1).liked()).isFalse();
    }
}
