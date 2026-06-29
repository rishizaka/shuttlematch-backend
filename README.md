# ShuttleMatch (Backend)

バドミントンサークル管理アプリのバックエンド。出欠管理とダブルスのランダムマッチングを提供する。

## 技術スタック

| レイヤー | 技術 |
|----------|------|
| 言語 | Java 21 |
| フレームワーク | Spring Boot 4.1 |
| ビルド | Gradle (Kotlin DSL) |
| DB | PostgreSQL 16 (本番想定) / 14 (ローカル) |
| マイグレーション | Flyway |
| テスト | JUnit 5 + Testcontainers |
| 設計 | DDD (ドメイン駆動設計) |

## ディレクトリ構成 (DDD)

```
src/main/java/com/shuttlematch/
├── domain/            # ドメイン層 (model / service / repository インターフェース)
│   ├── model/         #   集約・エンティティ・値オブジェクト (user/circle/session/match)
│   ├── service/       #   ドメインサービス (マッチングロジック等)
│   └── repository/    #   リポジトリインターフェース
├── application/       # アプリケーション層
│   ├── usecase/       #   ユースケース (session/circle)
│   └── dto/           #   アプリ層 DTO
├── infrastructure/    # インフラ層
│   ├── persistence/   #   JPA / Redis 実装
│   ├── security/      #   Cognito JWT 検証 (今後)
│   └── config/        #   各種設定
└── presentation/      # プレゼンテーション層
    └── api/           #   REST controller / request / response

src/main/resources/db/migration/  # Flyway マイグレーション (V1__init.sql ...)
```

## セットアップ

### 前提

- JDK 21 (Homebrew: `brew install openjdk@21`)
- PostgreSQL (Docker Compose または ローカル Homebrew 版)

### 1. データベースの起動

**A) Docker Compose を使う場合 (推奨 / 要 Docker)**

```bash
docker compose up -d
```

`compose.yaml` で PostgreSQL 16 が `localhost:5432` に起動する (DB/ユーザー/パスワードはすべて `shuttlematch`)。

**B) ローカルの PostgreSQL を使う場合**

```bash
brew services start postgresql@14
createuser -s shuttlematch 2>/dev/null; \
psql -d postgres -c "ALTER ROLE shuttlematch LOGIN PASSWORD 'shuttlematch';"
createdb -O shuttlematch shuttlematch
```

### 2. アプリの起動

Homebrew の `openjdk@21` は keg-only のため、`gradlew` を動かすには JAVA_HOME を通す必要がある。
`~/.zshrc` に以下を一度追記しておくと以後は不要:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
export PATH="$JAVA_HOME/bin:$PATH"
```

その上で:

```bash
./gradlew bootRun
```

起動確認:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Flyway がアプリ起動時に `V1__init.sql` を自動適用する。

### 3. テスト

Testcontainers が PostgreSQL コンテナを自動起動するため **Docker が必要**。

```bash
./gradlew test
```

## 接続設定

接続情報は環境変数で上書き可能 (`.env.example` 参照)。デフォルトはローカル/Compose 共通:

| 変数 | デフォルト |
|------|-----------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/shuttlematch` |
| `DB_USERNAME` | `shuttlematch` |
| `DB_PASSWORD` | `shuttlematch` |

## 今後の実装 (Phase 1 MVP)

- [ ] ドメインモデル実装 (User / Circle / Session / Match 集約)
- [ ] マッチングドメインサービス (15試合ランダム生成・Fisher-Yates)
- [ ] ユースケース (セッション作成 / 参加登録 / 試合生成)
- [ ] REST API (controller / request / response)
- [ ] Cognito JWT 認証 (Spring Security resource server)
