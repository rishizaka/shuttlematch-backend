-- 「セッション」を「ルーム」に用語統一。テーブル・カラムをリネームする。
alter table sessions rename to rooms;
alter table session_participants rename to room_participants;
alter table room_participants rename column session_id to room_id;
alter table match_schedules rename column session_id to room_id;
