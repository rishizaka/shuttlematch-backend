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
- **SSH の到達性**: さくらのVPSはSSHを常時開けたまま（鍵認証のみ・パスワード認証は無効化済み）。
  EC2時代のようなIP一時開放・OIDC AssumeRoleの仕組みは不要になった(2026-08-31 AWS→VPS移行で撤去)。
- Secrets: `VPS_HOST` / `VPS_SSH_KEY`（デプロイ専用 ed25519 鍵）。
  デプロイ鍵は VPS の `~/.ssh/authorized_keys` に `github-actions-deploy@shuttlematch-vps` として登録済み。
  ローテーションする場合は鍵の再生成 → authorized_keys 差し替え → `gh secret set VPS_SSH_KEY`。

**本番環境**
- さくらのVPS `160.16.52.211`（東京第2ゾーン、2GB、Ubuntu 24.04 LTS）
- SSH: `ssh -i ~/.ssh/shuttlematch-vps-key ubuntu@160.16.52.211`（sudoはNOPASSWD設定済み）
- systemd: `shuttlematch.service`（`java -jar ~/app.jar`、EnvFile `/etc/shuttlematch/app.env`、8080 で待受）
- frontend は同じVPS上の別サービス（3000）。詳細は `shuttlematch-frontend` の CLAUDE.md 参照。
- 公開URL: **https://s-match.net**。Cloudflare Tunnel（`cloudflared`、トンネル名`shuttlematch`）が
  `/etc/cloudflared/config.yml` の設定でVPSの3000番(frontend)へ振り分ける。frontendの`serve.mjs`が
  `/api/*` をさらに8080番(backend)へ中継するので、API も同一オリジン（`https://s-match.net/api/...`）で叩ける。
  VPSのinboundはSSH以外すべてufwで閉じている(Tunnelはアウトバウンド接続なので開放不要)。
- CORS 許可オリジンは EnvFile の `APP_CORS_ALLOWED_ORIGINS`（カンマ区切り）。ドメインを追加したら
  ここに足して `sudo systemctl restart shuttlematch` が必要。
- 旧AWS(EC2/RDS/CloudFront)は2026-08-31の切替後もしばらく残置(切り戻し用)。
  移行の経緯・落とし穴は `migration/README.md` を参照。

**手順（backend のコードを変更したとき）**

```bash
# 1. ローカルで実行可能 jar をビルド
cd ~/Develop/shuttlematch
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew bootJar   # build/libs/*.jar を生成

# 2. VPS へ転送し、現行 app.jar を app.jar.bak にローテートして差し替え
KEY=~/.ssh/shuttlematch-vps-key; HOST=160.16.52.211
JAR=$(ls -t build/libs/*.jar | grep -v plain | head -1)
scp -i "$KEY" "$JAR" ubuntu@$HOST:/tmp/app.jar
ssh -i "$KEY" ubuntu@$HOST '
  set -e; cd ~
  cp app.jar app.jar.bak
  mv /tmp/app.jar app.jar'

# 3. 再起動 & 確認
ssh -i "$KEY" ubuntu@$HOST '
  sudo systemctl restart shuttlematch
  sleep 5
  systemctl is-active shuttlematch
  curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8080/actuator/health'
```

- **ロールバック**: `ssh ... 'cd ~; cp app.jar.bak app.jar; sudo systemctl restart shuttlematch'`
- README 追記などアプリの挙動が変わらない変更のときは、backend の再デプロイは不要。
- Flyway マイグレーションはアプリ起動時に自動適用される（新しい `V*.sql` を含む jar をデプロイすれば反映）。

## Push 通知（モバイル向け・2026-07-22 導入）

モバイルアプリ（`../shuttlematch-mobile`）へ「セット開始」「ルーム終了」を通知する。

**宛先はユーザーではなく「ルーム購読」で持つ。** 参加者(`room_participants`)は `user_id` が
null のゲストがほとんど（かんたん作成は `guest_name="1".."N"`、自己参加も名前のみ）で、
ユーザー単位では宛先を解決できないため。`room_push_subscriptions` が実体（V16）。

```
PUT    /api/v1/rooms/{roomId}/push-subscriptions      購読(再登録は upsert)
DELETE /api/v1/rooms/{roomId}/push-subscriptions?expoToken=...   解除
```

- `participant_id`（任意）は端末が自己申告した「自分の番号」。あると文面を個別化できる
  （「あなたの試合です（第3セット・コート2）」／未申告なら「第3セットが始まりました」）。
- 解除のトークンはクエリパラメータ。`ExponentPushToken[...]` が角括弧を含むため。
- **送信先は Expo Push API**（`exp.host`）。**FCM/APNs の資格情報は backend が持たない**（EAS 側）。
  そのため通知の資格情報まわりでこのリポジトリを触る必要はない。
- **操作した本人を除外しない**。運営者はたいていプレーヤーを兼ねるため自分にも届くのが自然で、
  副次的に端末 1 台でも動作確認できる。
- 送信は `@TransactionalEventListener(AFTER_COMMIT)`。外部 HTTP をトランザクションに持ち込むと
  遅く、通知の失敗でセット開始がロールバックされてしまう。**通知の失敗は握り潰してログのみ**。
- Expo が `DeviceNotRegistered` を返した端末は購読を自動削除する。ルーム終了時も購読を掃除する。
- `APP_PUSH_ENABLED=false` で送信を止められる（ローカル開発用）。

**動作確認**: ダミートークンで購読 → セット開始 → 本番ログに
「セット開始を通知します: 宛先=N件」と、無効トークンなら「端末が無効になったため購読を削除します」
が出る。`ssh ... 'sudo journalctl -u shuttlematch --since "10 minutes ago" | grep 通知'`

## ミニゲームのランキング（2026-08-02 導入）

`/game/{slug}` の各ミニゲームの**上位5件だけ**を DB に持つ（`game_scores`、V17）。
昔のゲーセンのハイスコア表と同じで、ランクインしたら名前（最大8文字）を入れて登録する。

```
GET  /api/v1/games/{game}/ranking      上位5件
POST /api/v1/games/{game}/ranking      {playerName, score} → ランクインしたか・順位・登録後の一覧
```

- `{game}` は **フロントの URL と同じスラッグ**（`flap` / `rain` / `coin` / `flick` / `ski`）。
  ゲームを増やすときは `MiniGame` enum に1行足せば API とランキングが揃う。
- **ランクインしないスコアは保存しない**。登録のたびに6位以下を切り捨てるので、
  1ゲームあたり常に5行しかない（RDS の容量を食わない）。
- **同点は先に記録した方が上位**（後から同じ点でも追い落とせない）。判定は `Ranking.rankFor`。
  フロントにも同じ規則の `lib/ranking.ts` があり、名前入力を出すかの先読みに使う。
  **確定はサーバーの応答**（入力中に他の人に抜かれると `rankedIn=false` が返る）。
- **認証は無い**（このアプリ全体にまだ無い）。荒らし対策は割り切って次の3点だけ:
  1. 上位5件しか残さない
  2. `MiniGame.maxScore`（99999）を超えるスコアは 400。加点が1〜50点の4本はもちろん、
     滑走距離×倍率で伸びる `ski` でも実プレイでは届かない（フロント側でも頭打ちにしてある）
  3. `ScoreSubmissionRateLimiter` が送信元 IP ごとに10秒1回に制限（超えたら 429）。
     単一インスタンス運用なのでメモリで持つ（再起動で消えてよい）。
     本番は Cloudflare Tunnel 経由なので `CF-Connecting-IP`（Cloudflareが上書きするので
     クライアントは偽装できない）を優先し、無ければ `X-Forwarded-For` の先頭にフォールバックする
     （2026-08-31 AWS→VPS移行時に対応。切替直後に実地で429が正しく返ることを確認済み）。
  **本気の改ざん（API を直接叩く）は防げない。**荒れたら `delete from game_scores where game='...'`。

**動作確認**: `curl -s localhost:8080/api/v1/games/coin/ranking` と、
`curl -X POST .../ranking -d '{"playerName":"てすと","score":100}'`。
