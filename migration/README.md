# AWS → さくらのVPS 移行

計画の全文は Artifact「ShuttleMatch AWS脱出プラン」(2026-08-10作成、5フェーズ構成)。
ここにあるのは Phase 1〜3 を明日すぐ実行できるようにした実務ファイル。

- 移行先: **さくらのVPS 2GB・東京第2ゾーン・Ubuntu 24.04 LTS (amd64)**(2026-08-30 契約)
- 切替タイミング: 日中でOK(深夜帯限定にはこだわらない。ダウンタイム5分以内目標)
- サーバー: IP `160.16.52.211` / ホスト名 `tk2-202-10707.vs.sakura.ne.jp`

> **2026-08-31 の訂正**: サーバー一覧の表示が一時「Rocky Linux 10 x86_64」となっており、
> それに合わせてスクリプト一式をdnf系に書き直したが、実機にSSHして確認したところ実際は
> 申込み通り **Ubuntu 24.04 LTS** だった(パネルの表示が古かった/誤っていたと思われる)。
> 以下は最終的に確定した Ubuntu 版。

## ファイル

| ファイル | 用途 | 使うフェーズ |
|---|---|---|
| `setup-vps.sh` | OS初期設定・ufw・OpenJDK21/Node22/PostgreSQL18/cloudflared導入・DB作成 | Phase 1 |
| `shuttlematch.service` / `shuttlematch-frontend.service` | systemdユニット(`/etc/systemd/system/`へ配置) | Phase 1 |
| `dump-and-restore.sh` | RDS→新VPSへのデータ移行(pg_dump 18 は docker経由、動作確認済み) | Phase 2・Phase 3 両方で使用 |
| `cutover-checklist.md` | 本番切替の手順チェックリスト(日中実施版) | Phase 3 |
| `backup-cron.sh` | 移行後の夜間バックアップ(R2へ) | Phase 3以降の運用 |

## 接続情報

- IP: `160.16.52.211`
- ログインユーザー: `ubuntu`(さくらのVPSのUbuntuイメージはcloud-init経由でこのユーザーに
  公開鍵を登録する。root には登録されないので `root@` では入れない)
- SSH鍵: `~/.ssh/shuttlematch-vps-key`(2026-08-30 に新規作成。EC2用の`shuttlematch-key.pem`とは別)
- sudo: 当初 `ubuntu` は sudo グループに入っているだけでパスワードが必要な状態だった
  (AWSのcloud-initと違い、さくらのVPSはNOPASSWDを既定で付けない)。
  2026-08-31、作成時のroot初期パスワードを1回だけ使って
  `/etc/sudoers.d/90-ubuntu-nopasswd` を追加し、以降は鍵認証+sudoだけで完結するようにした。
  **root初期パスワードはチャットに平文で貼られたため、さくらのVPSコントロールパネルから
  変更しておくことを推奨**(今後の運用では一切使わないので変更しても支障はない)。

## 事前に決まっていること

- pg_dump は `docker run postgres:18-alpine pg_dump` を使う(手元の pg_dump は14系でRDSの18.3と
  バージョン不一致のため使えない。2026-08-30 に `postgres:18-alpine` の pull・動作確認済み)。
- `serve.mjs` は既に `API_PROXY_TARGET`(既定 `localhost:8080`)決め打ちなので、
  VPS上でbackend/frontendが同居する構成に変更不要。
- レート制限(`ScoreSubmissionRateLimiter`)が見るヘッダーを `CF-Connecting-IP` 優先に変更済み
  (`GameRankingController.clientKey`)。CloudFront では届かないヘッダーなので現状は無害、
  Cloudflare Tunnel化後に自動でこちらが使われる。**ただし実際に届くかは切替後に必ず確認**
  (`cutover-checklist.md` の該当項目)。

## Phase 1 完了(2026-08-31)

- [x] OpenJDK 21 / Node 22 / PostgreSQL 18 / cloudflared 導入
- [x] ufw で SSH 以外閉鎖、`PermitRootLogin no` / `PasswordAuthentication no` に設定(鍵認証のみ)
- [x] DB ロール `shuttlematch` 作成、強いパスワードに変更済み(値は `migration/.secrets/vps-db.env`
      に保存。gitignore済み・チャットには出していない)
- [x] `shuttlematch.service` / `shuttlematch-frontend.service` を配置・enable済み
      (まだ app.jar / frontend/dist が無いので **start はしていない**。Phase 2 で転送してから起動する)

## Phase 2 完了(2026-08-31)

- [x] app.jar / frontend(dist・serve.mjs・node_modules)をVPSへ転送、起動確認
- [x] RDS→VPSへのデータ移行リハーサル成功。**ただし2つの罠を発見・修正**:
  1. RDSはVPC内(EC2)からしか到達できない(手元Mac/VPSからは`Connection refused`)。
     `dump-and-restore.sh`はEC2にSSHしてdocker経由でdumpするよう書き換え済み(EC2にdocker導入済み)。
  2. `pg_restore --no-owner`はリストアを実行したロール(postgres)がそのままオーナーになり、
     アプリ用ロール(shuttlematch)は`permission denied`になる。リストア後に
     public スキーマの全テーブル/シーケンスの所有者を明示的に付け替える処理を追加済み。
- [x] Flywayが新規マイグレーションを適用せず「Schema is up to date」で素通りすることを確認
- [x] SSHポートフォワード経由でトップページ・API疎通・実データ(ミニゲームランキング)を確認
- [x] E2E一式(fairness / organizer-lifecycle / participant-self-number / redirect / self-join)を
      リハーサル環境に向けて実行、8件全て成功
      (最初CORSで403が出たが、テスト用トンネルのOriginを一時許可して解決→本番用の値に戻し済み)

## Phase 3 完了(2026-08-31) — 本番切替済み

- [x] `cloudflared tunnel login` 完了、トンネル`shuttlematch`(ID `a96f3795-b129-4572-bc9c-463727725855`)作成
- [x] `/etc/cloudflared/config.yml` で s-match.net / www.s-match.net / vps-check.s-match.net(検証用) を
      `http://localhost:3000` へルーティング。`cloudflared` systemd サービス化・enable 済み
- [x] 検証用サブドメイン `vps-check.s-match.net` で外部疎通を先に確認(`--overwrite-dns`が必要だった
      本番ドメインの上書きの前に、影響のない形でトンネル経路そのものを検証)
- [x] 旧EC2のbackend/frontend停止 → 最終dump(EC2上でdocker経由、RDSはVPC内限定のため) →
      VPSへリストア+所有権付け替え → VPS側サービス起動、の順で切替
- [x] `cloudflared tunnel route dns --overwrite-dns` で s-match.net / www.s-match.net を新環境へ切替
      (この操作は自動モードの安全装置がブロックしたため、ユーザーに明示確認を取ってから実行)
- [x] 外部疎通確認: 両ドメインとも200、実データ(ミニゲームランキング等)も正しく復元されていることを確認
      (手元ISPのDNSキャッシュが一時的に古い答えを返し504に見えた場面があったが、
      権威DNS・`--resolve`指定での直接確認では問題なし。ローカルの一時的なキャッシュの話で実害なし)
- [x] レート制限(`CF-Connecting-IP`優先の実装)が実地で正しく機能することを確認(連続送信で429)
- [x] `npm run e2e:prod` で本番ドメインに対し8件全E2E成功(所要31.9秒、旧SSHトンネル経由の
      リハーサルより高速)
- [x] 旧CloudFront・EC2・RDSは削除せず維持(切り戻し先として2週間残す、Phase 5で解約予定)

**現在の本番環境**: さくらのVPS(Ubuntu 24.04, `160.16.52.211`) + Cloudflare Tunnel。
AWSは2026-09-12に解約済み(下記Phase 5参照)。

## まだユーザー側の作業が必要なもの

- Cloudflare R2 バケット作成・APIトークン発行(`backup-cron.sh` の前提。バックアップ運用はまだ未設定)
- **(強く推奨)さくらのVPSコントロールパネルでroot初期パスワードを変更**
  (チャットに平文で貼られたため。今後の運用では一切使わない設計にしたので、変更しても支障はない)
- モバイルアプリからの実機での動作確認(接続先は`s-match.net`のままなので理屈上は無変更で動くはずだが、
  実機での確認はまだしていない)

## Phase 4 完了(2026-08-31) — CI/CDをVPS向けに切替

- [x] 両リポジトリの`ci.yml`からAWS OIDC・SG一時開放のステップを削除、VPSへの直接SSHデプロイに変更
- [x] frontendはnode_modules転送・sha256比較の仕組みを撤去、VPS上で`npm ci`する方式に単純化
      (VPS 2GBはメモリに余裕がありEC2 912MBのようなOOMの心配がないため)
- [x] GitHub Secrets差し替え: `VPS_HOST`/`VPS_SSH_KEY`(backend/frontendそれぞれ専用鍵)を追加、
      `AWS_ROLE_ARN`/`EC2_SG_ID`/`EC2_HOST`/`EC2_SSH_KEY`を削除
- [x] デプロイ専用鍵を新規作成し、VPSの`ubuntu`ユーザーのauthorized_keysに追加
      (`~/.ssh/shuttlematch-vps-deploy-backend`, `~/.ssh/shuttlematch-vps-deploy-frontend`)
- [x] 両リポジトリのCLAUDE.mdを更新(VPS向け手順、オレンジ雲が正になった旨など)
- [x] push後の実CI実行は**GitHub Actionsのartifact容量制限(既知の問題、このセッション中ずっと発生)で
      build時点で失敗**し、deployジョブの自動検証はできなかった。そのため、新しいデプロイ専用鍵を使って
      同じ手順を手動で再現し、backend/frontendとも正常にデプロイできることを確認済み。
      容量が回復(GitHub側で6〜12時間ごとに再計算)すれば以降のpushで自動デプロイが動くはず。

## Phase 5 完了(2026-09-12) — AWS解約

12日間の安定運用(CI/CDの自動デプロイも本番で正常動作)を確認したうえで実施。

- [x] RDS: 最終スナップショット`shuttlematch-db-final-20260912`を取得 → インスタンス削除
      (2026-07-31取得の`shuttlematch-db-manual-20260801-0213`と合わせて2世代を保持)
- [x] EC2 `i-01f606364ee122d2c`(`shuttlematch-app`)を終了
- [x] Elastic IP `3.113.92.223` を解放(EC2終了だけでは自動解放されない。放置課金を回避)
- [x] EBSボリューム: EC2終了時に自動削除されたことを確認(DeleteOnTermination)
- [x] CloudFront `E2ZAQ39VPHE72R`: 無効化(反映まで数分) → 削除
- [x] ACM証明書(`s-match.net`, us-east-1)を削除(CloudFront削除後)
- [x] セキュリティグループ`shuttlematch-ec2-sg`/`shuttlematch-rds-sg`を削除
      (ec2-sgは終了直後ENI依存で一度失敗、少し待って再実行で成功)
- [x] IAMロール`github-actions-shuttlematch-deploy`のインラインポリシー`manage-deploy-ssh-rule`を
      削除してからロール自体を削除
- [x] AWS Budgetsのアラートは残置(解約漏れ・想定外課金の検知用)
- [x] 全リソースの解約を最終確認(EC2/RDS/CloudFront/ACM/EIP/SG/IAMロール、いずれも0件)

**これでAWSアカウント`264595825358`に残るのはRDSスナップショット2世代とBudgetsのみ。
月額課金はほぼ$0になるはず(次回請求で確認)。**

移行プロジェクトはこれで完了。今後インフラ費用の話が出たら、この移行の経緯
([[infra-migration-considered-not-done]])を踏まえて判断する。
