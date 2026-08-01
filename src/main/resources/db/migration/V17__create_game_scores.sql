-- ミニゲーム(/game)のランキング。昔のゲーセンのハイスコア表と同じで、上位5件だけを残す。
--
-- 認証が無いので誰でも登録できる。「上位5件しか残さない」「ゲームごとのスコア上限で弾く」
-- 「同一IPにレート制限をかける」の3点で荒らしの大半を止める割り切り。
-- 本気の改ざん(APIを直接叩く)は防げないが、ミニゲームに見合うコストで抑える。
create table game_scores (
    id          uuid primary key,
    -- ミニゲームの識別子。フロントの /game/{slug} と同じ (flap / rain / coin / flick)。
    game        varchar(16) not null,
    -- プレイヤーが自分で入れた名前。最大8文字。
    player_name varchar(8)  not null,
    score       integer     not null,
    recorded_at timestamptz not null default now()
);

-- ランキングの取得(上位5件)と、6位以下の切り捨てで使う。
-- 同点は先に記録した方を上位にするため、recorded_at は昇順。
create index idx_game_scores_ranking on game_scores (game, score desc, recorded_at);
