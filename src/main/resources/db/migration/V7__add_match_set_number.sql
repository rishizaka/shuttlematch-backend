-- 試合のセット番号。1コートあたり同一セット番号でコート番号違いの試合が並ぶ。
alter table matches add column set_number int;

-- 既存データはコート数(スケジュール内の court_number 最大値、未設定は1)から導出して埋める。
update matches m
set set_number = ((m.match_number - 1) / sub.court_count) + 1
from (
    select match_schedule_id,
           greatest(coalesce(max(court_number), 1), 1) as court_count
    from matches
    group by match_schedule_id
) sub
where m.match_schedule_id = sub.match_schedule_id;

alter table matches alter column set_number set not null;
