-- ゲストユーザー(未ログインでのルーム作成者)を許可するため、email を任意にする。
-- unique 制約は維持する(PostgreSQL では NULL 同士は重複とみなされない)。
alter table users alter column email drop not null;
