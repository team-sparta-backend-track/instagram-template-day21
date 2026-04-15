package com.example.instagramclone.domain.post.api;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Day 20 Step 1 시연용: Offset vs Cursor 실행 시간 비교.
 * <p>
 * H2의 EXPLAIN은 실행 계획 정보가 부실하므로,
 * 동일 조건에서 실제 쿼리를 실행하고 소요 시간(ms)을 직접 측정합니다.
 * <p>
 * local/docker 프로파일에서만 활성화됩니다.
 */
@RestController
@RequiredArgsConstructor
@Profile({"local", "docker"})
public class PaginationBenchmarkController {

    private final JdbcTemplate jdbcTemplate;

    private static final int WARMUP_RUNS = 3;
    private static final int MEASURE_RUNS = 5;

    @GetMapping("/api/benchmark/offset-vs-cursor")
    public Map<String, Object> benchmark() {

        int pageSize = 20;

        // ── 총 게시물 수 확인 ──
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Long.class);
        long totalPosts = count != null ? count : 0L;

        // ── 테스트할 offset 깊이들 ──
        int[] offsets = {0, 1_000, 10_000, 50_000, 100_000, 300_000, 490_000};

        List<Map<String, Object>> results = new ArrayList<>();

        for (int offset : offsets) {
            if (offset >= totalPosts) continue;

            // ── Offset 쿼리로 해당 위치의 cursorId 미리 확보 ──
            List<Map<String, Object>> offsetRows = jdbcTemplate.queryForList(
                "SELECT id FROM posts ORDER BY id DESC LIMIT ? OFFSET ?",
                pageSize, offset
            );
            Long cursorId = null;
            if (!offsetRows.isEmpty()) {
                cursorId = ((Number) offsetRows.get(offsetRows.size() - 1).get("ID")).longValue();
            }

            // ── ① Offset 방식: 워밍업 후 측정 ──
            for (int w = 0; w < WARMUP_RUNS; w++) {
                jdbcTemplate.queryForList(
                    "SELECT id, content FROM posts ORDER BY id DESC LIMIT ? OFFSET ?",
                    pageSize, offset
                );
            }
            long offsetNanoSum = 0;
            for (int r = 0; r < MEASURE_RUNS; r++) {
                long start = System.nanoTime();
                jdbcTemplate.queryForList(
                    "SELECT id, content FROM posts ORDER BY id DESC LIMIT ? OFFSET ?",
                    pageSize, offset
                );
                offsetNanoSum += System.nanoTime() - start;
            }
            double offsetMs = offsetNanoSum / (double) MEASURE_RUNS / 1_000_000.0;

            // ── ② Cursor 방식: 워밍업 후 측정 ──
            Long cursorParam = (offset == 0 || cursorId == null) ? null : cursorId + pageSize;
            for (int w = 0; w < WARMUP_RUNS; w++) {
                runCursorQuery(cursorParam, pageSize);
            }
            long cursorNanoSum = 0;
            for (int r = 0; r < MEASURE_RUNS; r++) {
                long start = System.nanoTime();
                runCursorQuery(cursorParam, pageSize);
                cursorNanoSum += System.nanoTime() - start;
            }
            double cursorMs = cursorNanoSum / (double) MEASURE_RUNS / 1_000_000.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offset", offset);
            row.put("offsetMs", String.format("%.2f", offsetMs));
            row.put("cursorMs", String.format("%.2f", cursorMs));
            row.put("ratio", cursorMs > 0.01
                ? String.format("%.1fx", offsetMs / cursorMs)
                : "∞");
            results.add(row);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalPosts", totalPosts);
        response.put("pageSize", pageSize);
        response.put("measureRuns", MEASURE_RUNS);
        response.put("message", "Offset은 깊어질수록 느려지고, Cursor는 항상 일정합니다");
        response.put("results", results);
        return response;
    }

    private void runCursorQuery(Long cursorParam, int pageSize) {
        if (cursorParam == null) {
            jdbcTemplate.queryForList(
                "SELECT id, content FROM posts ORDER BY id DESC LIMIT ?",
                pageSize
            );
        } else {
            jdbcTemplate.queryForList(
                "SELECT id, content FROM posts WHERE id < ? ORDER BY id DESC LIMIT ?",
                cursorParam, pageSize
            );
        }
    }
}
