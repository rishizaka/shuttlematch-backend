-- サークルの概念を廃止し、ルーム(旧セッション)を単体エンティティにする。
-- セッションからサークル参照と公開範囲を外し、サークル系テーブルを削除する。
alter table sessions drop column if exists circle_id;
alter table sessions drop column if exists visibility;

drop table if exists circle_members;
drop table if exists circles;
