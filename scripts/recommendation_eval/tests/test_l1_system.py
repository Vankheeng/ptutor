import numpy as np

import l1_system as l1
import linear_bayes


def test_the_hidden_weights_differ_from_the_prior_but_stay_inside_the_safety_band():
    for target, prior in ((l1.W_STAR, l1.PRIOR_A), (l1.B_STAR, l1.PRIOR_B)):
        assert np.isclose(target.sum(), 1.0)
        ratio = target / prior
        assert np.all(ratio >= l1.MIN_RATIO) and np.all(ratio <= l1.MAX_RATIO), ratio
    assert np.abs(l1.W_STAR - l1.PRIOR_A).sum() > 0.25


def test_the_shift_moves_every_time_column_of_every_table_once():
    columns = [("logs", "created_at", "timestamp without time zone"), ("logs", "updated_at", "timestamp"),
               ("lessons", "lesson_date", "date"), ("my table", "at", "timestamp with time zone")]

    statements = l1.shift_statements(columns)

    assert len(statements) == 3
    assert 'UPDATE "logs" SET "created_at" = "created_at" - INTERVAL \'1 day\', ' \
           '"updated_at" = "updated_at" - INTERVAL \'1 day\';' in statements
    assert any(s.startswith('UPDATE "my table"') for s in statements)
    joined = " ".join(statements)
    assert all(f'"{column}" = "{column}" - INTERVAL' in joined for _, column, _ in columns)
    assert "DATE '2000-01-01'" in l1.maxima_sql(columns, "before")


def test_the_shift_check_finds_a_column_left_behind_and_a_time_in_the_future():
    day = 86400.0
    before = {("logs", "created_at"): 10 * day, ("logs", "updated_at"): 10 * day, ("lessons", "lesson_date"): 5 * day,
              ("empty", "created_at"): None}
    moved = {key: (None if value is None else value - day) for key, value in before.items()}
    assert l1.check_shift(before, moved) == []

    left_behind = {**moved, ("lessons", "lesson_date"): 5 * day}
    assert any("lessons.lesson_date" in problem for problem in l1.check_shift(before, left_behind))

    # A row written after the newest time seen before the move is in the future once the day is over.
    future = {**moved, ("logs", "updated_at"): 10 * day + 60}
    problems = l1.check_shift(before, future)
    assert any("future" in problem for problem in problems)


def test_parse_maxima_reads_both_passes():
    text = "before|logs|created_at|100.5\nbefore|x|y|\nafter|logs|created_at|-86299.5\nnoise line\n"
    before, after = l1.parse_maxima(text)
    assert before == {("logs", "created_at"): 100.5, ("x", "y"): None}
    assert after == {("logs", "created_at"): -86299.5}


def test_a_searcher_who_likes_the_top_posting_applies_and_reads_from_the_top():
    rng = np.random.default_rng(1)
    strong = np.tile(np.array([1.0, 1, 1, 1, 1, 1, 1]), (10, 1))
    weak = np.zeros((10, 7))
    liked = [l1.reactions(strong, rng) for _ in range(200)]
    disliked = [l1.reactions(weak, rng) for _ in range(200)]

    assert sum(any(action in ("APPLY", "CONTACTED") for _, action in r) for r in liked) > 150
    actions = [action for r in disliked for _, action in r]
    assert actions and np.mean([action == "DISMISSED" for action in actions]) > 0.95
    assert all(sum(action == "APPLY" for _, action in r) <= 1 for r in liked)
    # One chance a search, at the first loved posting.
    assert 0.25 < np.mean([any(action == "APPLY" for _, action in r) for r in liked]) < 0.45
    first = [r[0][0] for r in liked if r]
    assert np.mean(np.array(first) == 0) > 0.6  # the top is examined most


def search(events, shown):
    return {"logId": "log", "events": events,
            "shown": [{"rank": rank, "id": cid, "exploration": expl, "features": [0.1 * rank] * 7}
                      for rank, cid, expl in shown]}


def test_expected_observations_follow_the_rules_of_the_backend():
    shown = [(rank, f"p{rank}", rank == 5) for rank in range(1, 21)]
    events = [{"candidate": "p2", "action": "VIEWED", "ok": True},
              {"candidate": "p2", "action": "DISMISSED", "ok": True},  # a positive signal beats a dismissal
              {"candidate": "p3", "action": "FAVORITED", "ok": True},
              {"candidate": "p4", "action": "CONTACTED", "ok": False}]  # not recorded by the backend
    day = 86400
    applications = [("p6", 1000 + day), ("p9", 1000 + 8 * day), ("p1", 500)]  # inside, too late, before the search

    one = l1.expected_search(search(events, shown), 1000, applications)

    assert [row["rank"] for row in one["candidates"]] == list(range(1, 9))  # deepest 6 + margin 2
    rewards = {row["candidateId"]: row["reward"] for row in one["candidates"]}
    assert rewards == {"p1": 0, "p2": 0.3, "p3": 0.6, "p4": 0, "p5": 0, "p6": 1.0, "p7": 0, "p8": 0}
    assert [row["weight"] for row in one["candidates"]][4] == l1.EXPLORATION_WEIGHT
    assert l1.expected_search(search([], shown), 1000, []) is None


def test_expected_decisions_weigh_fast_acceptances_and_reject_the_unanswered_late():
    hour, day = 3600, 86400
    accepted = l1.expected_decision({"status": "ACCEPTED"}, [0.5] * 4, 0, 2 * hour, 30 * day)
    slow = l1.expected_decision({"status": "ACCEPTED"}, [0.5] * 4, 0, 30 * hour, 30 * day)
    rejected = l1.expected_decision({"status": "REJECTED"}, [0.5] * 4, 0, hour, 30 * day)
    waiting = l1.expected_decision({"status": "PENDING"}, [0.5] * 4, 0, None, 10 * day)
    expired = l1.expected_decision({"status": "PENDING"}, [0.5] * 4, 0, None, 20 * day)

    assert (accepted["label"], accepted["weight"]) == (1.0, l1.FAST_ACCEPT_WEIGHT)
    assert slow["weight"] == 1.0
    assert (rejected["label"], rejected["weight"], rejected["at"]) == (0.0, 1.0, hour)
    assert waiting is None
    assert (expired["label"], expired["weight"], expired["at"]) == (0.0, l1.IMPLICIT_WEIGHT, 14 * day)


def test_compare_rows_reports_each_kind_of_difference():
    row = {"candidateId": "p1", "rank": 1, "reward": 0.3, "weight": 1.0, "features": [0.5] * 7}
    assert l1.compare_rows([row], [dict(row)]) == []
    assert l1.compare_rows([row], []) == ["1 rows expected, 0 dumped"]
    assert l1.compare_rows([row], [dict(row, reward=0.6)]) == ["rank 1 reward: 0.3 expected, 0.6 dumped"]
    assert l1.compare_rows([row], [dict(row, features=[0.4] * 7)]) == ["rank 1 features differ"]


def test_the_replay_matches_a_dump_built_by_the_same_rules():
    rng = np.random.default_rng(3)
    prior = l1.PRIOR_A
    x = rng.random((30, 7))
    r = rng.random(30)
    s = np.ones(30)
    scale = linear_bayes.prior_scale(prior, x, r, s)
    model = linear_bayes.LinearBayesModel(prior, 50, 1, scale)
    for xi, ri in zip(x, r):
        model.update(xi, ri)
    weights = linear_bayes.project(model.mean(), prior, prior, 0.5, 2, 0.05)
    dump = {"model": "A_S2T", "version": 1, "prior": prior.tolist(), "previous": prior.tolist(),
            "priorStrength": 50, "interceptPriorStrength": 1, "forgetting": 0.99, "priorScale": scale,
            "statsA": model.a.tolist(), "statsC": model.c.tolist(), "posteriorMean": model.mean().tolist(),
            "weights": weights.tolist(),
            "training": {"searches": [{"candidates": [{"features": xi.tolist(), "reward": ri, "weight": 1.0}
                                                      for xi, ri in zip(x, r)]}], "decisions": []}}

    [difference] = l1.replay([dump])

    assert max(difference[key] for key in ("priorScale", "statsA", "statsC", "mean", "weights")) < 1e-9


def test_the_loop_check_expects_the_newest_version_activated_on_an_earlier_night():
    import analyze_l1
    days = analyze_l1.version_days([
        {"day": 15, "learn": [{"model": "A_S2T", "version": 1, "activated": False},
                              {"model": "B_S2T", "version": None, "activated": False}]},
        {"day": 16, "learn": [{"model": "A_S2T", "version": 2, "activated": True}]}])
    searches = [{"day": 16, "logId": "a"}, {"day": 17, "logId": "b"}, {"day": 17, "logId": "c"}]
    contexts = {"a": {"weights": {"A": {"version": None}, "B": {"version": None}}},
                "b": {"weights": {"A": {"version": 2}, "B": {"version": None}}},
                "c": {"weights": {"A": {"version": None}, "B": {"version": None}}}}

    rows = analyze_l1.loop_check(searches, contexts, days)

    assert days == {("A_S2T", 1): (15, False), ("A_S2T", 2): (16, True)}
    assert [(row["day"], row["A_expected"], row["ok"]) for row in rows] == [(16, None, True), (17, 2, False)]


def test_a_resumed_run_finishes_a_day_whose_activity_is_done_instead_of_doing_it_again():
    def searches(day, count, applied):
        return [{"type": "search", "day": day, "applications": [{"ok": i < applied}]} for i in range(count)]

    marked = [{"type": "activity_done", "day": 3, "searches": 100, "applications": 30, "accepted": 15,
               "rejected": 13, "unanswered": 2}]
    legacy = searches(15, l1.SEARCHES_PER_DAY, 2) + [{"type": "decision", "day": 15, "status": "ACCEPTED"},
                                                     {"type": "decision", "day": 15, "status": "PENDING"}]
    unfinished = searches(16, 40, 5)

    assert l1.day_activity(3, marked)["accepted"] == 15
    assert l1.day_activity(15, legacy) == {"day": 15, "searches": 100, "applications": 2, "accepted": 1,
                                           "rejected": 0, "unanswered": 1}
    assert l1.day_activity(16, unfinished) is None
    assert l1.day_activity(17, legacy) is None


def test_the_python_auc_is_the_one_of_the_backend():
    import analyze_l1
    rows = [{"features": [0.9, 0.5, 0.5, 0.5], "label": 1.0, "weight": 1.5},
            {"features": [0.4, 0.5, 0.5, 0.5], "label": 1.0, "weight": 1.0},
            {"features": [0.5, 0.5, 0.5, 0.5], "label": 0.0, "weight": 1.0},
            {"features": [0.1, 0.5, 0.5, 0.5], "label": 0.0, "weight": 0.5}]
    # Same case as WeightLearningServiceAucTest.
    assert abs(analyze_l1.auc(rows, [1, 0, 0, 0]) - 2.75 / 3.75) < 1e-12
    assert analyze_l1.auc(rows[:2], [1, 0, 0, 0]) is None
