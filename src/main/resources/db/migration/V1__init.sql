-- ShuttleMatch 初期スキーマ
-- 主キーは UUID。gen_random_uuid() は PostgreSQL 13+ で標準利用可能。

-- ユーザー
create table users (
    id          uuid         primary key default gen_random_uuid(),
    cognito_sub varchar(255) unique,
    name        varchar(100) not null,
    email       varchar(255) not null unique,
    created_at  timestamptz  not null default now()
);

-- サークル
create table circles (
    id          uuid         primary key default gen_random_uuid(),
    name        varchar(100) not null,
    description text,
    invite_code varchar(32)  not null unique,
    -- 参加方式: OPEN(自由参加) / APPROVAL(承認制)
    join_policy varchar(20)  not null default 'OPEN',
    created_by  uuid         not null references users (id),
    created_at  timestamptz  not null default now(),
    constraint chk_circle_join_policy check (join_policy in ('OPEN', 'APPROVAL'))
);

-- サークルメンバー
create table circle_members (
    circle_id uuid        not null references circles (id) on delete cascade,
    user_id   uuid        not null references users (id) on delete cascade,
    -- ロール: ORGANIZER(主催者) / PLAYER(一般参加者)
    role      varchar(20) not null default 'PLAYER',
    joined_at timestamptz not null default now(),
    primary key (circle_id, user_id),
    constraint chk_member_role check (role in ('ORGANIZER', 'PLAYER'))
);
create index idx_circle_members_user on circle_members (user_id);

-- セッション(1回の活動日)
create table sessions (
    id         uuid         primary key default gen_random_uuid(),
    circle_id  uuid         not null references circles (id) on delete cascade,
    title      varchar(200) not null,
    held_at    timestamptz  not null,
    location   varchar(200),
    capacity   int,
    -- ステータス: PREPARING(準備中)/OPEN(参加受付中)/GENERATED(試合生成済み)/CLOSED(終了)
    status     varchar(20)  not null default 'PREPARING',
    created_by uuid         not null references users (id),
    created_at timestamptz  not null default now(),
    constraint chk_session_status check (status in ('PREPARING', 'OPEN', 'GENERATED', 'CLOSED'))
);
create index idx_sessions_circle on sessions (circle_id);

-- 参加者(セッションごと)。user_id は NULL 許容(ゲスト参加者)
create table session_participants (
    id         uuid         primary key default gen_random_uuid(),
    session_id uuid         not null references sessions (id) on delete cascade,
    user_id    uuid         references users (id),
    guest_name varchar(100),
    joined_at  timestamptz  not null default now(),
    -- 登録ユーザー or ゲスト名のいずれかは必須
    constraint chk_participant_identity check (user_id is not null or guest_name is not null)
);
create index idx_session_participants_session on session_participants (session_id);
-- 登録ユーザーの同一セッション重複参加を防止(ゲストは対象外)
create unique index ux_session_participant_user
    on session_participants (session_id, user_id)
    where user_id is not null;

-- 試合スケジュール(セッションに対し1つ)
create table match_schedules (
    id           uuid        primary key default gen_random_uuid(),
    session_id   uuid        not null references sessions (id) on delete cascade,
    generated_at timestamptz not null default now()
);
create unique index ux_match_schedules_session on match_schedules (session_id);

-- 試合。ペアは session_participants を参照(ゲストも試合に組み込めるようにするため)
create table matches (
    id                uuid primary key default gen_random_uuid(),
    match_schedule_id uuid not null references match_schedules (id) on delete cascade,
    match_number      int  not null,
    pair_a_player1_id uuid not null references session_participants (id),
    pair_a_player2_id uuid not null references session_participants (id),
    pair_b_player1_id uuid not null references session_participants (id),
    pair_b_player2_id uuid not null references session_participants (id),
    court_number      int,
    unique (match_schedule_id, match_number)
);
create index idx_matches_schedule on matches (match_schedule_id);
