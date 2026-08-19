# ギリギリ — 街の店の閉店間際フードロス救済サービス

[한국어](./CLAUDE.md)

## 📋 プロジェクト概要
- **プロジェクト名**: ギリギリ
- **チーム情報**: 14期 A組 2班（チーム名: ダイスキ）
- **リーダー**: ソン・ボミ | **メンバー**: カン・ノウン、キム・テフン、ムン・チャンホ、ソン・チェヒョン（5名）
- **目標**: 地域の個人事業主の廃棄ロスを減らし、消費者には合理的な価格で新鮮食品・ベーカリーなどを購入できる機会を提供 → 食品ロス問題と個人事業主の売上問題を同時に解決

## 🎯 コアコンセプト
- **店主の立場**: 閉店時間が近づくほど、売れ残った食品は全量廃棄ロスになる。残っている食品や割引情報をリアルタイムで顧客に知らせる手段がない。
- **消費者の立場**: 安く食品を買いたくても、どの店がいつ閉店セールを行うのか分かりにくい。
- **ソリューション**: リアルタイム地図ベースの閉店セール情報共有 + 事前決済予約 + ドキュメント自動生成による透明性の確保

## 🏗️ 技術スタック
| 区分 | 技術 |
|---|---|
| Frontend | Thymeleaf + JavaScript（モバイル固定幅 ~420px） |
| Backend | Java + Spring Boot |
| Database | MySQL |
| 認証 | Spring Security + OAuth2（Kakao/Naver） |
| 地図・位置情報 | Kakao Map API |
| リアルタイム通知 | WebSocket / STOMP |
| 決済 | PortOne（アイムポート）テスト決済 |
| ドキュメント生成 | openhtmltopdf（PDF）+ Apache POI（Excel） |
| QRコード | QRライブラリ |

## 🔑 主な機能

### 3.1 会員・認証 [新規]
- ソーシャルログイン（Kakao/Naver OAuth2）+ 通常会員登録
- Roleベースのルーティング: USER（一般）/ ADMIN（店主）
- 管理者デュアルモード: 実際のroleは固定、viewModeセッションで「管理者モード / 一般ユーザーモード」を切り替え
- 権限チェックは常に実際のrole基準（viewModeではない）

### 3.2 店主用在庫登録
- 閉店前に残った商品の写真＋簡単な情報登録（品目、原価、数量）
- 本日の登録/販売状況を一目で確認
- POSレジからJSONデータを受信（自動化を想定）

### 3.3 消費者用リアルタイム検索
- 近隣の閉店セール中の店を地図で確認
- カテゴリフィルタリング（ベーカリー/おかず/弁当/カフェなど）
- お気に入り店舗登録＋リアルタイム通知（WebSocket/STOMP）

### 3.4 予約・受け取り
- 希望商品を事前決済後、受け取り時間帯を予約
- 受け取りQRコード（または番号）発行で現地確認
- ノーショー防止メカニズム

### 3.5 ドキュメント自動生成 [新規・差別化ポイント]
- 決済領収書PDF（ユーザー用）: マイページから再ダウンロード可能
- 販売・廃棄レポート（店主用）: Excel + PDF
  - 品目別 登録/販売/廃棄数量
  - 売上・割引額
  - 救済率（販売÷登録）
  - CO₂削減換算
  - 日次が基本、累積時は週次に拡張
- （オプション）環境貢献度認証書PDF: SNS共有用

### 3.6 信頼・記録
- 利用後の店舗レビュー＆評価
- 累積節約金額、救済した食品数、CO₂削減量などの個人ダッシュボード

## 📱 主要画面UI

すべての画面はモバイル固定幅（約420px、中央揃え）を基準に設計

| 画面 | 説明 |
|---|---|
| ログイン/モード選択 [新規] | ソーシャルログイン、管理者は「管理者モード / 一般ユーザーモード」を選択 |
| ホーム画面 | 地図ベースの近隣閉店セール店舗カード一覧（割引率、残り時間を強調） |
| 商品詳細 | 原価/割引価格の比較、残り数量、受け取り時間、予約ボタン |
| 店主ダッシュボード | 登録状況、販売/廃棄統計グラフ、レポートダウンロード（Excel/PDF） |
| マイページ | 予約履歴、受け取りQR、領収書PDF再ダウンロード、累積節約・環境貢献度 |

## 👥 役割分担（画面ベース）
| 担当者 | 軸 | 主な機能 |
|---|---|---|
| ソン・ボミ（リーダー） | 共通・バックエンドコア | ソーシャルログイン、roleルーティング・モード分岐、予約ロジック、割引率自動計算、全体企画・調整 |
| ソン・チェヒョン | 共通DB＋店主用 | ERD/DB設計、在庫登録、POS JSON連携、販売/廃棄ダッシュボード・統計、販売・廃棄レポート（Excel/PDF） |
| カン・ノウン | 店主用＋発表 | 受け取り予約管理・ノーショー防止、決済（PortOne）・QR・領収書PDF、掲示板、PPT・デモ動画 |
| キム・テフン | 一般ユーザー用（画面） | 共通レイアウト（モバイル）、商品詳細、マイページ、レビュー、環境貢献度の可視化 |
| ムン・チャンホ | 一般ユーザー用（地図・リアルタイム） | Kakao Map連携、地図メイン検索、カテゴリフィルタ、お気に入り、リアルタイム通知（WebSocket） |

**⚠️ クリティカルパス:**
1. ソーシャルログイン・role分岐（ソン・ボミ）
2. リアルタイム通知・地図（ムン・チャンホ）
→ この2つがボトルネックになるため優先着手

## 🎨 設計原則
- **モバイルファースト**: 固定幅420pxを基準（PWAは採用しない — レスポンシブではなく、モバイルサイズに固定された通常のWebページ）
- **デュアルモード分岐**:
  - ログイン時にroleを確認 → USERまたはADMIN画面に遷移
  - ADMINはヘッダートグルで「管理者モード / 一般ユーザーモード」を切り替え（viewModeセッションに保存）
  - 権限チェックは常に実際のrole基準
- **リアルタイム性**: WebSocketを使用（お気に入り店舗の閉店セール開始時）
- **トランザクション安全性**: 決済検証（フロント → バックエンドで再検証）
- **ドキュメント自動化**: PDF/Excel生成による記録＆精算の透明性

## 📊 主要エンティティ（想定）

```
User（一般ユーザー）
├─ loginId, password（またはOAuth2 ID）
├─ role（USER / ADMIN）
├─ nickname, location（緯度/経度）
└─ createdAt, updatedAt

Store（店舗/店主アカウント）
├─ loginId, password
├─ storeName, category, address
├─ location（緯度/経度）, operatingHours
├─ role = ADMIN
└─ createdAt, updatedAt

Product（登録された食品商品）
├─ storeId（FK）
├─ name, originalPrice, discountedPrice
├─ quantity, remainingQuantity
├─ imageUrl, description
├─ status（active / sold / expired）
└─ registeredAt

Reservation（予約）
├─ userId, productId, storeId
├─ reservedQuantity, totalPrice
├─ pickupTime, pickupCode（QR）
├─ status（pending / confirmed / picked / cancelled / noshowed）
└─ reservedAt, pickedAt

Review（レビュー）
├─ userId, storeId
├─ rating, content
└─ createdAt

Like（お気に入り）
├─ userId, storeId
├─ createdAt

Receipt（領収書PDF記録）
├─ reservationId
├─ pdfUrl, generatedAt

Report（店舗レポート）
├─ storeId, reportDate
├─ registeredCount, soldCount, expiredCount
├─ totalSales, totalDiscount, savedCO2
├─ excelUrl, pdfUrl
└─ generatedAt
```

## ⚙️ 開発チェックリスト

### Phase 1: 基礎（優先着手）
- [ ] ソーシャルログイン（Kakao/Naver OAuth2）— ソン・ボミ
- [ ] Roleベースルーティング＋viewModeセッション — ソン・ボミ
- [ ] 共通レイアウト実装（モバイル固定幅コンテナ、ヘッダー/ナビ）— キム・テフン
- [ ] DBスキーマ設計・エンティティ実装・共通CRUD — ソン・チェヒョン
- [ ] Kakao Map連携（緯度/経度受信）— ムン・チャンホ

### Phase 2: コア機能
- [ ] 在庫登録＆管理 — ソン・チェヒョン
- [ ] リアルタイム通知（WebSocket）— ムン・チャンホ
- [ ] 予約ロジック — ソン・ボミ
- [ ] マイページUI — キム・テフン
- [ ] 地図メイン画面 — ムン・チャンホ

### Phase 3: 決済＆ドキュメント [新規]
- [ ] PortOne決済連携 — カン・ノウン
- [ ] 領収書PDF生成 — カン・ノウン＋ソン・チェヒョン
- [ ] 店舗レポート（Excel + PDF）— ソン・チェヒョン
- [ ] 環境貢献度認証書 — ソン・チェヒョン

### Phase 4: 追加機能
- [ ] レビュー/評価 — キム・テフン
- [ ] 掲示板 — カン・ノウン
- [ ] 環境貢献度ダッシュボード可視化 — キム・テフン

## 🔐 認証＆権限設計 [新規]

```javascript
// セッションに保存
{
  userId: "...",
  role: "ADMIN", // 実際の権限（不変）
  viewMode: "ADMIN_MODE", // 現在の表示モード（変更可能）
  storeId: "..." // ADMINの場合のみ必須
}

// 権限チェックは常にrole基準
if (session.role === "ADMIN") {
  // 店主向け機能を許可
}

// 画面分岐はviewMode基準
if (session.viewMode === "ADMIN_MODE") {
  // 店主ダッシュボードを表示
} else {
  // 一般ユーザーホームを表示
}
```

## 🌍 差別化ポイント
| 項目 | 本サービス |
|---|---|
| モバイル密着型UI | ✅ 固定幅420px最適化 |
| 環境貢献度の可視化 | ✅ 累積節約・CO₂削減ダッシュボード |
| 受け取りQRシステム | ✅ ノーショー防止＆現地確認 |
| ドキュメント自動生成 | ✅ 領収書PDF / 店舗レポート（Excel/PDF）/ 環境認証書 |
| リアルタイム通知 | ✅ WebSocketベースの即時通知 |

## 📚 参考サービス
- Too Good To Go（ヨーロッパ）: 廃棄間近食品販売プラットフォーム（コア構造を参考）
- ラストオーダー（韓国）: 類似サービス → 差別化ポイントの明確化が必要

## 🚀 ローカル開発環境セットアップ（予定）

```bash
# 1. プロジェクトのクローン
git clone [repo-url]

# 2. MySQLデータベースの作成
mysql -u root -p < schema.sql

# 3. application.propertiesの設定
# spring.datasource.url, username, password
# spring.security.oauth2.client.registration.kakao.client-id
# spring.security.oauth2.client.registration.naver.client-id
# portone.api.key, portone.merchant.id
# kakao.map.api.key

# 4. プロジェクトの実行
./gradlew bootRun
```

## 📞 主な連絡先／お知らせ
- リーダー管理チャンネル: [Slack / Discordリンク]
- 技術的な問題共有: [GitHub Issues / Discussions]
- 定例会議: [曜日/時間]
