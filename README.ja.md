# girigiri（ギリギリ）
街の店の閉店間際フードロス救済サービス

[한국어](./README.md)

プロジェクト全体の企画、エンティティ設計、役割分担、開発チェックリストは [CLAUDE.ja.md](./CLAUDE.ja.md) を参照してください。

## 技術スタック
- Backend: Java 21 + Spring Boot 3.5.16 (Gradle)
- Frontend: Thymeleaf + JavaScript（モバイル固定幅 ~420px）
- Database: MySQL
- 認証: Spring Security（+ OAuth2 Kakao/Naver 予定）

## ローカル実行
```bash
# 1. クローン
git clone https://github.com/dsainbusan/girigiri.git
cd girigiri

# 2. MySQLデータベースの作成
mysql -u root -p -e "CREATE DATABASE girigiri DEFAULT CHARACTER SET utf8mb4"

# 3. 環境変数の設定
cp .env.example .env
# .env ファイルを開いて実際のDB接続情報に修正

# 4. 実行
./gradlew bootRun
```

初回実行時、コンソールにSpring Securityが生成した仮ログインパスワードがログ出力されます — まだ実際の会員/OAuth2認証が連携されていないスキャフォールディング段階のため正常な動作です。
