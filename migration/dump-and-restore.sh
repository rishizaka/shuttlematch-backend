#!/usr/bin/env bash
# ShuttleMatch: RDS -> 新VPS の PostgreSQL データ移行(Phase 2 のリハーサル、Phase 3 の最終移行の両方で使う)。
#
# 2026-08-31 のリハーサルで判明した2点をこのスクリプトに反映済み:
#   1. RDS は VPC 内(EC2)からしか到達できない。手元のMacやVPSから直接 pg_dump しようとすると
#      "Connection refused"(DNSがVPC内プライベートIPを返す)になる。そのため dump は
#      EC2 に SSH してその場で(docker の postgres:18-alpine で)実行し、EC2→手元→VPS と中継する。
#   2. `pg_restore --no-owner` は「所有者を設定するSQLを出さない」の意味であって、リストアした
#      ロール(=postgresで実行すると postgres)がそのままオーナーになる。アプリは shuttlematch
#      ロールで接続するため、リストア直後は "permission denied for table ..." になる。
#      そのためリストア後に public スキーマの全テーブル/シーケンスの所有者を明示的に
#      VPS_DB_USERNAME へ付け替える(REASSIGN OWNED BY は使わない。postgresロールは
#      システム側にも紐づいているため "required by the database system" で失敗する)。
#
# 使い方:
#   RDS_PASSWORD='...' \
#   VPS_HOST=<新VPSのIP> \
#   ./dump-and-restore.sh
#
# Phase 2(リハーサル)ではそのまま実行。Phase 3(最終切替)では、直前に旧EC2の
# systemd を止めてから実行すること(実行中に旧側へ書き込みが入ると分裂する)。
set -euo pipefail

EC2_HOST="${EC2_HOST:-3.113.92.223}"
EC2_SSH_KEY="${EC2_SSH_KEY:-$HOME/.ssh/shuttlematch-key.pem}"
EC2_SSH_USER="${EC2_SSH_USER:-ec2-user}"

RDS_HOST="${RDS_HOST:-shuttlematch-db.cfi8eyekipbp.ap-northeast-1.rds.amazonaws.com}"
RDS_PORT="${RDS_PORT:-5432}"
RDS_DB="${RDS_DB:-shuttlematch}"
RDS_USERNAME="${RDS_USERNAME:-shuttlematch}"
RDS_PASSWORD="${RDS_PASSWORD:?RDS_PASSWORD を指定してください(EC2の/etc/shuttlematch/app.envのDB_PASSWORDと同じ)}"

VPS_HOST="${VPS_HOST:?VPS_HOST を指定してください}"
VPS_SSH_KEY="${VPS_SSH_KEY:-$HOME/.ssh/shuttlematch-vps-key}"
VPS_SSH_USER="${VPS_SSH_USER:-ubuntu}"
VPS_DB="${VPS_DB:-shuttlematch}"
VPS_DB_USERNAME="${VPS_DB_USERNAME:-shuttlematch}"

DUMP_FILE="/tmp/shuttlematch-$(date +%Y%m%d-%H%M%S).dump"

echo "==> 1/4 EC2上でdocker確認(無ければ導入。RDSはVPC内からしか到達できないためEC2で実行する)"
ssh -i "$EC2_SSH_KEY" "${EC2_SSH_USER}@${EC2_HOST}" '
  command -v docker >/dev/null 2>&1 || (sudo dnf install -y docker && sudo systemctl enable --now docker)
'

echo "==> 2/4 RDS から pg_dump(EC2上でdocker postgres:18-alpine実行)"
ssh -i "$EC2_SSH_KEY" "${EC2_SSH_USER}@${EC2_HOST}" "
  sudo docker run --rm -e PGPASSWORD='${RDS_PASSWORD}' postgres:18-alpine \
    pg_dump -h ${RDS_HOST} -p ${RDS_PORT} -U ${RDS_USERNAME} -d ${RDS_DB} \
      --format=custom --no-owner --no-privileges \
    > /tmp/shuttlematch.dump
  ls -lh /tmp/shuttlematch.dump
"
scp -i "$EC2_SSH_KEY" "${EC2_SSH_USER}@${EC2_HOST}:/tmp/shuttlematch.dump" "$DUMP_FILE"
ssh -i "$EC2_SSH_KEY" "${EC2_SSH_USER}@${EC2_HOST}" 'rm -f /tmp/shuttlematch.dump'
ls -lh "$DUMP_FILE"

echo "==> 3/4 新VPSへ転送"
scp -i "$VPS_SSH_KEY" "$DUMP_FILE" "${VPS_SSH_USER}@${VPS_HOST}:/tmp/shuttlematch.dump"

echo "==> 4/4 新VPSでリストア(既存データは一度全消しして作り直す)+ 所有権の付け替え"
ssh -i "$VPS_SSH_KEY" "${VPS_SSH_USER}@${VPS_HOST}" bash -s <<REMOTE
set -euo pipefail
sudo -u postgres dropdb --if-exists "${VPS_DB}"
sudo -u postgres createdb -O "${VPS_DB_USERNAME}" "${VPS_DB}"
sudo -u postgres pg_restore --no-owner --no-privileges -d "${VPS_DB}" /tmp/shuttlematch.dump
rm -f /tmp/shuttlematch.dump

sudo -u postgres psql -d "${VPS_DB}" <<'SQL'
DO \$\$
DECLARE r RECORD;
BEGIN
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname='public' LOOP
    EXECUTE 'ALTER TABLE public.' || quote_ident(r.tablename) || ' OWNER TO ${VPS_DB_USERNAME}';
  END LOOP;
  FOR r IN SELECT sequencename FROM pg_sequences WHERE schemaname='public' LOOP
    EXECUTE 'ALTER SEQUENCE public.' || quote_ident(r.sequencename) || ' OWNER TO ${VPS_DB_USERNAME}';
  END LOOP;
END \$\$;
SQL

echo "flyway_schema_history の最終行:"
sudo -u postgres psql -d "${VPS_DB}" -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
REMOTE

rm -f "$DUMP_FILE"
echo "==> 完了。アプリを起動し、Flyway が新規マイグレーションを適用しない(=素通りする)ことを確認してください。"
