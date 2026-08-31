#!/usr/bin/env bash
# ShuttleMatch: 移行後の夜間バックアップ(cron想定)。
# pg_dump | gzip -> Cloudflare R2(無料枠 10GB)へ。DBが9MB程度なので毎日フルダンプで十分。
# 30日分を保持し、月に1度は復元テストをすること(手動)。
#
# 事前準備(初回のみ、手動):
#   1. Cloudflare ダッシュボードで R2 バケットを作成(例: shuttlematch-backups)
#   2. R2 の API トークンを発行し、`rclone config` で `r2` という名前のリモートを設定
#      (type=s3, provider=Cloudflare, access_key_id/secret_access_key/endpoint を入力)
#   3. このスクリプトを /etc/cron.daily/shuttlematch-backup 等に置くか、crontab に登録
#      例: 0 3 * * * /home/ubuntu/migration/backup-cron.sh >> /var/log/shuttlematch-backup.log 2>&1
set -euo pipefail

DB_NAME="${DB_NAME:-shuttlematch}"
BUCKET="${R2_BUCKET:-shuttlematch-backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
STAMP=$(date +%Y%m%d-%H%M%S)
DUMP_FILE="/tmp/shuttlematch-${STAMP}.sql.gz"

echo "[$(date -Iseconds)] dump start"
sudo -u postgres pg_dump "$DB_NAME" | gzip > "$DUMP_FILE"
ls -lh "$DUMP_FILE"

echo "[$(date -Iseconds)] upload to r2:${BUCKET}"
rclone copy "$DUMP_FILE" "r2:${BUCKET}/daily/"
rm -f "$DUMP_FILE"

echo "[$(date -Iseconds)] prune older than ${RETENTION_DAYS}d"
rclone delete "r2:${BUCKET}/daily/" --min-age "${RETENTION_DAYS}d"

echo "[$(date -Iseconds)] done"
