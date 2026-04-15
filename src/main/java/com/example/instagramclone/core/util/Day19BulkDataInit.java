package com.example.instagramclone.core.util;

import com.example.instagramclone.domain.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Order(2)   // TestDataInit(1) 이후 실행
// @Component
@RequiredArgsConstructor
@Profile({"local", "docker"})   // 로컬, 도커 환경에서만 실행
@Slf4j
public class Day19BulkDataInit implements ApplicationRunner {

    private static final int BULK_POST_COUNT = 500_000;
    private static final int CHUNK_SIZE = 10_000;
    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // 이미 벌크 데이터가 있으면 건너뜀 (posts가 25건 초과 = 벌크 데이터 존재)
        Long postCountResult = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM posts", Long.class);
        long postCount = postCountResult != null ? postCountResult : 0L;
        if (postCount > 25) {
            log.info("[Day19BulkDataInit] 이미 초기화됨, 건너뜀 (posts={}건)", postCount);
            return;
        }

        List<Long> memberIds = memberRepository.findAll()
            .stream().map(m -> m.getId()).toList();
        if (memberIds.isEmpty()) return;

        long start = System.currentTimeMillis();

        // ① 게시물 벌크 INSERT (CHUNK_SIZE 단위로 분할 — MySQL OOM 방지)
        //    like_count = 999 : 고의로 틀린 값 → 배치 교정 대상
        String postSql = "INSERT INTO posts (content, member_id, like_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        List<Object[]> chunk = new ArrayList<>(CHUNK_SIZE);
        for (int i = 1; i <= BULK_POST_COUNT; i++) {
            Long memberId = memberIds.get(i % memberIds.size());
            chunk.add(new Object[]{
                "배치 테스트 게시물 #" + i + " #day19 #batch",
                memberId,
                999,                            // ← 고의 오염값
                LocalDateTime.now().minusSeconds(i),
                LocalDateTime.now().minusSeconds(i)
            });
            if (chunk.size() >= CHUNK_SIZE) {
                jdbcTemplate.batchUpdate(postSql, chunk);
                chunk.clear();
            }
        }
        if (!chunk.isEmpty()) {
            jdbcTemplate.batchUpdate(postSql, chunk);
            chunk.clear();
        }

        // ② 방금 INSERT된 게시물 ID 목록 조회 (앞 25건 캐릭터 게시물 제외)
        List<Long> bulkPostIds = jdbcTemplate.queryForList(
            "SELECT id FROM posts ORDER BY id DESC LIMIT " + BULK_POST_COUNT,
            Long.class
        );

        // ③ 좋아요 5,000건: 벌크 게시물 앞 5,000건에만 1명씩
        String likeSql = "INSERT INTO post_like (member_id, post_id, created_at, updated_at) VALUES (?, ?, ?, ?)";
        int halfCount = Math.min(bulkPostIds.size() / 2, 5_000);
        for (int i = 0; i < halfCount; i++) {
            Long postId = bulkPostIds.get(i);
            Long memberId = memberIds.get(i % memberIds.size());
            chunk.add(new Object[]{memberId, postId,
                LocalDateTime.now(), LocalDateTime.now()});
            if (chunk.size() >= CHUNK_SIZE) {
                jdbcTemplate.batchUpdate(likeSql, chunk);
                chunk.clear();
            }
        }
        if (!chunk.isEmpty()) {
            jdbcTemplate.batchUpdate(likeSql, chunk);
        }

        log.info("[Day19BulkDataInit] 완료 — posts {}건(like_count=999), post_like {}건 / {}ms",
            BULK_POST_COUNT, halfCount, System.currentTimeMillis() - start);
        log.info("▶ 배치 실행 후 앞 {}건: like_count 999→1, 나머지 {}건: 999→0 으로 교정되는지 확인하세요.",
            halfCount, bulkPostIds.size() - halfCount);
    }
}
