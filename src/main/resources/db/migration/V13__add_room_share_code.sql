-- URL共有用の短い共有コード。/r/{code} でルームを解決する。
alter table rooms add column share_code varchar(12);

-- 既存ルームには id 由来の決定的なコードを採番する(md5先頭8文字。衝突は実質起きない)。
update rooms set share_code = substr(md5(id::text), 1, 8) where share_code is null;

alter table rooms alter column share_code set not null;
create unique index ux_rooms_share_code on rooms (share_code);
