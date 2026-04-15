# ================================================================
# Day 17 - Spring Boot 앱 Docker 이미지
#
# [빌드 & 실행 방법]
#   1) Gradle 빌드 먼저: ./gradlew bootJar
#   2) Compose로 이미지 빌드 포함 기동: docker compose up -d --build
#
# [주의]
#   docker compose up -d 전에 반드시 ./gradlew bootJar 를 실행해야 한다.
#   jar 파일이 없으면 COPY 단계에서 빌드가 실패한다.
# ================================================================

# eclipse-temurin 17 JRE (Debian 기반)
# JDK 대신 JRE만 사용 → 이미지 크기를 줄이는 현업 관행
#
FROM eclipse-temurin:17-jre

WORKDIR /app

# bootJar 결과물을 컨테이너 안으로 복사한다.
# 와일드카드(*)를 사용하면 버전이 바뀌어도 Dockerfile 수정 없이 대응 가능하다.
COPY build/libs/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
