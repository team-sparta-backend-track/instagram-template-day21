#!/bin/bash
# ================================================================
# Day 17 - 풀스택 종료 스크립트
# IntelliJ Run Configuration > Shell Script 에서 실행합니다.
# ================================================================
set -e

echo "=== Docker Compose 풀스택 종료 ==="
docker compose down

echo "=== 종료 완료 — 컨테이너 상태 ==="
docker compose ps