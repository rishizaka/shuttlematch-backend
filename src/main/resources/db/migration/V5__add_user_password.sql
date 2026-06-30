-- パスワード(BCrypt ハッシュ)。NULL は未設定を表し、ログイン時はデフォルトパスワードを受け付ける。
alter table users add column password_hash varchar(255);
