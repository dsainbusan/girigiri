# girigiri (기리기리)
동네 가게 마감 임박 음식 긴급 구제 서비스

[日本語](./README.ja.md)

전체 기획, 엔티티 설계, 역할 분담, 개발 체크리스트는 [CLAUDE.md](./CLAUDE.md)를 참고하세요.

## 기술 스택
- Backend: Java 21 + Spring Boot 3.5.16 (Gradle)
- Frontend: Thymeleaf + JavaScript (모바일 고정폭 ~420px)
- Database: MySQL
- 인증: Spring Security (+ OAuth2 카카오/네이버 예정)

## 로컬 실행
```bash
# 1. 클론
git clone https://github.com/dsainbusan/girigiri.git
cd girigiri

# 2. MySQL 데이터베이스 생성
mysql -u root -p -e "CREATE DATABASE girigiri DEFAULT CHARACTER SET utf8mb4"

# 3. 환경변수 설정
cp .env.example .env
# .env 파일을 열어 실제 DB 접속 정보로 수정

# 4. 실행
./gradlew bootRun
```

첫 실행 시 콘솔에 Spring Security가 생성한 임시 로그인 비밀번호가 로그로 출력됩니다 — 아직 실제 회원/OAuth2 인증이 연동되지 않은 스캐폴딩 단계라 정상입니다.
