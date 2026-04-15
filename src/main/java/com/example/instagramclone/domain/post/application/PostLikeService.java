package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.core.aop.annotation.DistributedLock;
import com.example.instagramclone.core.constant.RedisKeys;
import com.example.instagramclone.core.exception.LockAcquisitionException;
import com.example.instagramclone.core.exception.PostErrorCode;
import com.example.instagramclone.core.exception.PostException;
import com.example.instagramclone.domain.member.application.MemberService;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.post.api.LikeStatusResponse;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostLike;
import com.example.instagramclone.domain.post.domain.PostLikeRepository;
import com.example.instagramclone.domain.post.domain.PostRepository;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.event.NotificationEvent;
import com.example.instagramclone.domain.post.event.LikeCountDeltaEvent;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * [Day 12 Step 2~3] 좋아요 토글 API.
 *
 * <ul>
 *   <li>Step 2: PostLike Insert/Delete 분기</li>
 *   <li>Step 3: Post.likeCount 비정규화 (+1 / -1). 응답 likeCount는 COUNT(*) 대신 post.likeCount 사용.</li>
 * </ul>
 *
 * <p><b>[Day 18] 수업 순서</b>
 * <pre>
 *   Step 2 — toggleLike()                  : findByIdWithLock 비관적 락
 *   Step 3 — toggleLikeWithRedisLock()     : Redis SET NX EX + Lua 직접 구현
 *   Step 4 — toggleLikeWithRedisson()      : Redisson RLock 추상화
 *   보너스 — toggleLikeWithLockTemplate()  : RedisLockTemplate으로 보일러플레이트 제거
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final MemberService memberService;
    private final RedisLockService redisLockService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    // =========================================================================
    // [Day 18 Step 2] 비관적 락 버전
    // =========================================================================

    /**
     * 좋아요 토글 — 비관적 락 적용 버전 (Day 18 Step 2).
     *
     * <p>{@code findByIdWithLock}이 {@code SELECT ... FOR UPDATE}를 발행해
     * 한 트랜잭션이 끝날 때까지 같은 행을 다른 트랜잭션이 읽지 못하게 막는다.
     * Read-Modify-Write가 직렬화되어 likeCount 유실이 사라진다.
     */
    @Transactional
    public LikeStatusResponse toggleLike(Long loginMemberId, Long postId) {
        // SELECT ... FOR UPDATE → 이 트랜잭션이 끝날 때까지 다른 트랜잭션 대기
        Post post = postRepository.findByIdWithLock(postId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        Member member = memberService.getReferenceById(loginMemberId);
        boolean alreadyLiked = postLikeRepository.existsByMemberAndPost(member, post);

        if (alreadyLiked) {
            postLikeRepository.deleteByMemberAndPost(member, post);
            post.changeLikeCountBy(-1);
            return new LikeStatusResponse(false, post.getLikeCount());
        }
        postLikeRepository.save(PostLike.create(member, post));
        post.changeLikeCountBy(1);
        return new LikeStatusResponse(true, post.getLikeCount());
    }

    // =========================================================================
    // [Day 18 Step 3] Redis SET NX EX 직접 구현 버전
    // =========================================================================

    /**
     * 좋아요 토글 — Redis 분산 락 직접 구현 버전 (Day 18 Step 3).
     *
     * <p><b>락 설계</b>
     * <ul>
     *   <li>락 키: {@code "lock:like:{postId}"} — 게시물 단위로 잠근다</li>
     *   <li>락 값: UUID — 서버마다 고유해서 본인이 건 락인지 식별한다</li>
     *   <li>타임아웃: 3초 — 서버가 죽어도 3초 후 자동 해제되어 데드락을 막는다</li>
     * </ul>
     *
     * <p><b>fail-fast 특성</b><br>
     * 락을 얻지 못하면 대기 없이 즉시 {@link LockAcquisitionException}(409)을 던진다.
     * 재시도가 필요하다면 Step 4 Redisson의 {@code waitTime}을 활용한다.
     */
    @Transactional
    public LikeStatusResponse toggleLikeWithRedisLock(Long loginMemberId, Long postId) {

        // ── 1. 락 키·값 준비 ─────────────────────────────────────────────────
        // 키: 게시물 ID 단위 — 서로 다른 게시물은 서로의 락에 영향을 주지 않는다.
        // 값: UUID — 여러 서버 중 어느 서버가 이 락을 잡고 있는지 구별한다.
        String lockKey   = RedisKeys.lockLike(postId);
        String lockValue = UUID.randomUUID().toString();

        // ── 2. 락 획득 시도 ───────────────────────────────────────────────────
        // SET lockKey lockValue NX EX 3
        // → 키가 없으면 세팅 성공(true), 이미 있으면 실패(false)
        if (!redisLockService.tryLock(lockKey, lockValue, 3)) {
            throw new LockAcquisitionException("잠시 후 다시 시도해 주세요.");
        }

        // ── 3. 비즈니스 로직 (try-finally로 락 해제 보장) ────────────────────
        try {
            // Redis 락이 직렬화 지점이므로 findById만으로 충분하다.
            // findByIdWithLock(DB 비관적 락)을 함께 걸면 이중 잠금이 된다.
            Post post = postRepository.findById(postId)
                    .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

            Member member = memberService.getReferenceById(loginMemberId);
            boolean alreadyLiked = postLikeRepository.existsByMemberAndPost(member, post);

            if (alreadyLiked) {
                postLikeRepository.deleteByMemberAndPost(member, post);
                post.changeLikeCountBy(-1);
                return new LikeStatusResponse(false, post.getLikeCount());
            }
            postLikeRepository.save(PostLike.create(member, post));
            post.changeLikeCountBy(1);
            return new LikeStatusResponse(true, post.getLikeCount());

        } finally {
            // 예외가 발생해도 반드시 락을 해제한다.
            // Lua 스크립트로 본인 UUID인지 확인 후 삭제 → 다른 서버의 락을 실수로 지우지 않는다.
            redisLockService.releaseLock(lockKey, lockValue);
        }
    }

    // =========================================================================
    // [Day 18 Step 4] Redisson RLock 버전
    // =========================================================================

    /**
     * 좋아요 토글 — Redisson RLock 버전 (Day 18 Step 4).
     *
     * <p><b>Step 3 직접 구현의 한계</b>
     * <ul>
     *   <li>Lua 스크립트를 직접 작성·관리해야 한다</li>
     *   <li>{@code tryLock}에 재시도(waitTime) 로직이 없다 → 락 경합 시 즉시 실패</li>
     *   <li>watchdog이 없어 비즈니스 로직이 길어지면 락이 중간에 만료될 수 있다</li>
     * </ul>
     *
     * <p><b>Redisson이 해결하는 것</b>
     * <ul>
     *   <li>Lua 스크립트 관리 → Redisson 내부 처리</li>
     *   <li>{@code waitTime}: 지정 시간 동안 락 재시도 → 처리량 우선 시 유용</li>
     *   <li>{@code leaseTime}: 락 자동 만료 시간. {@code -1}이면 watchdog이 30초마다 갱신</li>
     * </ul>
     *
     * <p><b>파라미터 실험 포인트</b>
     * <pre>
     *   waitTime=0  → 락 없으면 즉시 실패 (버튼 중복 클릭 방지, 빠른 응답)
     *   waitTime=5  → 5초간 재시도 후 실패 (처리량 우선, 순차 처리)
     * </pre>
     */
    @Transactional
    public LikeStatusResponse toggleLikeWithRedisson(Long loginMemberId, Long postId) {

        // ── 1. RLock 객체 생성 ────────────────────────────────────────────────
        // getLock()은 실제로 Redis에 접근하지 않는다. 키 이름과 연결된 락 객체를 반환할 뿐이다.
        // 실제 Redis 명령은 tryLock() 시점에 발행된다.
        RLock lock = redissonClient.getLock(RedisKeys.lockLike(postId));

        // ── 2. 락 획득 여부를 추적하는 플래그 ────────────────────────────────
        // finally에서 "내가 실제로 락을 획득했는가"를 확인하기 위해 필요하다.
        // tryLock이 예외를 던진 경우 acquired=false이므로 unlock을 호출하지 않는다.
        boolean acquired = false;

        try {
            // ── 3. 락 획득 시도 ───────────────────────────────────────────────
            // tryLock(waitTime, leaseTime, unit)
            //   waitTime  = 5초: 락을 얻지 못하면 5초 동안 재시도한다.
            //   leaseTime = -1초: leaseTime=-1이면 watchdog이 자동으로 갱신한다.
            acquired = lock.tryLock(5, -1, TimeUnit.SECONDS);

            if (!acquired) {
                // 3초를 기다렸는데도 락을 못 얻음 → 요청이 너무 많은 상황
                throw new LockAcquisitionException("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
            }

            // ── 4. 비즈니스 로직 ──────────────────────────────────────────────
            Post post = postRepository.findById(postId)
                    .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

            Member member = memberService.getReferenceById(loginMemberId);
            boolean alreadyLiked = postLikeRepository.existsByMemberAndPost(member, post);

            if (alreadyLiked) {
                postLikeRepository.deleteByMemberAndPost(member, post);
                post.changeLikeCountBy(-1);
                return new LikeStatusResponse(false, post.getLikeCount());
            }
            postLikeRepository.save(PostLike.create(member, post));
            post.changeLikeCountBy(1);
            return new LikeStatusResponse(true, post.getLikeCount());

        } catch (InterruptedException e) {
            // tryLock 대기 중 스레드가 인터럽트되면 InterruptedException이 발생한다.
            // interrupt() 상태를 복원해야 상위 코드가 인터럽트를 인식할 수 있다.
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("락 대기 중 인터럽트가 발생했습니다.", e);

        } finally {
            // acquired=true이고 현재 스레드가 락 보유자인 경우에만 해제한다.
            // isHeldByCurrentThread(): 다른 스레드의 락을 실수로 해제하는 사고를 막는다.
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // =========================================================================
    // [Day 18 과제] @DistributedLock AOP 버전 — 비즈니스 로직만 남긴다
    // =========================================================================

    /**
     * 좋아요 토글 — {@link DistributedLock} AOP 버전 (Day 18 과제).
     *
     * <p>Step 4(Redisson 직접)와 동작이 완전히 같지만,
     * 락 관련 코드({@code tryLock / unlock / acquired 플래그 / InterruptedException})가
     * 서비스 코드에서 완전히 사라지고 <b>비즈니스 로직만</b> 남는다.
     *
     * <p><b>실행 순서 (DistributedLockAspect @Order(1) 덕분)</b>
     * <pre>
     *   [락 획득] → [트랜잭션 시작(@Transactional)] → [비즈니스 로직]
     *            → [트랜잭션 커밋] → [락 해제]
     * </pre>
     *
     * <p><b>SpEL 키 표현식</b><br>
     * {@code "'lock:like:' + #postId"} → 파라미터 이름 {@code postId}를 {@code #postId}로 참조.
     * {@code @Cacheable(key = "#id")}와 완전히 같은 문법이다.
     *
     * <p><b>waitTime=5, leaseTime=-1</b><br>
     * 5초간 락 재시도(처리량 우선), watchdog으로 락 자동 갱신.
     */
    @DistributedLock(key = "'" + RedisKeys.LOCK_LIKE_PREFIX + "' + #postId", waitTime = 5)
    @Transactional
    public LikeStatusResponse toggleLikeWithDistributedLock(Long loginMemberId, Long postId) {

        // 락 획득·해제는 DistributedLockAspect가 처리한다.
        // 여기엔 순수 비즈니스 로직만 작성한다.
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        Member member = memberService.getReferenceById(loginMemberId);
        boolean alreadyLiked = postLikeRepository.existsByMemberAndPost(member, post);

        if (alreadyLiked) {
            postLikeRepository.deleteByMemberAndPost(member, post);
            post.changeLikeCountBy(-1);
            return new LikeStatusResponse(false, post.getLikeCount());
        }
        postLikeRepository.save(PostLike.create(member, post));
        post.changeLikeCountBy(1);
        return new LikeStatusResponse(true, post.getLikeCount());
    }

    
    
    // =========================================================================
    // [Day 19 Step 4] Redis INCR Write-Back 버전
    // =========================================================================

    /**
     * TODO Step 4: PostLike는 즉시 DB에 저장하고, likeCount 갱신은 Redis delta에 위임한다.
     *
     * 힌트:
     *  1. postLikeRepository.existsByMemberAndPost() 로 기존 좋아요 여부 확인
     *  2. 좋아요 있으면 → deleteByMemberAndPost() + redisTemplate.opsForValue().increment(RedisKeys.likeDelta(postId), -1)
     *  3. 좋아요 없으면 → save(PostLike.create()) + redisTemplate.opsForValue().increment(RedisKeys.likeDelta(postId), +1)
     *  4. post.changeLikeCountBy() 호출 제거 — likeCount 갱신은 LikeCountWriteBackScheduler가 담당
     *
     * ⚠️ 트랜잭션 경계 주의:
     *  Redis INCR는 @Transactional 범위 밖이므로 DB 롤백 시 delta 불일치 발생 가능.
     *  → Day 21 @TransactionalEventListener(AFTER_COMMIT) 으로 해결 예정.
     */
    @Transactional
    public LikeStatusResponse toggleLikeWriteBack(Long loginMemberId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
        Member member = memberService.getReferenceById(loginMemberId);

        boolean alreadyLiked = postLikeRepository.existsByMemberAndPost(member, post);

        int delta;
        if (alreadyLiked) {
            postLikeRepository.deleteByMemberAndPost(member, post);
            delta = -1;
        } else {
            postLikeRepository.save(PostLike.create(member, post));
            delta = +1;

            // ✅ 알림 이벤트 (좋아요를 눌렀을 때만!)
            eventPublisher.publishEvent(new NotificationEvent(
                    NotificationType.LIKE,
                    post.getWriter().getId(),
                    loginMemberId,
                    postId,
                    null
            ));
        }

        // ✅ Redis INCR → 이벤트로 교체 (커밋 후에만 실행)
        eventPublisher.publishEvent(new LikeCountDeltaEvent(postId, delta));

        // 아직 flush 안 된 누적 delta를 Redis에서 읽어 정확한 카운트를 계산.
        // (Redis INCR 자체는 커밋 후 실행되므로, 여기서는 '이번 delta 반영 전' 값을 읽는다)
        String accDeltaStr = redisTemplate.opsForValue().get(RedisKeys.likeDelta(postId));
        long accumulatedDelta = (accDeltaStr != null) ? Long.parseLong(accDeltaStr) : 0;
        long estimatedCount = Math.max(0, post.getLikeCount() + accumulatedDelta + delta);
        return new LikeStatusResponse(!alreadyLiked, estimatedCount);
    }





    

    // =========================================================================
    // [Day 18 과제 2] 데드락 데모용 메서드 — 실무 코드에 절대 사용하지 말 것
    // =========================================================================

    /**
     * [과제 2 Step 2] 데드락 발생을 재현하기 위한 메서드.
     *
     * <p>두 게시물에 순서대로 비관적 락을 건다.
     * Thread A가 {@code (post1, post2)}, Thread B가 {@code (post2, post1)} 순서로 호출하면
     * 서로가 서로의 락을 기다리는 <b>데드락</b>이 발생한다.
     *
     * <pre>
     *   Thread A: post1 락 획득 ──── sleep ────▶ post2 락 시도 ─── B가 보유 중 → 대기 ⏸
     *   Thread B:                  post2 락 획득 ──── sleep ────▶ post1 락 시도 ─── A가 보유 중 → 대기 ⏸
     *                                                            ↑
     *                                                    서로가 서로를 기다림 → 데드락!
     * </pre>
     *
     * <p><b>Thread.sleep()이 여기 있는 이유</b><br>
     * 첫 번째 락을 획득한 뒤 잠깐 멈춰서, 다른 스레드가 반대쪽 락을 획득할 시간을 준다.
     * sleep이 없으면 타이밍이 맞지 않아 데드락이 재현되지 않을 수 있다.
     * ⚠️ 실무 코드에서 트랜잭션 안에 sleep을 절대 넣지 말 것.
     *
     * @param firstPostId  먼저 락을 걸 게시물 ID
     * @param secondPostId 나중에 락을 걸 게시물 ID
     */
    @Transactional
    public void likeTwoPostsDeadlockProne(Long firstPostId, Long secondPostId) {
        // ── Step 1: 첫 번째 게시물 비관적 락 획득 ────────────────────────────
        // 이 락은 트랜잭션이 끝날 때까지 해제되지 않는다.
        Post first = postRepository.findByIdWithLock(firstPostId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        // ── Step 2: 의도적인 지연 ─────────────────────────────────────────────
        // 이 사이에 반대편 스레드가 자신의 첫 번째 락(= 우리의 두 번째 락)을 획득한다.
        // 이 줄이 없으면 타이밍이 안 맞아 데드락이 재현되지 않을 수 있다.
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ── Step 3: 두 번째 게시물 비관적 락 시도 ────────────────────────────
        // 반대편 스레드가 이미 이 행을 잠갔다면 → 여기서 블로킹
        // 동시에 반대편도 우리 첫 번째 행을 기다리는 중 → 데드락!
        Post second = postRepository.findByIdWithLock(secondPostId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        first.changeLikeCountBy(1);
        second.changeLikeCountBy(1);
    }



    /**
     * [과제 2 Step 3] 데드락 해결 버전 — 항상 ID 오름차순으로 락을 획득한다.
     *
     * <p><b>데드락 해결 원리</b><br>
     * 데드락은 두 스레드가 <b>서로 다른 순서</b>로 락을 획득할 때 발생한다.
     * 항상 같은 순서(작은 ID → 큰 ID)로 획득하면
     * 두 스레드 모두 같은 행부터 기다리므로 순서가 생겨 데드락이 불가능해진다.
     *
     * <pre>
     *   Thread A: min(post1, post2) 락 → max(post1, post2) 락
     *   Thread B: min(post2, post1) 락 → max(post2, post1) 락
     *             ↑ 둘 다 min 행부터 시도 → 먼저 도착한 스레드가 락 획득, 나머지는 대기
     *             → 선점 스레드 완료 후 대기 스레드가 진행 → 데드락 없음
     * </pre>
     *
     * @param postIdA 게시물 A의 ID (순서 무관)
     * @param postIdB 게시물 B의 ID (순서 무관)
     */
    @Transactional
    public void likeTwoPostsSafeOrder(Long postIdA, Long postIdB) {
        // 항상 ID가 작은 쪽부터 락을 건다 — 스레드 호출 순서에 무관하게 동일한 락 순서 보장
        Long firstId  = Math.min(postIdA, postIdB);
        Long secondId = Math.max(postIdA, postIdB);

        Post first = postRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        // 데드락 데모와 비교하기 위해 sleep을 동일하게 유지한다.
        // 이번엔 양쪽 스레드가 같은 행부터 시도하므로 sleep이 있어도 데드락이 생기지 않는다.
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Post second = postRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        first.changeLikeCountBy(1);
        second.changeLikeCountBy(1);
    }
}
