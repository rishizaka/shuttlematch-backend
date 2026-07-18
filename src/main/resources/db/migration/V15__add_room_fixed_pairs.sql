-- 固定ペア: 大会前などに「常に同じチームで組む2人」を運営が指定する。
-- 1ルームに複数設定でき、1人は最大1ペアまで(重複はアプリ側で保証)。
-- participant_a < participant_b に正規化して保存し、同じ組の重複を防ぐ。
create table room_fixed_pairs (
    id uuid primary key,
    room_id uuid not null references rooms(id) on delete cascade,
    participant_a uuid not null,
    participant_b uuid not null,
    created_at timestamptz not null default now(),
    constraint uq_room_fixed_pair unique (room_id, participant_a, participant_b)
);

create index idx_room_fixed_pairs_room on room_fixed_pairs(room_id);
