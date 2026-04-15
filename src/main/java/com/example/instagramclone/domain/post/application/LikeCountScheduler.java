package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostLikeRepository;
import com.example.instagramclone.domain.post.domain.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [Day 19 Step 1~3] likeCount 주기적 재집계 스케줄러.
 * <p>
 * Step 1: JPA 루프 방식 — 단순하지만 느린 이유를 직접 확인
 * Step 2: JdbcTemplate.batchUpdate() — 쿼리 횟수를 극적으로 줄이는 방식
 * Step 3: Spring Batch Job 수동 트리거 — 스케줄러에서 JobLauncher로 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountScheduler {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final JobLauncher jobLauncher;
    private final Job likeCountSyncJob;

    // =========================================================================
    // [Day 19 Step 1] @Scheduled + JPA 루프
    // =========================================================================

    /**
     * TODO Step 1: JPA로 전체 게시물을 불러와 루프로 likeCount를 갱신한다.
     * <p>
     * 힌트:
     * 1. postRepository.findAll() 로 전체 게시물 로드
     * 2. 각 post마다 postLikeRepository.countByPost(post) 호출
     * 3. post.updateLikeCount(count) 후 postRepository.save(post)
     * 4. 실행 전후 System.currentTimeMillis() 로 소요 시간 측정 후 로그 출력
     * <p>
     * 문제 제기: 10,000건 기준 SELECT COUNT(*) N번 + UPDATE N번 발생 — 얼마나 걸리는가?
     */
    // @Transactional
    // @Scheduled(fixedDelay = 60_000) // Step 2 진행 시 주석 처리
    public void syncLikeCount() {
        long start = System.currentTimeMillis();

        List<Post> posts = postRepository.findAll();  // 10,000건 전부 메모리에
        for (Post post : posts) {
            long count = postLikeRepository.countByPost(post);  // SELECT COUNT(*) 10,000번
            int delta = (int) (count - post.getLikeCount());
            post.changeLikeCountBy(delta);           // 실제 COUNT와의 차이만큼 보정
            postRepository.save(post);               // UPDATE 10,000번
        }

        log.info("[Step1 JPA루프] {}ms / {}건",
                System.currentTimeMillis() - start, posts.size());
    }

    // =========================================================================
    // [Day 19 Step 2] JdbcTemplate.batchUpdate()
    // =========================================================================

    /**
     * TODO Step 2: GROUP BY 집계 쿼리 1번 + batchUpdate()로 UPDATE를 한 번에 전송한다.
     * <p>
     * 힌트:
     * 1. jdbcTemplate.queryForList("SELECT post_id, COUNT(*) AS cnt FROM post_like GROUP BY post_id")
     * 2. 결과를 Object[]{ cnt, postId } 리스트로 변환
     * 3. jdbcTemplate.batchUpdate("UPDATE posts SET like_count = ? WHERE id = ?", params)
     * 4. Step 1과 실행 시간 비교
     * <p>
     * 포인트: JPA save() 루프와 달리 영속성 컨텍스트를 거치지 않으므로 N번 왕복이 사라진다.
     */
    // @Scheduled(fixedDelay = 15_000) // Step 3 진행 시 주석 처리
    public void syncLikeCountBatch() {
        long start = System.currentTimeMillis();

        // ① 전체 게시물 기준 LEFT JOIN 집계 (쿼리 1번)
        //    좋아요 0건인 게시물도 cnt=0으로 포함된다
        List<LikeCountRow> rows = jdbcTemplate.query(
                """
                        SELECT p.id AS post_id, COUNT(pl.post_id) AS cnt
                        FROM posts p LEFT JOIN post_like pl ON p.id = pl.post_id
                        GROUP BY p.id
                        """,
                (rs, rowNum) -> new LikeCountRow(rs.getLong("post_id"), rs.getLong("cnt"))
        );

        // ② 한 번의 배치 호출 (UPDATE 쿼리를 JDBC 드라이버가 묶어서 전송)
        jdbcTemplate.batchUpdate(
                "UPDATE posts SET like_count = ? WHERE id = ?",
                rows.stream()
                        .map(row -> new Object[]{row.count(), row.postId()})
                        .toList()
        );

        log.info("[Step2 batchUpdate] {}ms / {}건",
                System.currentTimeMillis() - start, rows.size());
    }

    // =========================================================================
    // [Day 19 Step 3] Spring Batch Job — @Scheduled에서 JobLauncher로 실행
    // =========================================================================

    /**
     * Spring Batch Job을 스케줄러에서 수동 트리거한다.
     *
     * <p>spring.batch.job.enabled=false로 앱 시작 시 자동 실행을 껐으므로
     * 데이터 초기화(TestDataInit, Day19BulkDataInit) 이후에 스케줄러가 실행한다.
     * 매 실행마다 현재 시각을 파라미터로 전달해 Spring Batch가 새 실행으로 인식한다.
     */
//     @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void runLikeCountSyncJob() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(likeCountSyncJob, params);
        } catch (Exception e) {
            log.error("[Step3 Batch] Job 실행 실패", e);
        }
    }

    private record LikeCountRow(long postId, long count) {}
}
