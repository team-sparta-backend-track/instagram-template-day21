package com.example.instagramclone.domain.comment.infrastructure;

import com.example.instagramclone.domain.comment.domain.Comment;
import com.example.instagramclone.domain.comment.domain.CommentRepository;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostRepository;
import com.example.instagramclone.domain.post.infrastructure.PostGridQueryHelper;
import com.example.instagramclone.infrastructure.persistence.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CommentRepositoryCustomImpl} QueryDSL 쿼리 통합 테스트 (@DataJpaTest + H2).
 *
 * <p>QComment 생성·컴파일이 선행되어야 하며, {@link QueryDslConfig} 로 {@link com.querydsl.jpa.impl.JPAQueryFactory} 를 주입합니다.
 */
@DataJpaTest
@Import({QueryDslConfig.class, PostGridQueryHelper.class})
class CommentRepositoryCustomImplTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    private Member writer;
    private Post post;

    @BeforeEach
    void setUp() {
        writer = memberRepository.save(Member.builder()
                .username("comment_writer")
                .password("pw")
                .email("cw@test.com")
                .name("작성자")
                .build());

        post = postRepository.save(Post.builder()
                .content("게시글")
                .writer(writer)
                .build());
    }

    @Test
    @DisplayName("findRootCommentsWithReplyCountByPostId: 원댓만·시간순 + 상관 서브쿼리 replyCount")
    void findRootComments_with_reply_count_subquery() {
        // given: 원댓글 2개 + 대댓글 2개(첫 원댓에만)
        Comment rootOld = commentRepository.save(Comment.create(post, writer, "옛날 원댓", null));
        Comment rootNew = commentRepository.save(Comment.create(post, writer, "최신 원댓", null));
        commentRepository.save(Comment.create(post, writer, "대댓1", rootOld));
        commentRepository.save(Comment.create(post, writer, "대댓2", rootOld));

        Pageable pageable = PageRequest.of(0, 10);

        // when
        Slice<RootCommentListRow> slice =
                commentRepository.findRootCommentsWithReplyCountByPostId(post.getId(), pageable);

        // then: 대댓 행은 목록에 안 나옴, replyCount는 서브쿼리로 2 / 0
        assertThat(slice.getContent()).hasSize(2);
        assertThat(slice.getContent().get(0).comment().getId()).isEqualTo(rootOld.getId());
        assertThat(slice.getContent().get(0).replyCount()).isEqualTo(2L);
        assertThat(slice.getContent().get(1).comment().getId()).isEqualTo(rootNew.getId());
        assertThat(slice.getContent().get(1).replyCount()).isZero();
        assertThat(slice.hasNext()).isFalse();
    }

    @Test
    @DisplayName("existsRootCommentForReplies: 해당 게시글의 원댓글 id만 true")
    void existsRootCommentForReplies_only_matching_root() {
        Comment root = commentRepository.save(Comment.create(post, writer, "원댓", null));
        Comment reply = commentRepository.save(Comment.create(post, writer, "대댓", root));

        Post otherPost = postRepository.save(Post.builder()
                .content("다른 글")
                .writer(writer)
                .build());
        Comment rootOnOther = commentRepository.save(Comment.create(otherPost, writer, "다른글 원댓", null));

        assertThat(commentRepository.existsRootCommentForReplies(post.getId(), root.getId())).isTrue();
        assertThat(commentRepository.existsRootCommentForReplies(post.getId(), reply.getId())).isFalse();
        assertThat(commentRepository.existsRootCommentForReplies(post.getId(), rootOnOther.getId())).isFalse();
        assertThat(commentRepository.existsRootCommentForReplies(post.getId(), 99999L)).isFalse();
    }

    @Test
    @DisplayName("findRepliesByRootComment: parent_id 일치·시간순·Slice")
    void findReplies_under_root_ordered_slice() {
        Comment root = commentRepository.save(Comment.create(post, writer, "원댓", null));
        Comment otherRoot = commentRepository.save(Comment.create(post, writer, "다른 원댓", null));
        Comment r1 = commentRepository.save(Comment.create(post, writer, "대댓1", root));
        Comment r2 = commentRepository.save(Comment.create(post, writer, "대댓2", root));
        commentRepository.save(Comment.create(post, writer, "다른 스레드", otherRoot));

        Pageable pageable = PageRequest.of(0, 2);

        Slice<Comment> slice = commentRepository.findRepliesByRootComment(post.getId(), root.getId(), pageable);

        assertThat(slice.getContent()).hasSize(2);
        assertThat(slice.getContent().get(0).getId()).isEqualTo(r1.getId());
        assertThat(slice.getContent().get(1).getId()).isEqualTo(r2.getId());
        assertThat(slice.hasNext()).isFalse();

        Slice<Comment> page2 = commentRepository.findRepliesByRootComment(
                post.getId(), root.getId(), PageRequest.of(1, 1));
        assertThat(page2.getContent()).hasSize(1);
        assertThat(page2.hasNext()).isFalse();
    }

    @Test
    @DisplayName("findRepliesByRootComment: size+1로 hasNext 판별")
    void findReplies_hasNext_when_more_than_page() {
        Comment root = commentRepository.save(Comment.create(post, writer, "원댓", null));
        commentRepository.save(Comment.create(post, writer, "대댓1", root));
        commentRepository.save(Comment.create(post, writer, "대댓2", root));
        commentRepository.save(Comment.create(post, writer, "대댓3", root));

        Slice<Comment> slice = commentRepository.findRepliesByRootComment(
                post.getId(), root.getId(), PageRequest.of(0, 2));

        assertThat(slice.getContent()).hasSize(2);
        assertThat(slice.hasNext()).isTrue();
    }
}
