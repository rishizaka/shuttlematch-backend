#!/usr/bin/env python3
"""ランダムマッチング生成ロジックの QA スクリプト。

docs/matching-spec.md の「保証する性質 P1〜P12」を複数構成で自動検証し、
PASS / FAIL / SKIP のレポートを出力する。稼働中の backend API を叩いて検証する。

使い方:
  python3 scripts/qa_matching.py                              # localhost:8080
  python3 scripts/qa_matching.py --base http://3.113.92.223:8080
  python3 scripts/qa_matching.py --trials 5                   # 性質ベースの試行回数

終了コード: すべて PASS なら 0、FAIL があれば 1。
検証で作ったルームは最後に削除する。
"""
import argparse
import json
import math
import sys
import urllib.error
import urllib.request
from collections import Counter

BASE = "http://localhost:8080"


# ---- API クライアント ----
def req(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            t = resp.read().decode()
            return resp.status, (json.loads(t) if t else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300]


def guest():
    return req("POST", "/api/v1/users/guest", {"name": "qa"})[1]["id"]


def quick_room(n, courts):
    """人数・コート数を指定してルーム作成(番号参加者+DEFAULT_SET_COUNT セット生成)。"""
    uid = guest()
    _, room = req("POST", "/api/v1/rooms/quick",
                  {"title": "QA", "courtCount": courts, "participantCount": n,
                   "createdBy": uid})
    return room


def cleanup(rid):
    req("DELETE", f"/api/v1/rooms/{rid}")


# ---- スケジュール解析 ----
def players_of(m):
    return [m["pairA"]["player1Id"], m["pairA"]["player2Id"],
            m["pairB"]["player1Id"], m["pairB"]["player2Id"]]


def index_map(participants):
    return {p["id"]: i + 1 for i, p in enumerate(participants)}


def playing_by_set(matches, idx):
    sets = sorted({m["setNumber"] for m in matches})
    out = {s: [] for s in sets}
    for m in matches:
        out[m["setNumber"]].extend(idx[x] for x in players_of(m))
    return sets, out


# ---- 個々の性質チェック。戻り値: (status, detail) ----
PASS, FAIL, SKIP = "PASS", "FAIL", "SKIP"


def check_schedule(matches, idx, n, courts, *, has_rest, set_count):
    """1スケジュールに対する P1〜P7 の静的検証。結果 dict を返す。"""
    res = {}
    sets, pbs = playing_by_set(matches, idx)
    allnums = set(range(1, n + 1))

    # P1: 各試合4名重複なし
    p1 = True
    for m in matches:
        nums = [idx[x] for x in players_of(m)]
        if len(set(nums)) != 4:
            p1 = False
    res["P1"] = (PASS if p1 else FAIL, "各試合4名・重複なし")

    # P2: 出場者は登録参加者のみ
    p2 = all(all(1 <= v <= n for v in pbs[s]) for s in sets)
    res["P2"] = (PASS if p2 else FAIL, "登録参加者のみ出場")

    # P3: 試合数 = コート数 × セット数、matchNumber 連番
    nums = sorted(m["matchNumber"] for m in matches)
    p3 = (len(matches) == courts * len(sets)
          and nums == list(range(nums[0], nums[0] + len(nums))))
    res["P3"] = (PASS if p3 else FAIL,
                 f"{len(matches)}試合 = {courts}コート × {len(sets)}セット / 連番{p3}")

    # P4: 出場・休憩回数の差 ≤ 1
    play = Counter()
    rest = Counter()
    for s in sets:
        pl = set(pbs[s])
        for v in pbs[s]:
            play[v] += 1
        for v in allnums - pl:
            rest[v] += 1
    play_diff = (max(play.values()) - min(play.values())) if play else 0
    rest_diff = (max(rest.values()) - min(rest.values())) if rest else 0
    p4 = play_diff <= 1 and rest_diff <= 1
    res["P4"] = (PASS if p4 else FAIL, f"出場差{play_diff} 休憩差{rest_diff}")

    # P5: 連続休みなし(休みが取れる構成のみ)
    if not has_rest:
        res["P5"] = (SKIP, "休みゼロ構成")
    else:
        consec = 0
        prev = set()
        for s in sets:
            r = allnums - set(pbs[s])
            consec += len(r & prev)
            prev = r
        res["P5"] = (PASS if consec == 0 else FAIL, f"連続休み延べ{consec}")

    # P6: 最大連続出場が閾値以下(休みが取れる構成のみ)
    if not has_rest:
        res["P6"] = (SKIP, f"休みゼロ構成(連続出場=セット数={len(sets)})")
    else:
        rest_per_set = n - 4 * courts
        bound = math.ceil(n / rest_per_set) + 1
        mx = 0
        for v in range(1, n + 1):
            cur = 0
            for s in sets:
                cur = cur + 1 if v in pbs[s] else 0
                mx = max(mx, cur)
        res["P6"] = (PASS if mx <= bound else FAIL,
                     f"最大連続出場{mx} (許容≤{bound})")

    # P7: 休みグループ・コート4人組の多様化(過度な固定化がない)
    if not has_rest:
        res["P7"] = (SKIP, "休みゼロ構成")
    else:
        rest_groups = Counter()
        court_groups = Counter()
        for s in sets:
            rest_groups[frozenset(allnums - set(pbs[s]))] += 1
            for m in matches:
                if m["setNumber"] == s:
                    court_groups[frozenset(idx[x] for x in players_of(m))] += 1
        max_rest_rep = max(rest_groups.values())
        max_court_rep = max(court_groups.values())
        # 同一グループが setCount の 1/3 を超えて繰り返すのは固定化とみなす。
        bound = max(2, math.ceil(len(sets) / 3))
        p7 = max_rest_rep <= bound and max_court_rep <= bound
        res["P7"] = (PASS if p7 else FAIL,
                     f"同一休み組最多{max_rest_rep}/同一コート組最多{max_court_rep} (許容≤{bound})")
    return res


def max_streak(matches, idx, n):
    sets, pbs = playing_by_set(matches, idx)
    mx = 0
    for v in range(1, n + 1):
        cur = 0
        for s in sets:
            cur = cur + 1 if v in pbs[s] else 0
            mx = max(mx, cur)
    return mx


def rest_diff(matches, idx, n):
    sets, pbs = playing_by_set(matches, idx)
    rest = Counter()
    for s in sets:
        for v in set(range(1, n + 1)) - set(pbs[s]):
            rest[v] += 1
    return (max(rest.values()) - min(rest.values())) if rest else 0


# ---- シナリオ ----
def merge(agg, res):
    """複数試行の結果をマージ(1つでも FAIL なら FAIL、全 SKIP なら SKIP)。"""
    for k, (st, detail) in res.items():
        if k not in agg:
            agg[k] = (st, detail)
        else:
            prev = agg[k][0]
            if st == FAIL:
                agg[k] = (FAIL, detail)
            elif prev == SKIP and st == PASS:
                agg[k] = (PASS, detail)


def scenario_static(n, courts, trials, target_sets):
    has_rest = n > 4 * courts
    agg = {}
    for _ in range(trials):
        room = quick_room(n, courts)
        rid = room["id"]
        idx = index_map(room["participants"])
        _, sched = req("POST", f"/api/v1/rooms/{rid}/matches/generate",
                       {"matchCount": target_sets})
        res = check_schedule(sched["matches"], idx, n, courts,
                             has_rest=has_rest, set_count=target_sets)
        merge(agg, res)
        cleanup(rid)
    return agg


def scenario_fixed_pairs(n, courts, trials, target_sets):
    """P9: 固定ペアが常に同一試合・同一 Pair・同時休憩。"""
    agg = {}
    for _ in range(trials):
        room = quick_room(n, courts)
        rid = room["id"]
        parts = room["participants"]
        idx = index_map(parts)
        a, b = parts[0]["id"], parts[1]["id"]
        c, d = parts[2]["id"], parts[3]["id"]
        req("POST", f"/api/v1/rooms/{rid}/fixed-pairs",
            {"participantA": a, "participantB": b})
        req("POST", f"/api/v1/rooms/{rid}/fixed-pairs",
            {"participantA": c, "participantB": d})
        _, sched = req("POST", f"/api/v1/rooms/{rid}/matches/generate",
                       {"matchCount": target_sets})
        ms = sched["matches"]
        ok = True
        for pa, pb in [(a, b), (c, d)]:
            for s in sorted({m["setNumber"] for m in ms}):
                sm = [m for m in ms if m["setNumber"] == s]
                # a と b は同じ試合・同じ pair、または両方休み
                ma = next((m for m in sm if pa in players_of(m)), None)
                mb = next((m for m in sm if pb in players_of(m)), None)
                if ma is None and mb is None:
                    continue  # 同時休憩 OK
                if ma is None or mb is None or ma["matchNumber"] != mb["matchNumber"]:
                    ok = False
                    break
                same_pair = ({pa, pb} == {ma["pairA"]["player1Id"], ma["pairA"]["player2Id"]}
                             or {pa, pb} == {ma["pairB"]["player1Id"], ma["pairB"]["player2Id"]})
                if not same_pair:
                    ok = False
                    break
        merge(agg, {"P9": (PASS if ok else FAIL, "固定ペアは常に同一チーム・同時出入り")})
        cleanup(rid)
    return agg


def scenario_addsets(n, courts, trials):
    """P10: generate(10)+addSets(6) が generate(16) と同水準。"""
    has_rest = n > 4 * courts
    agg = {}
    for _ in range(trials):
        # baseline: 一括16
        room = quick_room(n, courts)
        rid = room["id"]
        idx = index_map(room["participants"])
        _, sa = req("POST", f"/api/v1/rooms/{rid}/matches/generate", {"matchCount": 16})
        base_streak = max_streak(sa["matches"], idx, n)
        cleanup(rid)
        # incremental: 10 + addSets 6
        room = quick_room(n, courts)
        rid = room["id"]
        idx = index_map(room["participants"])
        req("POST", f"/api/v1/rooms/{rid}/matches/generate", {"matchCount": 10})
        _, sb = req("POST", f"/api/v1/rooms/{rid}/matches/sets", {"setCount": 6})
        inc_streak = max_streak(sb["matches"], idx, n)
        inc_restdiff = rest_diff(sb["matches"], idx, n)
        # 連続休み(境界含む)
        sets, pbs = playing_by_set(sb["matches"], idx)
        consec = 0
        prev = set()
        for s in sets:
            r = set(range(1, n + 1)) - set(pbs[s])
            consec += len(r & prev)
            prev = r
        cleanup(rid)
        ok = (inc_restdiff <= 1 and (not has_rest or consec == 0)
              and inc_streak <= base_streak + 1)
        merge(agg, {"P10": (PASS if ok else FAIL,
                            f"追加後 連続出場{inc_streak}(一括{base_streak}) 休み差{inc_restdiff} 連続休み{consec}")})
    return agg


def scenario_replan(n, courts):
    """P11/P12: 開始済み保持・早退者除外。"""
    agg = {}
    room = quick_room(n, courts)
    rid = room["id"]
    parts = room["participants"]
    idx = index_map(parts)
    _, before = req("GET", f"/api/v1/rooms/{rid}/matches")
    set1_before = sorted(
        (m["courtNumber"], sorted(idx[x] for x in players_of(m)))
        for m in before["matches"] if m["setNumber"] == 1)
    # 第1セットを開始
    req("POST", f"/api/v1/rooms/{rid}/matches/sets/1/start")
    # 早退者を1人(第2セット以降に出る想定の番号)設定 → replan
    leaver = parts[-1]["id"]
    leaver_num = idx[leaver]
    req("POST", f"/api/v1/rooms/{rid}/participants/{leaver}/leave")
    _, after = req("POST", f"/api/v1/rooms/{rid}/matches/replan")
    set1_after = sorted(
        (m["courtNumber"], sorted(idx[x] for x in players_of(m)))
        for m in after["matches"] if m["setNumber"] == 1)
    # P11: 開始済み第1セットは不変
    res11 = PASS if set1_before == set1_after else FAIL
    # P12: 早退者は未開始セット(2以降)に登場しない
    future_nums = {idx[x] for m in after["matches"] if m["setNumber"] >= 2
                   for x in players_of(m) if x in idx}
    res12 = PASS if leaver_num not in future_nums else FAIL
    merge(agg, {
        "P11": (res11, "開始済み第1セットが不変"),
        "P12": (res12, f"早退者{leaver_num}番が未開始セットに不在"),
    })
    cleanup(rid)
    return agg


# ---- レポート ----
def status_mark(st):
    return {"PASS": "✅ PASS", "FAIL": "❌ FAIL", "SKIP": "⚪ SKIP"}[st]


def print_block(title, agg):
    print(f"\n── {title} ──")
    for k in sorted(agg, key=lambda x: (int(x[1:]))):
        st, detail = agg[k]
        print(f"  {k:4} {status_mark(st):8} {detail}")


def main():
    global BASE
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=BASE)
    ap.add_argument("--trials", type=int, default=3)
    args = ap.parse_args()
    BASE = args.base.rstrip("/")

    code = req("GET", "/actuator/health")[0]
    if code != 200:
        print(f"backend に接続できません ({BASE}/actuator/health -> {code})")
        sys.exit(2)

    print(f"QA 対象: {BASE}  試行回数: {args.trials}")
    any_fail = False
    blocks = []

    # 静的検証(P1〜P7)を複数構成で
    for (n, courts, label) in [
        (10, 2, "10人2コート(毎セット2人休み)"),
        (12, 2, "12人2コート(毎セット4人休み)"),
        (6, 1, "6人1コート(毎セット2人休み)"),
        (8, 2, "8人2コート(休みゼロ=構造上の限界確認)"),
    ]:
        agg = scenario_static(n, courts, args.trials, target_sets=15)
        blocks.append((f"静的検証 {label}", agg))

    # P9 固定ペア
    blocks.append(("固定ペア P9 (10人2コート・2組固定)",
                   scenario_fixed_pairs(10, 2, args.trials, target_sets=15)))
    # P10 セット追加の引き継ぎ
    blocks.append(("セット追加の引き継ぎ P10 (10人2コート)",
                   scenario_addsets(10, 2, args.trials)))
    # P11/P12 再編成
    blocks.append(("再編成 P11/P12 (10人2コート)",
                   scenario_replan(10, 2)))

    for title, agg in blocks:
        print_block(title, agg)
        if any(st == FAIL for st, _ in agg.values()):
            any_fail = True

    # P8 は API ではシード固定できないため Java 単体テストで担保
    print("\n── 決定性 P8 ──")
    print("  P8   ⚪ SKIP   API ではシード固定不可。Java 単体テスト "
          "deterministicWithSameSeed で担保")

    print("\n" + "=" * 60)
    print("総合結果:", "❌ FAIL あり" if any_fail else "✅ すべて PASS")
    print("=" * 60)
    sys.exit(1 if any_fail else 0)


if __name__ == "__main__":
    main()
