-- セッションの公開範囲を追加
-- PUBLIC(公開) / MEMBERS_ONLY(メンバー限定)
alter table sessions
    add column visibility varchar(20) not null default 'PUBLIC';

alter table sessions
    add constraint chk_session_visibility check (visibility in ('PUBLIC', 'MEMBERS_ONLY'));
