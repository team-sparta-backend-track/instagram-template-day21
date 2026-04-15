package com.example.instagramclone.core.config;


import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DotenvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            // .env 파일 로드 (파일이 없으면 무시)
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();

            Map<String, Object> dotenvMap = new HashMap<>();

            // .env 파일의 모든 key-value를 Map에 저장
            dotenv.entries().forEach(entry -> dotenvMap.put(entry.getKey(), entry.getValue()));

            // 스프링 Environment에 등록 (우선순위를 높게 설정)
            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("dotenv", dotenvMap));
        } catch (Exception e) {
            // .env 파일이 없거나 로드할 수 없는 경우 무시
            log.info("INFO: .env file not found or could not be loaded. Using default configuration.");
        }
    }
}