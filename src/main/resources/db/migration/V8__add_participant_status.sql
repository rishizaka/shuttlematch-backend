-- 参加者の在席状態。途中早退(LEFT)を履歴として残しつつ、未開始セットの再編成から除外するために使う。
alter table session_participants add column status varchar(16) not null default 'ACTIVE';
