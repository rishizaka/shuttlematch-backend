-- ルーム単位の Push 購読。「このルームの通知をこの端末に送る」を表す。
--
-- 宛先をユーザーではなくルーム購読で持つ理由:
-- 参加者(room_participants)は user_id が null のゲストがほとんど(かんたん作成は
-- guest_name="1".."N"、自己参加も名前のみ)なので、ユーザー単位では宛先を解決できない。
--
-- participant_id は「自分の番号」(端末ローカルの自己申告)。任意で、あれば
-- 「あなたの試合です(第3セット・コート2)」のように通知を個別化できる。
create table room_push_subscriptions (
    id             uuid primary key,
    room_id        uuid not null references rooms (id) on delete cascade,
    -- Expo Push Token(例: ExponentPushToken[xxxxxxxx])。FCM/APNs のトークンではない。
    expo_token     text        not null,
    participant_id uuid,
    platform       varchar(10) not null,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    -- 同じ端末が同じルームを二重購読しないようにする(再登録は upsert)
    constraint uq_room_push_subscription unique (room_id, expo_token)
);

create index idx_room_push_subscriptions_room on room_push_subscriptions (room_id);
-- 端末が無効になった(DeviceNotRegistered)ときに全ルームから消すため
create index idx_room_push_subscriptions_token on room_push_subscriptions (expo_token);
