# CLAUDE.md — shuttlematch (backend)

ShuttleMatch のバックエンド（Java 21 / Spring Boot 4 / Gradle Kotlin DSL / PostgreSQL / DDD）。
セットアップ・ローカル起動・DB接続は `README.md` を参照（`./run.sh` でワンコマンド起動）。

## デプロイ（本番反映）

> CI/CD は未整備（`ci.yml` は Build&Test のみで自動デプロイなし）。**デプロイは手動で、Claude に依頼して実施している**。将来的に CI/CD 化したい。

**本番環境**
- EC2 インスタンス `shuttlematch-app`（`3.113.92.223`, ap-northeast-1, t3.micro）
- SSH: `ssh -i ~/.ssh/shuttlematch-key.pem ec2-user@3.113.92.223`（passwordless sudo 可）
- systemd: `shuttlematch.service`（`java -jar ~/app.jar`、EnvFile `/etc/shuttlematch/app.env`、8080 で待受）
- frontend は同じ EC2 上の別サービス（3000）。詳細は `shuttlematch-frontend` の CLAUDE.md 参照。

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
