-- 参加者の並び順(=表示番号)を安定させるための連番。
-- 取得クエリに ORDER BY が無く、PostgreSQL の返す順序が更新のたびに変わるため、
-- ポーリングごとに参加者番号がシャッフルされる不具合があった。その修正。
alter table room_participants add column join_order bigserial;

-- 既存行は参加日時(同時刻は ID)順で振り直す
with ordered as (
    select id, row_number() over (order by joined_at, id) as rn
    from room_participants
)
update room_participants p
set join_order = ordered.rn
from ordered
where p.id = ordered.id;

-- シーケンスを現在の最大値に合わせる
select setval(
    pg_get_serial_sequence('room_participants', 'join_order'),
    coalesce((select max(join_order) from room_participants), 1)
);
