-- サークル参加申請(承認制フロー)
create table circle_join_requests (
    id           uuid        primary key default gen_random_uuid(),
    circle_id    uuid        not null references circles (id) on delete cascade,
    user_id      uuid        not null references users (id) on delete cascade,
    -- ステータス: PENDING(申請中)/APPROVED(承認済み)/REJECTED(却下)
    status       varchar(20) not null default 'PENDING',
    requested_at timestamptz not null default now(),
    decided_at   timestamptz,
    constraint chk_join_request_status check (status in ('PENDING', 'APPROVED', 'REJECTED'))
);
create index idx_join_requests_circle_status on circle_join_requests (circle_id, status);
-- 同一サークルへの申請中の重複を防止
create unique index ux_join_requests_pending
    on circle_join_requests (circle_id, user_id)
    where status = 'PENDING';
