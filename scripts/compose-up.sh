#!/bin/bash
# ================================================================
# Day 17 - 풀스택 기동 스크립트
#
# 실행 순서:
#   1) ./gradlew bootJar   — 실행 가능한 jar 빌드
#   2) docker compose up -d --build  — 이미지 빌드 + 컨테이너 기동
#
# IntelliJ Run Configuration > Shell Script 에서 이 파일을 지정합니다.
# Working directory 를 반드시 프로젝트 루트($ProjectFileDir$)로 설정해야
# Gradle wrapper와 docker-compose.yml 경로가 올바르게 잡힙니다.
# ================================================================
set -e   # 중간 명령 실패 시 즉시 종료 (bootJar 실패 → compose up 건너뜀)

echo "=== [1/2] Gradle bootJar 빌드 시작 ==="
./gradlew bootJar

echo "=== [2/2] Docker Compose 풀스택 기동 ==="
docker compose up -d --build


echo "=== 기동 완료 — 앱 로그 출력 시작 (중지: Ctrl+C) ==="
docker compose logs -f app