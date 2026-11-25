-- 参加者の端末で試合表が開かれた記録(KPI 用)。
--
-- 「作られたのに一度もセットが開始されない試合表」(2026-10〜11 で約85%)が、
-- 参加者に配られていないのか、配られたが開始ボタンが押されていないのかを分けるために持つ。
-- フロントが「運営者ではない端末で、そのルームを初めて開いたとき」に1回だけ送る
-- (端末ごとに localStorage で1回に絞る)ので、count はおおよそ「開いた参加者の端末数」になる。
-- JS を動かさないクローラー(LINE のリンクプレビューなど)は数えない。
--
-- ルームの集約(Room)には載せない。運営の振る舞いに関係しない計測値で、
-- 集約を読み書きするたびに運ぶ理由が無いため。更新は1行の UPDATE で直接行う。
alter table rooms add column participant_open_count integer not null default 0;
alter table rooms add column participant_first_opened_at timestamptz;
