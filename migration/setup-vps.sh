#!/usr/bin/env bash
# ShuttleMatch: さくらのVPS 初期セットアップ(Phase 1)。
# Ubuntu 24.04 LTS(amd64)。ubuntu ユーザーで実行する想定
# (さくらのVPSはUbuntuイメージだとcloud-init経由でubuntuユーザーに公開鍵を登録する。
# sudoはグループ在籍だけではパスワードが要る既定だったため、初回だけrootパスワードを使って
# NOPASSWDのsudoersを1枚追加済み。以降はこのスクリプトも含め鍵認証+sudoだけで完結する)。
#
# 実行後にやること(このスクリプトではやらない):
#   - cloudflared のトンネル作成・認証(対話が要るので手動): `cloudflared tunnel login` 等
#   - /etc/shuttlematch/app.env の作成(このスクリプトは雛形だけ置く。DB_PASSWORD 等は手で入れる)
#   - systemd ユニット(shuttlematch.service / shuttlematch-frontend.service)の配置
#     (同ディレクトリの *.service を /etc/systemd/system/ にコピーして `systemctl enable --now`)
set -euo pipefail

DEPLOY_USER="${DEPLOY_USER:-ubuntu}"
DB_NAME="${DB_NAME:-shuttlematch}"
DB_ROLE="${DB_ROLE:-shuttlematch}"

echo "==> apt 更新・自動更新の有効化"
sudo apt-get update -y
sudo apt-get upgrade -y
sudo apt-get install -y unattended-upgrades ufw curl gnupg lsb-release ca-certificates
sudo dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> ufw: SSH 以外を閉じる"
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw --force enable
sudo ufw status verbose

echo "==> OpenJDK 21"
sudo apt-get install -y openjdk-21-jdk
java -version

echo "==> Node 22 (NodeSource)"
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt-get install -y nodejs
node -v

echo "==> PostgreSQL 18 (PGDG リポジトリ。Ubuntu 標準は 16/17 までなので追加が要る)"
sudo install -d /usr/share/postgresql-common/pgdg
sudo curl -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
  --fail https://www.postgresql.org/media/keys/ACCC4CF8.asc
sudo sh -c 'echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
sudo apt-get update -y
sudo apt-get install -y postgresql-18
pg_lsclusters

echo "==> DB ロール・DB 作成(Unix ソケット限定、外部公開しない)"
sudo -u postgres psql -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DB_ROLE}') THEN
    CREATE ROLE ${DB_ROLE} LOGIN PASSWORD 'CHANGE_ME_BEFORE_USE';
  END IF;
END
\$\$;
SELECT 'CREATE DATABASE ${DB_NAME} OWNER ${DB_ROLE}'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${DB_NAME}')\gexec
SQL
echo "!! DB_ROLE のパスワードは仮値です。ALTER ROLE ${DB_ROLE} WITH PASSWORD '...' で必ず変更してください。"

echo "==> pg_hba.conf / postgresql.conf: localhost のみ許可を確認"
PG_CONF_DIR="/etc/postgresql/18/main"
sudo grep -n "^listen_addresses" "$PG_CONF_DIR/postgresql.conf" || \
  echo "listen_addresses = 'localhost'" | sudo tee -a "$PG_CONF_DIR/postgresql.conf" > /dev/null
sudo systemctl restart postgresql

echo "==> cloudflared 導入(トンネル作成・認証は手動)"
ARCH=$(dpkg --print-architecture)
curl -fsSL "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${ARCH}.deb" -o /tmp/cloudflared.deb
sudo dpkg -i /tmp/cloudflared.deb
cloudflared --version

echo "==> デプロイ用ディレクトリ"
sudo -u "$DEPLOY_USER" mkdir -p "/home/$DEPLOY_USER/frontend"
sudo mkdir -p /etc/shuttlematch
sudo chmod 750 /etc/shuttlematch

echo "==> app.env 雛形(値は後で埋める。既存 EC2 の CORS 値からドメイン以外を削ったもの)"
if [ ! -f /etc/shuttlematch/app.env ]; then
  sudo tee /etc/shuttlematch/app.env > /dev/null <<ENV
DB_URL=jdbc:postgresql://localhost:5432/${DB_NAME}
DB_USERNAME=${DB_ROLE}
DB_PASSWORD=CHANGE_ME
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://s-match.net,https://www.s-match.net
ENV
  sudo chmod 640 /etc/shuttlematch/app.env
fi

echo "==> 完了。残りの手作業:"
echo "  1. ALTER ROLE ${DB_ROLE} WITH PASSWORD '<強いパスワード>'; と /etc/shuttlematch/app.env の DB_PASSWORD を揃える"
echo "  2. cloudflared tunnel login && cloudflared tunnel create shuttlematch"
echo "  3. cloudflared のトンネル設定(config.yml)で 3000 番へルーティング"
echo "  4. shuttlematch.service / shuttlematch-frontend.service を /etc/systemd/system/ に配置して enable --now"
echo "  5. app.jar と frontend/dist を旧 EC2 と同じ手順で転送"
