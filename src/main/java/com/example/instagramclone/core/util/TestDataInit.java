package com.example.instagramclone.core.util;

import com.example.instagramclone.domain.comment.domain.Comment;
import com.example.instagramclone.domain.comment.domain.CommentRepository;
import com.example.instagramclone.domain.follow.domain.Follow;
import com.example.instagramclone.domain.follow.domain.FollowRepository;
import com.example.instagramclone.domain.hashtag.application.HashtagService;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostImage;
import com.example.instagramclone.domain.post.domain.PostImageRepository;
import com.example.instagramclone.domain.post.domain.PostLike;
import com.example.instagramclone.domain.post.domain.PostLikeRepository;
import com.example.instagramclone.domain.post.domain.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Order(1)
@Component
@RequiredArgsConstructor
public class TestDataInit implements ApplicationRunner {

    private static final List<MemberSeed> MEMBER_SEEDS = List.of(
            new MemberSeed("kuromi", "쿠로미",
                    "https://slbs.shop/web/product/big/202403/ac79148d83434f2d513be1318fe6a8c0.jpg"),
            new MemberSeed("mamel", "마이멜로디",
                    "https://thumbnail8.coupangcdn.com/thumbnails/remote/492x492ex/image/retail/images/7129265199492958-abf55714-20d0-4e3c-89cd-218a7c7b177d.jpg"),
            new MemberSeed("pikachu", "피카츄",
                    "https://mblogthumb-phinf.pstatic.net/20160817_259/retspe_14714118890125sC2j_PNG/%C7%C7%C4%AB%C3%F2_%281%29.png?type=w800"),
            new MemberSeed("kitty", "키티",
                    "https://img.khan.co.kr/news/2014/11/03/l_2014110401000298000026001.jpg"),
            new MemberSeed("heartping", "하츄핑",
                    "https://i.ytimg.com/vi/NLfP4ewUf1c/oardefault.jpg")
    );

    private final MemberRepository memberRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;
    private final HashtagService hashtagService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (memberRepository.count() > 0) return;

        String password = passwordEncoder.encode("abc1234!");
        List<Member> savedMembers = new ArrayList<>();
        List<List<Post>> postsByMember = new ArrayList<>();

        for (int i = 0; i < MEMBER_SEEDS.size(); i++) {
            MemberSeed seed = MEMBER_SEEDS.get(i);

            Member member = Member.builder()
                    .username(seed.username())
                    .password(password)
                    .name(seed.name())
                    .email(seed.username() + "@test.com")
                    .profileImageUrl(seed.profileImageUrl())
                    .build();

            Member savedMember = memberRepository.save(member);
            savedMembers.add(savedMember);
            List<Post> savedPostsOfMember = new ArrayList<>();

            for (int p = 1; p <= 5; p++) {
                String content = buildPostCaptionWithHashtags(seed, p);
                Post post = Post.builder()
                        .content(content)
                        .writer(savedMember)
                        .build();
                Post savedPost = postRepository.save(post);
                savedPostsOfMember.add(savedPost);
                hashtagService.syncHashtagsForPost(savedPost.getId(), savedPost.getContent());

                for (int img = 1; img <= 2; img++) {
                    PostImage postImage = PostImage.builder()
                            .post(savedPost)
                            .imageUrl("https://picsum.photos/600/600?random=" + (i * 10 + p * 2 + img))
                            .imgOrder(img)
                            .build();
                    postImageRepository.save(postImage);
                }
            }

            postsByMember.add(savedPostsOfMember);
        }

        // 팔로우 테스트용 관계망 구성
        // kuromi -> mamel, pikachu, heartping
        // mamel -> kuromi, kitty
        // pikachu -> kuromi, mamel, heartping
        // kitty -> kuromi, mamel, heartping
        // heartping -> kitty
        seedFollowRelations(savedMembers);

        // 좋아요 테스트용 관계망 구성
        // 각 회원의 대표 게시글 몇 개에 서로 다른 회원들이 좋아요를 누른 상황을 만든다.
        seedPostLikeRelations(savedMembers, postsByMember);

        // 댓글·대댓글 (Day 14 Step 3 API: 원댓글 목록 + replyCount) 테스트용
        seedCommentThreads(savedMembers, postsByMember);

        System.out.println("테스트용 계정 5개, 팔로우, 좋아요, 댓글/대댓글, 해시태그 동기화, 피드 세팅 완료! (게시물 총 25개)");
    }

    /**
     * 캡션에 서로 다른 해시태그 조합을 넣어 태그 피드·검색 데모가 되도록 합니다.
     * 공통 태그(#일상 #데모 등)는 여러 글에 반복되어 태그별 게시물 수가 쌓입니다.
     */
    private static String buildPostCaptionWithHashtags(MemberSeed seed, int postNumber) {
        String body = seed.name() + "의 " + postNumber + "번째 일상 피드입니다~!";
        String sharedByPost = switch (postNumber) {
            case 1 -> "#일상 #데모 #테스트";
            case 2 -> "#맛집 #카페 #일상";
            case 3 -> "#산책 #주말 #데일리";
            case 4 -> "#OOTD #셀카 #피드";
            case 5 -> "#친구 #추억 #일상";
            default -> "#테스트";
        };
        String characterTags = characterHashtags(seed.username());
        return body + " " + sharedByPost + " " + characterTags;
    }

    /** 캐릭터(회원)별로 한 번 더 구분되는 태그 — 태그 피드에서 작성자 성격이 드러나게 */
    private static String characterHashtags(String username) {
        return switch (username) {
            case "kuromi" -> "#쿠로미 #보라매력";
            case "mamel" -> "#마이멜로디 #핑크";
            case "pikachu" -> "#피카츄 #전기충전금지";
            case "kitty" -> "#키티 #리본";
            case "heartping" -> "#하츄핑 #티니핑";
            default -> "#sanrio";
        };
    }

    private void seedFollowRelations(List<Member> members) {
        Member kuromi = members.get(0);
        Member mamel = members.get(1);
        Member pikachu = members.get(2);
        Member kitty = members.get(3);
        Member heartping = members.get(4);

        followRepository.save(Follow.create(kuromi, mamel));
        followRepository.save(Follow.create(kuromi, pikachu));
        followRepository.save(Follow.create(kuromi, heartping));

        followRepository.save(Follow.create(mamel, kuromi));
        followRepository.save(Follow.create(mamel, kitty));

        followRepository.save(Follow.create(pikachu, kuromi));
        followRepository.save(Follow.create(pikachu, mamel));
        followRepository.save(Follow.create(pikachu, heartping));

        followRepository.save(Follow.create(kitty, kuromi));
        followRepository.save(Follow.create(kitty, mamel));
        followRepository.save(Follow.create(kitty, heartping));

        followRepository.save(Follow.create(heartping, kitty));
    }

    private void seedPostLikeRelations(List<Member> members, List<List<Post>> postsByMember) {
        Member kuromi = members.get(0);
        Member mamel = members.get(1);
        Member pikachu = members.get(2);
        Member kitty = members.get(3);
        Member heartping = members.get(4);

        // 각 회원의 게시글에 좋아요가 고르게 퍼져 보이도록
        // "인기글", "중간 반응 글", "조용한 글"이 섞이게 구성한다.

        // kuromi 게시글들
        addLike(mamel, postsByMember.get(0).get(0));
        addLike(pikachu, postsByMember.get(0).get(0));
        addLike(kitty, postsByMember.get(0).get(0));
        addLike(heartping, postsByMember.get(0).get(1));
        addLike(mamel, postsByMember.get(0).get(2));
        addLike(kitty, postsByMember.get(0).get(3));

        // mamel 게시글들
        addLike(kuromi, postsByMember.get(1).get(0));
        addLike(pikachu, postsByMember.get(1).get(0));
        addLike(kitty, postsByMember.get(1).get(1));
        addLike(heartping, postsByMember.get(1).get(1));
        addLike(kuromi, postsByMember.get(1).get(4));

        // pikachu 게시글들
        addLike(kuromi, postsByMember.get(2).get(0));
        addLike(mamel, postsByMember.get(2).get(0));
        addLike(heartping, postsByMember.get(2).get(0));
        addLike(kitty, postsByMember.get(2).get(2));
        addLike(kuromi, postsByMember.get(2).get(3));
        addLike(mamel, postsByMember.get(2).get(4));

        // kitty 게시글들
        addLike(kuromi, postsByMember.get(3).get(0));
        addLike(heartping, postsByMember.get(3).get(0));
        addLike(mamel, postsByMember.get(3).get(1));
        addLike(pikachu, postsByMember.get(3).get(1));
        addLike(kuromi, postsByMember.get(3).get(4));

        // heartping 게시글들
        addLike(kuromi, postsByMember.get(4).get(0));
        addLike(mamel, postsByMember.get(4).get(0));
        addLike(kitty, postsByMember.get(4).get(0));
        addLike(pikachu, postsByMember.get(4).get(2));
        addLike(mamel, postsByMember.get(4).get(3));
        addLike(kitty, postsByMember.get(4).get(4));
    }

    private void addLike(Member member, Post post) {
        postLikeRepository.save(PostLike.create(member, post));
        // post_like 레코드와 비정규화 likeCount를 함께 맞춰 둔다.
        post.changeLikeCountBy(1);
    }

    /**
     * 쿠로미·마이멜로디·피카츄·키티·하츄핑이 서로의 게시글에 원댓글을 달고, 일부에만 대댓글(2-depth)을 이어 붙인다.
     * Step 3 {@code GET /api/posts/{postId}/comments} 로 원댓글 + {@code replyCount} 를 확인할 때 사용한다.
     * 쿠로미 1번 게시글은 원댓·대댓이 많아 {@code GROUP BY parent_id} / {@code COUNT} 네이티브 SQL 예시로 쓰기 좋다.
     */
    private void seedCommentThreads(List<Member> members, List<List<Post>> postsByMember) {
        Member kuromi = members.get(0);
        Member mamel = members.get(1);
        Member pikachu = members.get(2);
        Member kitty = members.get(3);
        Member heartping = members.get(4);

        // --- 쿠로미(kuromi) 1번 게시글: 원댓·대댓 풍부 (네이티브 SQL GROUP BY parent_id 집계 예시용) ---
        // 원댓 6개, 대댓 분포: 4 / 3 / 2 / 2 / 1 / 0 
        Post kuromiPost0 = postsByMember.get(0).get(0);
        Comment kR1 = commentRepository.save(Comment.create(kuromiPost0, mamel, "쿠로미도 오늘 너무 귀여워~", null));
        Comment kR2 = commentRepository.save(Comment.create(kuromiPost0, pikachu, "피카피카! 응원이야", null));
        Comment kR3 = commentRepository.save(Comment.create(kuromiPost0, heartping, "하츄하츄! 좋아요", null));
        Comment kR4 = commentRepository.save(Comment.create(kuromiPost0, kitty, "리본이랑 배경이 찰떡이야", null));
        Comment kR5 = commentRepository.save(Comment.create(kuromiPost0, mamel, "사진 각도 미쳤다… 또 보러 올게", null));
        commentRepository.save(Comment.create(kuromiPost0, mamel, "쿠로미 피드는 힐링이야 (대댓 없는 원댓 예시)", null));

        commentRepository.save(Comment.create(kuromiPost0, kuromi, "마이멜로디 고마워 💜", kR1));
        commentRepository.save(Comment.create(kuromiPost0, pikachu, "멜로디 칭찬 인정이야 피카", kR1));
        commentRepository.save(Comment.create(kuromiPost0, kitty, "쿠로미×멜로디 조합 좋다", kR1));
        commentRepository.save(Comment.create(kuromiPost0, heartping, "하츄핑도 동의!", kR1));

        commentRepository.save(Comment.create(kuromiPost0, kuromi, "피카츄 고마워, 충전은 실내에서만!", kR2));
        commentRepository.save(Comment.create(kuromiPost0, kitty, "피카츄 옆자리 비워줄게", kR2));
        commentRepository.save(Comment.create(kuromiPost0, mamel, "피카츄 응원 댓글 귀엽다", kR2));

        commentRepository.save(Comment.create(kuromiPost0, kuromi, "하츄핑도 좋아요 눌렀지?", kR3));
        commentRepository.save(Comment.create(kuromiPost0, pikachu, "하츄하츄에 피카 한 스푼", kR3));

        commentRepository.save(Comment.create(kuromiPost0, kuromi, "키티 눈썰미 좋다", kR4));
        commentRepository.save(Comment.create(kuromiPost0, heartping, "키티랑 쿠로미 케미 좋아", kR4));

        commentRepository.save(Comment.create(kuromiPost0, kuromi, "멜로디 또 와줘서 고마워", kR5));

        // --- 마이멜로디(mamel) 1번 게시글: 원댓 2개 + 대댓 1개 ---
        Post mamelPost0 = postsByMember.get(1).get(0);
        Comment mRootKuromi = commentRepository.save(Comment.create(mamelPost0, kuromi, "핑크 헤어밴드 너무 잘 어울려", null));
        commentRepository.save(Comment.create(mamelPost0, pikachu, "멜로디 노래 듣고 왔어", null));
        commentRepository.save(Comment.create(mamelPost0, mamel, "쿠로미도 내가 사랑해~", mRootKuromi));

        // --- 피카츄(pikachu) 1번 게시글: 원댓 1개 + 대댓 1개 ---
        Post pikaPost0 = postsByMember.get(2).get(0);
        Comment pRootKuromi = commentRepository.save(Comment.create(pikaPost0, kuromi, "전기 타면 안 돼! 조심해", null));
        commentRepository.save(Comment.create(pikaPost0, heartping, "하츄핑이 응원할게!", pRootKuromi));

        // --- 키티(kitty) 1번 게시글: 원댓만 2개 (대댓 없음 → replyCount 0) ---
        Post kittyPost0 = postsByMember.get(3).get(0);
        commentRepository.save(Comment.create(kittyPost0, kuromi, "리본이 오늘따라 반짝반짝", null));
        commentRepository.save(Comment.create(kittyPost0, mamel, "키티 짱", null));

        // --- 하츄핑(heartping) 1번 게시글: 원댓 2개 + 대댓 1개 ---
        Post heartPost0 = postsByMember.get(4).get(0);
        Comment hRootKuromi = commentRepository.save(Comment.create(heartPost0, kuromi, "캐치 티니핑 파이팅!", null));
        commentRepository.save(Comment.create(heartPost0, kitty, "키티랑 하츄핑 투샷 보고 싶다", null));
        commentRepository.save(Comment.create(heartPost0, heartping, "하츄핑이 직접 답글 달았어!", hRootKuromi));

        // --- 추가: 쿠로미 2번 게시글에만 가벼운 원댓 1개 (다른 글과 비교용) ---
        Post kuromiPost1 = postsByMember.get(0).get(1);
        commentRepository.save(Comment.create(kuromiPost1, pikachu, "두 번째 글도 피카!", null));
    }

    /**
     * 테스트 회원 1명의 고정 데이터를 묶어두는 record.
     *
     * 기존처럼 username / name / profileUrl 을 각각 따로 관리하면
     * 인덱스가 어긋났을 때 버그를 찾기 어렵다.
     * 따라서 한 회원의 데이터를 하나의 객체로 묶어두는 편이 안전하다.
     */
    private record MemberSeed(
            String username,
            String name,
            String profileImageUrl
    ) {
    }
}
