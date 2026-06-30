#!/usr/bin/env bash
# ローカルでバックエンドを起動するワンコマンドスクリプト。
#   使い方: ./run.sh
# JAVA_HOME の設定と PostgreSQL の起動を自動で面倒みる。
set -euo pipefail

# JAVA_HOME 未設定なら Homebrew の openjdk@21 を使う
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"

PG_BIN="/opt/homebrew/opt/postgresql@14/bin"

# PostgreSQL が起動していなければ起動する
if ! "${PG_BIN}/pg_isready" -h localhost -p 5432 >/dev/null 2>&1; then
  echo "▶ PostgreSQL を起動します..."
  brew services start postgresql@14
  # 起動待ち
  for _ in $(seq 1 10); do
    "${PG_BIN}/pg_isready" -h localhost -p 5432 >/dev/null 2>&1 && break
    sleep 1
  done
fi
echo "✔ PostgreSQL 稼働中"

echo "▶ バックエンドを起動します (http://localhost:8080, Swagger: /swagger-ui.html)"
exec ./gradlew bootRun
