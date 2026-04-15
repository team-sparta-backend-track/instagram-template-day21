package com.example.instagramclone.core.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Order(3)   // Day19BulkDataInit(2) 이후 실행
// @Component
@RequiredArgsConstructor
@Profile({"local", "docker"})
@Slf4j
public class Day20BulkDataInit implements ApplicationRunner {

    private static final int BULK_MEMBER_COUNT = 5_000;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // 이미 벌크 유저가 있으면 건너뜀 (members 25건 초과면 벌크 데이터 존재)
        Long memberCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users", Long.class);
        if (memberCount != null && memberCount > 25) {
            log.info("[Day20BulkDataInit] 이미 초기화됨, 건너뜀 (members={}건)", memberCount);
            return;
        }

        long start = System.currentTimeMillis();
        String encodedPw = passwordEncoder.encode("password123");

        // 유저 5,000명 벌크 INSERT — username 검색용
        // username 패턴: user_0001, user_0002 ... user_5000
        // 일부는 'kuro' 'mame' 같은 캐릭터 이름 prefix 를 섞어 검색 결과 다양화
        List<Object[]> memberParams = new ArrayList<>();
        String[] prefixes = {"user_", "kuro_", "mame_", "pika_", "doraemon_"};
        for (int i = 1; i <= BULK_MEMBER_COUNT; i++) {
            String prefix = prefixes[i % prefixes.length];
            String username = prefix + String.format("%04d", i);
            memberParams.add(new Object[]{
                username,
                username + "@test.com",
                encodedPw,
                username,           // name (NOT NULL)
                "USER",             // role (NOT NULL)
                LocalDateTime.now().minusSeconds(i),
                LocalDateTime.now().minusSeconds(i)
            });
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO users (username, email, password, name, role, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            memberParams
        );

        log.info("[Day20BulkDataInit] 완료 — members {}건 / {}ms",
            BULK_MEMBER_COUNT, System.currentTimeMillis() - start);
        log.info("▶ 검색 실습: 'kuro' 키워드로 검색하면 약 {}건이 매칭됩니다.",
            BULK_MEMBER_COUNT / prefixes.length);
    }
}
