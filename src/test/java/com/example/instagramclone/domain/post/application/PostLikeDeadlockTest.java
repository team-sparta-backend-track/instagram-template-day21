package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostLikeRepository;
import com.example.instagramclone.domain.post.domain.PostRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Day 18 과제 2] 비관적 락 데드락 재현 및 해결 테스트.
 *
 * <p><b>데드락 발생 조건</b>
 * <pre>
 *   시간 →
 *   Thread A │ post1 락 획득 ──────────── sleep ──────────── post2 락 시도 → ⏸ 대기 (B가 보유)
 *   Thread B │           post2 락 획득 ── sleep ── post1 락 시도 → ⏸ 대기 (A가 보유)
 *            │                                              ↑
 *            │                              A↔B 서로가 서로를 기다림 → 데드락!
 *            │                              DB가 감지 → 둘 중 하나를 패자로 선택 → 예외 발생
 * </pre>
 *
 * <p><b>데드락 해결 원리 — 락 획득 순서 통일</b>
 * <pre>
 *   항상 ID가 작은 게시물부터 락을 건다 (min → max).
 *
 *   Thread A │ min(post1,post2) 락 시도 → 획득 → max 락 시도 → 획득 → 완료
 *   Thread B │ min(post2,post1) 락 시도 → A가 보유 중 → ⏸ 대기 → A 완료 후 진행
 *            │
 *            │ 양쪽 모두 같은 행부터 경합 → 먼저 도착한 쪽이 이기고 나머지는 순서대로 대기
 *            │ → 데드락 불가능!
 * </pre>
 *
 * <p><b>Q. 실제 서비스에서는 어떻게 방어하나요?</b>
 * <ul>
 *   <li>애플리케이션: 항상 동일한 순서로 락 획득 (이 테스트의 해결책)</li>
 *   <li>DB 레벨: {@code innodb_lock_wait_timeout} 설정으로 락 대기 시간 상한 지정</li>
 *   <li>락 범위 최소화: 한 트랜잭션에서 여러 행을 동시에 잠그는 설계 자체를 피하는 것이 최선</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class PostLikeDeadlockTest {

    @Autowired private PostLikeService postLikeService;
    @Autowired private PostRepository postRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /** 데드락 재현에 사용할 게시물 2개의 ID */
    private Long postId1;
    private Long postId2;

    /** 두 게시물의 작성자 */
    private Long writerId;

    // =========================================================================
    // 셋업 / 정리
    // =========================================================================

    @BeforeEach
    void setUp() {
        Member writer = memberRepository.save(
                Member.builder()
                        .username("deadlock_test_writer")
                        .password(passwordEncoder.encode("abc1234!"))
                        .email("deadlock_writer@test.com")
                        .name("데드락테스터")
                        .build()
        );
        writerId = writer.getId();

        // 데드락에 참여할 게시물 2개 — ID 순서가 중요하므로 반드시 저장 후 ID를 사용한다
        postId1 = postRepository.save(
                Post.builder().content("데드락 테스트 게시물 1").writer(writer).build()
        ).getId();
        postId2 = postRepository.save(
                Post.builder().content("데드락 테스트 게시물 2").writer(writer).build()
        ).getId();
    }

    @AfterEach
    void tearDown() {
        // FK 제약: PostLike → Post → Member 순서로 삭제
        postLikeRepository.deleteAll();
        postRepository.deleteById(postId1);
        postRepository.deleteById(postId2);
        memberRepository.deleteById(writerId);
    }

    // =========================================================================
    // Step 2: 데드락 발생 재현
    // =========================================================================

    @Test
    @DisplayName("[과제 2 Step 2] 데드락 발생 — A(post1→post2), B(post2→post1) 반대 순서로 락 획득")
    void 데드락_발생_시나리오() throws InterruptedException {

        // ── 두 스레드가 동시에 시작할 수 있도록 준비 ────────────────────────
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        // 각 스레드에서 발생한 예외를 메인 스레드로 전달하기 위한 홀더
        AtomicReference<Throwable> exA = new AtomicReference<>();
        AtomicReference<Throwable> exB = new AtomicReference<>();

        // ── Thread A: post1 → post2 순서로 락 ───────────────────────────────
        executor.submit(() -> {
            try {
                postLikeService.likeTwoPostsDeadlockProne(postId1, postId2);
            } catch (Exception e) {
                exA.set(e); // 예외 캡처 (데드락 패자가 되면 여기에 담긴다)
            } finally {
                latch.countDown();
            }
        });

        // ── Thread B: post2 → post1 순서로 락 (A와 반대!) ───────────────────
        executor.submit(() -> {
            try {
                postLikeService.likeTwoPostsDeadlockProne(postId2, postId1);
            } catch (Exception e) {
                exB.set(e);
            } finally {
                latch.countDown();
            }
        });

        latch.await(); // 두 스레드 모두 완료될 때까지 대기

        // ── 결과 출력 ────────────────────────────────────────────────────────
        System.out.println("=== 데드락 시나리오 결과 ===");
        System.out.println("Thread A 예외: " + exA.get());
        System.out.println("Thread B 예외: " + exB.get());
        System.out.println("==========================");

        // ── 검증: 둘 중 하나는 반드시 예외를 받아야 한다 ─────────────────────
        // DB가 데드락을 감지하면 둘 중 하나(패자)를 강제 롤백시키고 예외를 발생시킨다.
        // Spring은 이를 DeadlockLoserDataAccessException 또는 CannotAcquireLockException으로 변환한다.
        Throwable deadlockException = exA.get() != null ? exA.get() : exB.get();

        assertThat(exA.get() != null || exB.get() != null)
                .as("데드락으로 인해 두 스레드 중 적어도 하나에서 예외가 발생해야 합니다.")
                .isTrue();

        // Spring 6.0.3+ 에서 DeadlockLoserDataAccessException 이 deprecated 됨.
        // 대신 DataAccessException 계층을 확인하거나 CannotAcquireLockException 을 사용한다.
        // MariaDB/MySQL  : 데드락 감지 시 CannotAcquireLockException (cause: DeadlockLoser...)
        // H2             : 락 대기 초과 시 CannotAcquireLockException
        assertThat(deadlockException)
                .as("발생한 예외는 데드락 또는 락 획득 실패 관련 예외여야 합니다.")
                .isInstanceOfAny(
                        CannotAcquireLockException.class,
                        DataAccessException.class
                );

        System.out.println("✅ 데드락 확인 완료! 예외 타입: " + deadlockException.getClass().getSimpleName());
        System.out.println("   예외 메시지: " + deadlockException.getMessage());
    }

    // =========================================================================
    // Step 3: 데드락 해결 — 락 획득 순서 통일
    // =========================================================================

    @Test
    @DisplayName("[과제 2 Step 3] 데드락 해결 — 항상 ID 오름차순(min→max)으로 락 획득")
    void 데드락_해결_락_순서_통일() throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        // 성공적으로 완료된 스레드 수를 추적
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Throwable> anyException = new AtomicReference<>();

        // ── Thread A: (post1, post2) 전달 — 내부에서 min→max 정렬 ───────────
        executor.submit(() -> {
            try {
                // 내부에서 Math.min/max로 정렬 → 항상 작은 ID부터 락을 건다
                postLikeService.likeTwoPostsSafeOrder(postId1, postId2);
                successCount.incrementAndGet();
            } catch (Exception e) {
                anyException.set(e);
            } finally {
                latch.countDown();
            }
        });

        // ── Thread B: (post2, post1) 전달 — 반대 순서지만 내부 정렬로 동일하게 처리 ──
        executor.submit(() -> {
            try {
                // postId2, postId1 순서로 넘겨도 내부에서 min→max로 정렬하므로
                // Thread A와 동일한 순서(작은 ID → 큰 ID)로 락을 획득한다.
                postLikeService.likeTwoPostsSafeOrder(postId2, postId1);
                successCount.incrementAndGet();
            } catch (Exception e) {
                anyException.set(e);
            } finally {
                latch.countDown();
            }
        });

        latch.await();

        // ── 결과 출력 ────────────────────────────────────────────────────────
        Post p1 = postRepository.findById(postId1).orElseThrow();
        Post p2 = postRepository.findById(postId2).orElseThrow();

        System.out.println("=== 데드락 해결 결과 ===");
        System.out.println("성공한 스레드 수: " + successCount.get());
        System.out.println("post1 likeCount: " + p1.getLikeCount()); // 2 예상
        System.out.println("post2 likeCount: " + p2.getLikeCount()); // 2 예상
        System.out.println("발생 예외: " + anyException.get());
        System.out.println("======================");

        // ── 검증: 두 스레드 모두 성공, 데드락 없음 ───────────────────────────
        assertThat(anyException.get())
                .as("락 순서를 통일하면 데드락이 발생하지 않아야 합니다.")
                .isNull();

        assertThat(successCount.get())
                .as("두 스레드 모두 성공해야 합니다.")
                .isEqualTo(2);

        // 각 게시물에 두 스레드가 한 번씩 +1 했으므로 likeCount = 2
        assertThat(p1.getLikeCount()).isEqualTo(2);
        assertThat(p2.getLikeCount()).isEqualTo(2);

        System.out.println("✅ 데드락 없이 두 스레드 모두 성공!");
    }
}
