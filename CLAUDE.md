# CLAUDE.md — shuttlematch (backend)

ShuttleMatch のバックエンド（Java 21 / Spring Boot 4 / Gradle Kotlin DSL / PostgreSQL / DDD）。
セットアップ・ローカル起動・DB接続は `README.md` を参照（`./run.sh` でワンコマンド起動）。

## デプロイ（本番反映）

> **main に push すれば自動デプロイされる**（`.github/workflows/ci.yml`）。以下の手動手順は
> Actions が使えないときの緊急用。

**CI/CD（GitHub Actions）**
- `build`: JDK21 で `./gradlew build`。main への push と PR で発火。実行可能 jar を artifact 化。
- `deploy`: **main への push のときだけ**実行（PR では走らない）。`concurrency` で直列化。
  - jar を scp → `app.jar.bak` に退避 → 差し替え → `systemctl restart`
  - health check を最大300秒リトライ。**失敗したら `app.jar.bak` へ自動ロールバック**して再起動。
  - 最後に `https://s-match.net/api/*` の到達を確認。
- **SSH の到達性**: EC2 の 22番は自宅IP(`14.8.61.161/32`)にしか開いていない。runner は
  GitHub OIDC で IAM ロール `github-actions-shuttlematch-deploy` を AssumeRole し、
  自分の IP を /32 で SG に一時追加 → 完了後（失敗時も `if: always()`）必ず revoke する。
  **22番を常時開放しない設計なので、この仕組みを外さないこと。**
- Secrets: `EC2_HOST` / `EC2_SSH_KEY`（デプロイ専用 ed25519 鍵）/ `AWS_ROLE_ARN` / `EC2_SG_ID`。
  デプロイ鍵は EC2 の `~/.ssh/authorized_keys` に `github-actions-deploy@shuttlematch` として登録済み。
  ローテーションする場合は鍵の再生成 → authorized_keys 差し替え → `gh secret set EC2_SSH_KEY`。

**本番環境**
- EC2 インスタンス `shuttlematch-app`（`3.113.92.223`, ap-northeast-1, t3.micro）
- SSH: `ssh -i ~/.ssh/shuttlematch-key.pem ec2-user@3.113.92.223`（passwordless sudo 可）
- systemd: `shuttlematch.service`（`java -jar ~/app.jar`、EnvFile `/etc/shuttlematch/app.env`、8080 で待受）
- frontend は同じ EC2 上の別サービス（3000）。詳細は `shuttlematch-frontend` の CLAUDE.md 参照。
- 公開URL: **https://s-match.net**。CloudFront `E2ZAQ39VPHE72R` が `/api/*` を 8080 に振り分けるので、
  API も同一オリジン（`https://s-match.net/api/...`）で叩ける。
- CORS 許可オリジンは EnvFile の `APP_CORS_ALLOWED_ORIGINS`（カンマ区切り）。ドメインを追加したら
  ここに足して `sudo systemctl restart shuttlematch` が必要。

**手順（backend のコードを変更したとき）**

```bash
# 1. ローカルで実行可能 jar をビルド
cd ~/Develop/shuttlematch
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew bootJar   # build/libs/*.jar を生成

# 2. EC2 へ転送し、現行 app.jar を app.jar.bak にローテートして差し替え
KEY=~/.ssh/shuttlematch-key.pem; HOST=3.113.92.223
JAR=$(ls -t build/libs/*.jar | grep -v plain | head -1)
scp -i "$KEY" "$JAR" ec2-user@$HOST:/tmp/app.jar
ssh -i "$KEY" ec2-user@$HOST '
  set -e; cd ~
  cp app.jar app.jar.bak
  mv /tmp/app.jar app.jar'

# 3. 再起動 & 確認
ssh -i "$KEY" ec2-user@$HOST '
  sudo systemctl restart shuttlematch
  sleep 5
  systemctl is-active shuttlematch
  curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8080/actuator/health'
```

- **ロールバック**: `ssh ... 'cd ~; cp app.jar.bak app.jar; sudo systemctl restart shuttlematch'`
- README 追記などアプリの挙動が変わらない変更のときは、backend の再デプロイは不要。
- Flyway マイグレーションはアプリ起動時に自動適用される（新しい `V*.sql` を含む jar をデプロイすれば反映）。
