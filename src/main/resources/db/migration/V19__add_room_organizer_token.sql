-- ルームの運営者トークン。作成した端末だけが破壊的操作(終了・削除・参加者管理・試合表操作)を
-- 行えるようにする。トークンは実質パスワードなので平文は保存せず SHA-256 のハッシュだけを持つ。
-- 型は varchar。char(64) だと PostgreSQL 上は bpchar になり、
-- Hibernate の ddl-auto: validate が String マッピングと不一致で起動に失敗する。
--
-- 既存ルームは null のまま残す。導入時点で開催中のルームがあり、ここで値を埋めると
-- その運営者が誰も操作できなくなるため。null のルームは従来どおり誰でも運営操作ができる
-- (Room#isOrganizer)。導入後に作るルームは必ずハッシュを持つ。
alter table rooms add column organizer_token_hash varchar(64);
