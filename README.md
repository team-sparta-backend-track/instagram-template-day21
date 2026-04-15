# Instagram Clone Project

## 🛠️ 초기 실행 방법

이 프로젝트는 민감한 설정 정보(DB, JWT 등)를 보호하기 위해 환경변수(`.env`)를 사용합니다.

### 1. 환경 설정 (.env)
프로젝트 루트 디렉토리에 있는 `.env.example` 파일을 복사하여 `.env` 파일을 생성하세요.
```bash
cp .env.example .env
```
그 후, 생성된 `.env` 파일 내의 값들을 본인의 환경에 맞게 수정해주세요.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: 데이터베이스 접속 정보
- `JWT_SECRET_KEY`: JWT 서명에 사용할 32바이트 이상의 비밀키

### 2. 프로젝트 실행
- 인텔리제이(IntelliJ) 등 IDE에서 `InstagramCloneTemplateApplication`을 실행합니다.
- 기본 포트는 **8090**으로 설정되어 있습니다.

### 3. 접속 및 확인
- 브라우저에서 아래 주소로 접속합니다.
- **[http://localhost:8090](http://localhost:8090)**

---
> [!IMPORTANT]
> `.env` 파일은 절대 GitHub에 커밋하지 마세요. (현재 `.gitignore`에 등록되어 있습니다.)
