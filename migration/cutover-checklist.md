# Phase 3 — 本番切り替えチェックリスト

実施タイミング: **日中でOK**(ユーザー判断。ダウンタイム5分以内なので深夜帯にこだわらない)。
ただし利用が集中する時間帯(実測 03〜05時・10時台)は避ける。

## 事前(数時間前)
- [ ] Cloudflare の DNS レコード(`s-match.net` / `www`)の TTL を短く(60〜300秒)しておく
- [ ] Phase 2 のリハーサルで検証用サブドメインの動作確認が済んでいること
- [ ] `migration/dump-and-restore.sh` に渡す環境変数(RDS_PASSWORD 等)を手元に用意しておく

## 切替本番
1. [ ] 旧 EC2 の systemd を2つとも停止(ここから書き込み不可にする)
   ```bash
   ssh -i ~/.ssh/shuttlematch-key.pem ec2-user@3.113.92.223 '
     sudo systemctl stop shuttlematch-frontend
     sudo systemctl stop shuttlematch'
   ```
2. [ ] 最終ダンプ→新VPSへリストア(差分ではなく作り直す)。RDSはVPC内(EC2)からしか
   到達できないため、スクリプトが内部でEC2にSSHしてdocker経由でdumpする(EC2にdocker導入済み)。
   ```bash
   RDS_PASSWORD='...' \
   VPS_HOST=160.16.52.211 \
   ./migration/dump-and-restore.sh
   ```
   リストア後、public スキーマの全テーブル/シーケンスの所有者を shuttlematch ロールへ
   付け替えるところまでスクリプトに含まれている(`--no-owner`だけだとpostgresロール所有のままで
   アプリが"permission denied"になるため。2026-08-31のリハーサルで発見・修正済み)。
3. [ ] 新VPSで backend/frontend を起動し、Flyway が素通りする(新規マイグレーションを当てない)ことを確認
   ```bash
   ssh ... 'sudo systemctl start shuttlematch && sleep 5 && systemctl is-active shuttlematch'
   ssh ... 'sudo systemctl start shuttlematch-frontend && sleep 3 && systemctl is-active shuttlematch-frontend'
   ```
4. [ ] Cloudflare の `s-match.net` / `www` を Tunnel 経由の VPS へ向ける(オレンジ雲)
5. [ ] 疎通確認
   - [ ] `https://s-match.net/` が 200
   - [ ] `https://s-match.net/api/v1/rooms`(または適当なGET)が応答
   - [ ] ミニゲームのランキング `GET /api/v1/games/coin/ranking`
   - [ ] 実際にルームを1つ作って試合表生成〜セット開始まで通しで確認
   - [ ] `X-Forwarded-For` / `CF-Connecting-IP` の実ヘッダを確認(下記の別チェック参照)
6. [ ] 旧 CloudFront はまだ消さない(切り戻し先として残す)

## 切替直後(即日)
- [ ] レート制限が見ているヘッダを確認 — ミニゲームのスコア送信を1回叩き、
      `journalctl -u shuttlematch` 等で `clientKey` に渡っている値が特定の1人に偏っていないか確認する。
      (コード側は `migration` 準備時点で `CF-Connecting-IP` 優先に変更済み。
      Cloudflare Tunnel 経由でこのヘッダが正しく届くかをここで初めて実地確認する)
- [ ] `APP_CORS_ALLOWED_ORIGINS` から旧EC2 IP・CloudFrontドメインを削り、
      `https://s-match.net,https://www.s-match.net` (+ローカル開発用)だけにして再起動
- [ ] モバイルアプリからの疎通確認(接続先は `s-match.net` のままなので変更不要、動作確認のみ)

## 問題があれば即座に切り戻し
```bash
# Cloudflare の DNS を CloudFront に戻す
ssh -i ~/.ssh/shuttlematch-key.pem ec2-user@3.113.92.223 '
  sudo systemctl start shuttlematch
  sudo systemctl start shuttlematch-frontend'
```
TTLを下げてあるので数分で反映。ただし切替後に新環境へ入ったデータは旧RDSには無いので、
切り戻す場合はその分のデータは失われる。
