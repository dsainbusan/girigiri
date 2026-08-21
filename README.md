# girigiri (ギリギリ / 기리기리)

**[日本語](#日本語)** | **[한국어](#한국어)**

---

## 日本語

街の店の閉店間際フードロス救済サービス

プロジェクト全体の企画、エンティティ設計、役割分担、開発チェックリストは [CLAUDE.md](./CLAUDE.md)（日本語版: [CLAUDE.ja.md](./CLAUDE.ja.md)）を参照してください。

### 技術スタック
- Backend: Java 21 + Spring Boot 3.5.16 (Gradle)
- Frontend: Thymeleaf + JavaScript（モバイル固定幅 ~420px）
- Database: MySQL
- 認証: Spring Security（+ OAuth2 Kakao/Naver 予定）

### ローカル実行
```bash
# 1. クローン
git clone https://github.com/dsainbusan/girigiri.git
cd girigiri

# 2. MySQLデータベースの作成
mysql -u root -p -e "CREATE DATABASE girigiri DEFAULT CHARACTER SET utf8mb4"

# 3. 環境変数の設定
# プロジェクトルートに .env ファイルを作成し、以下を実際の値で記入
cat <<'ENV' > .env
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/girigiri?useSSL=false&serverTimezone=Asia/Seoul&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=changeme

# 領収書PDFの保存先(Supabase Storage)。未設定でもOK — その場合は自動的にプロジェクト直下の
# receipts/ フォルダにローカル保存される(開発初期はこれでも問題ない)。
# Supabaseプロジェクト作成後、Project Settings > API から Project URL / service_role key を取得し、
# Storageで「receipts」という名前のPublicバケットを作成してから値を入れる。
SUPABASE_URL=
SUPABASE_SERVICE_KEY=
SUPABASE_STORAGE_BUCKET=receipts

ENV

# 4. 実行
./gradlew bootRun
```

初回実行時、コンソールにSpring Securityが生成した仮ログインパスワードがログ出力されます — まだ実際の会員/OAuth2認証が連携されていないスキャフォールディング段階のため正常な動作です。



---

## 한국어

동네 가게 마감 임박 음식 긴급 구제 서비스

전체 기획, 엔티티 설계, 역할 분담, 개발 체크리스트는 [CLAUDE.md](./CLAUDE.md) (일본어: [CLAUDE.ja.md](./CLAUDE.ja.md))를 참고하세요.

### 기술 스택
- Backend: Java 21 + Spring Boot 3.5.16 (Gradle)
- Frontend: Thymeleaf + JavaScript (모바일 고정폭 ~420px)
- Database: MySQL
- 인증: Spring Security (+ OAuth2 카카오/네이버 예정)

### 로컬 실행
```bash
# 1. 클론
git clone https://github.com/dsainbusan/girigiri.git
cd girigiri

# 2. MySQL 데이터베이스 생성
mysql -u root -p -e "CREATE DATABASE girigiri DEFAULT CHARACTER SET utf8mb4"

# 3. 환경변수 설정
# 프로젝트 루트에 .env 파일을 만들고 아래 내용을 실제 값으로 채운다
cat <<'ENV' > .env
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/girigiri?useSSL=false&serverTimezone=Asia/Seoul&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=changeme


# 영수증 PDF 저장 위치(Supabase Storage). 안 채워도 됨 — 그럴 땐 자동으로 프로젝트 폴더 밑
# receipts/ 폴더에 로컬 저장된다 (개발 초반엔 이걸로도 충분).
# Supabase 프로젝트 만든 뒤 Project Settings > API 에서 Project URL / service_role key를 받고,
# Storage에서 "receipts"라는 이름의 Public 버킷을 만든 다음에 값을 채우면 된다.
SUPABASE_URL=
SUPABASE_SERVICE_KEY=
SUPABASE_STORAGE_BUCKET=receipts

ENV

# 4. 실행
./gradlew bootRun
```

첫 실행 시 콘솔에 Spring Security가 생성한 임시 로그인 비밀번호가 로그로 출력됩니다 — 아직 실제 회원/OAuth2 인증이 연동되지 않은 스캐폴딩 단계라 정상입니다.


