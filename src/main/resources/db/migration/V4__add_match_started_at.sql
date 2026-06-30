-- セット開始時刻。アクティブ(進行中)なセットは「最も新しい started_at を持つ試合」として導出する。
alter table matches add column started_at timestamptz;
