package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostLikeRepository;
import com.example.instagramclone.domain.post.domain.PostRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Day 18] 동시 좋아요 요청 → Race Condition 재현 및 락 적용 전/후 비교 테스트.
 *
 * <p><b>수업 진행 순서</b>
 * <ol>
 *   <li>Step 1 테스트 실행 → <b>실패</b> 확인 (Race Condition 체감)</li>
 *   <li>PostLikeService.toggleLike() 에서 findById → findByIdWithLock 으로 교체</li>
 *   <li>Step 2 테스트의 @Disabled 제거 후 실행 → <b>통과</b> 확인 (비관적 락)</li>
 *   <li>build.gradle Redisson 주석 해제 + toggleLikeWithRedisson() 구현</li>
 *   <li>Step 4 테스트의 @Disabled 제거 후 실행 → <b>통과</b> 확인 (분산 락)</li>
 * </ol>
 *
 * <p><b>왜 @Transactional을 테스트에 달지 않나?</b><br>
 * 동시성 테스트는 각 스레드가 <b>독립적인 트랜잭션</b>을 가져야 Race Condition이 재현된다.
 * 테스트 메서드에 @Transactional을 달면 모든 스레드가 같은 트랜잭션 컨텍스트를 공유해
 * 문제가 숨겨진다. 대신 @AfterEach에서 데이터를 수동으로 정리한다.
 *
 * <p>{@code @ActiveProfiles("test")}: Dotenv가 테스트에서 로드되지 않으므로
 * {@code application-test.yml}의 고정 JWT 키·H2 설정을 쓴다 ({@code AuthControllerTest} 등과 동일).
 */
@SpringBootTest
@ActiveProfiles("test")
class PostLikeConcurrencyTest {

    @Autowired private PostLikeService postLikeService;
    @Autowired private PostRepository postRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /** 동시 요청 수. 이 숫자만큼의 회원이 같은 게시물에 좋아요를 누른다. */
    private static final int THREAD_COUNT = 100;

    /**
     * 스레드 풀 크기.
     * THREAD_COUNT(100)보다 작게 설정해야 스레드들이 진짜로 경합한다.
     * 예: 풀=32이면 32개가 먼저 실행되고 나머지 68개는 큐에서 대기하다 합류한다.
     */
    private static final int POOL_SIZE = 32;

    /** 테스트 대상 게시물 ID. setUp()에서 채워진다. */
    private Long testPostId;

    /**
     * 동시 좋아요를 누를 회원 ID 목록.
     *
     * <p>PostLike에 (member_id, post_id) 유니크 제약이 있으므로
     * 각 스레드마다 다른 회원을 사용해야 중복 좋아요 오류 없이
     * 순수 likeCount 경합만 테스트할 수 있다.
     */
    private final List<Long> memberIds = new ArrayList<>();

    // =========================================================================
    // 셋업 / 정리
    // =========================================================================

    @BeforeEach
    void setUp() {
        memberIds.clear();
        String encodedPw = passwordEncoder.encode("abc1234!");

        // THREAD_COUNT만큼 회원을 생성한다.
        // 각 스레드가 서로 다른 회원으로 좋아요를 눌러 유니크 제약 위반을 피한다.
        Member writer = null;
        for (int i = 0; i < THREAD_COUNT; i++) {
            Member member = memberRepository.save(
                    Member.builder()
                            .username("concurrency_user_" + i)
                            .password(encodedPw)
                            .email("concurrency_user_" + i + "@test.com")
                            .name("동시성테스터" + i)
                            .build()
            );
            memberIds.add(member.getId());
            if (i == 0) writer = member; // 첫 번째 회원이 게시물 작성자
        }

        // likeCount = 0에서 시작하는 테스트용 게시물 1개 생성
        Post post = postRepository.save(
                Post.builder()
                        .content("동시성 테스트용 게시물")
                        .writer(writer)
                        .build()
        );
        testPostId = post.getId();
    }

    @AfterEach
    void tearDown() {
        // FK 제약 때문에 PostLike → Post → Member 순서로 삭제해야 한다.
        postLikeRepository.deleteAll();
        postRepository.deleteById(testPostId);
        memberIds.forEach(memberRepository::deleteById);
    }

    // =========================================================================
    // Step 1: Race Condition 재현 — 이 테스트는 의도적으로 실패한다
    // =========================================================================

    @Test
//    @Disabled
    @DisplayName("[Step 1] 락 없음 — 100명 동시 좋아요 시 likeCount 유실 확인 (실패 예상)")
    void 락_없이_동시_좋아요_100개_요청() throws InterruptedException {

        // ── 1. 고정 크기 스레드 풀 생성 ─────────────────────────────────────
        // newFixedThreadPool(32): 최대 32개 스레드가 동시에 실행된다.
        // 요청 100개가 들어오면 32개씩 처리하고 나머지는 큐에서 대기한다.
        ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);

        // ── 2. CyclicBarrier 생성 ────────────────────────────────────────────
        // POOL_SIZE(32)개 스레드가 모두 barrier.await()에 도달할 때까지 서로 대기한다.
        // 모두 모이면 동시에 출발 → 경합이 최대화되어 Race Condition 재현 확률이 높아진다.
        // ★ THREAD_COUNT(100)로 설정하면 안 된다: 풀에서 32개만 활성화되므로
        //   나머지 68개가 배리어에 영원히 도달하지 못해 데드락이 발생한다.
        // ★ CyclicBarrier는 재사용 가능하므로 32개씩 배치로 반복 동기화된다.
        CyclicBarrier barrier = new CyclicBarrier(POOL_SIZE);

        // ── 3. CountDownLatch 생성 ───────────────────────────────────────────
        // 100개 작업이 모두 끝날 때까지 메인 스레드를 대기시키는 카운터다.
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        // ── 4. 100개 작업을 스레드 풀에 제출 ────────────────────────────────
        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long memberId = memberIds.get(i); // 각 스레드마다 다른 회원 ID

            executor.submit(() -> {
                try {
                    try {
                        // POOL_SIZE(32)개가 모이면 동시 출발.
                        // 마지막 배치(100 % 32 = 4개)는 32명이 채워지지 않으므로
                        // 타임아웃 후 BrokenBarrierException을 무시하고 그대로 진행한다.
                        barrier.await(5, TimeUnit.SECONDS);
                    } catch (BrokenBarrierException | TimeoutException ignored) { }
                    // toggleLike()는 현재 락이 없다.
                    // → 동시에 여러 스레드가 같은 likeCount를 읽고 +1 후 저장
                    // → Read-Modify-Write 사이클이 겹쳐 값이 유실된다.
                    postLikeService.toggleLike(memberId, testPostId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown(); // 성공·실패 관계없이 카운터 감소
                }
            });
        }

        // ── 4. 모든 스레드 완료 대기 ─────────────────────────────────────────
        latch.await(); // 카운터가 0이 될 때까지 블로킹

        // ── 5. 결과 확인 ─────────────────────────────────────────────────────
        // 영속성 컨텍스트 캐시를 피하기 위해 DB에서 새로 조회한다.
        Post post = postRepository.findById(testPostId).orElseThrow();
        System.out.println("=== Race Condition 결과 ===");
        System.out.println("예상 likeCount: " + THREAD_COUNT);
        System.out.println("실제 likeCount: " + post.getLikeCount()); // 100보다 작은 값
        System.out.println("==========================");

        // 100이어야 하지만 Race Condition 때문에 더 작은 값이 나온다 → 이 assert가 실패한다.
        // ★ 이 실패가 수업의 출발점 — "아, 정말 깨지는구나!" 를 직접 확인하는 것이 목적이다.
        assertThat(post.getLikeCount()).isEqualTo(THREAD_COUNT);
    }

    // =========================================================================
    // Step 2: 비관적 락 적용 후 확인
    // ★ PostLikeService.toggleLike() 수정 후 @Disabled 를 제거하고 실행하세요.
    // =========================================================================

    @Test
//    @Disabled("TODO Step 2: PostLikeService.toggleLike()에서 findById → findByIdWithLock 교체 후 이 어노테이션을 제거하세요.")
    @DisplayName("[Step 2] 비관적 락 — 100명 동시 좋아요 시 likeCount = 100 (통과 예상)")
    void 비관적_락_적용_후_동시_좋아요_100개_요청() throws InterruptedException {

        // Step 1과 완전히 동일한 코드다.
        // 달라지는 것은 PostLikeService.toggleLike() 내부뿐이다.
        //
        // findByIdWithLock() → SELECT ... FOR UPDATE 발행
        // → 한 트랜잭션이 행을 잡고 있는 동안 다른 트랜잭션은 같은 행을 읽지 못하고 대기
        // → Read-Modify-Write가 직렬화됨 → likeCount 유실 없음
        ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
        CyclicBarrier barrier = new CyclicBarrier(POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long memberId = memberIds.get(i);

            executor.submit(() -> {
                try {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                    } catch (BrokenBarrierException | TimeoutException ignored) { }
                    postLikeService.toggleLike(memberId, testPostId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Post post = postRepository.findById(testPostId).orElseThrow();
        System.out.println("=== 비관적 락 결과 ===");
        System.out.println("실제 likeCount: " + post.getLikeCount()); // 정확히 100
        System.out.println("====================");

        // 비관적 락 덕분에 100이 정확하게 들어간다 → 통과!
        assertThat(post.getLikeCount()).isEqualTo(THREAD_COUNT);
    }


    // =========================================================================
    // Step 4: Redisson 분산 락 적용 후 확인
    // ★ build.gradle Redisson 주석 해제 + toggleLikeWithRedisson() 구현 후
    //   @Disabled 를 제거하고 실행하세요.
    // =========================================================================

    @Test
//    @Disabled("TODO Step 4: build.gradle Redisson 주석 해제 + toggleLikeWithRedisson() 구현 후 이 어노테이션을 제거하세요.")
    @DisplayName("[Step 4] Redisson 분산 락 — 100명 동시 좋아요 시 likeCount = 100 (통과 예상)")
    void Redisson_분산_락_적용_후_동시_좋아요_100개_요청() throws InterruptedException {

        // Step 2(비관적 락)와 구조는 동일하다.
        // 다른 점: postLikeService.toggleLikeWithRedisson()을 호출한다.
        //
        // DB 락 대신 Redis 글로벌 키(lock:like:{postId})로 직렬화한다.
        // → 서버가 몇 대든 Redis 키 하나로 "한 번에 한 요청만" 처리 가능
        // → waitTime=3초: 3초 안에 락을 얻지 못하면 LockAcquisitionException → 409 반환
        ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
        CyclicBarrier barrier = new CyclicBarrier(POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long memberId = memberIds.get(i);

            executor.submit(() -> {
                try {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                    } catch (BrokenBarrierException | TimeoutException ignored) { }
                    // Redisson RLock이 Redis에서 원자적으로 락을 관리한다.
                    // waitTime > 0 이면 락을 얻을 때까지 지정 시간 동안 재시도하므로
                    // 100개 요청 대부분이 결국 처리돼 likeCount = 100이 된다.
                    postLikeService.toggleLikeWithDistributedLock(memberId, testPostId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Post post = postRepository.findById(testPostId).orElseThrow();
        System.out.println("=== Redisson 분산 락 결과 ===");
        System.out.println("실제 likeCount: " + post.getLikeCount()); // 정확히 100
        System.out.println("============================");

        // Redisson 분산 락 덕분에 100이 정확하게 들어간다 → 통과!
        assertThat(post.getLikeCount()).isEqualTo(THREAD_COUNT);
    }
}
