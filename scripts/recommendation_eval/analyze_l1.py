"""Step 43a, the checks of l1_system.py run: row-by-row reconciliation, Python replay, distance to the hidden
weights, the loop activated -> served -> logged, and the gain on the held-out days. -> ket-qua/l1-he-thong/."""

import csv
import json
from datetime import datetime, timedelta

import numpy as np

import learning
import l1_system as l1
from common import query_json, read_json
from scoring import A_S2T, B_S2T

EPOCH = datetime(2000, 1, 1)


def seconds(text: str | None) -> float | None:
    if text is None:
        return None
    return (datetime.fromisoformat(str(text).replace(" ", "T")) - EPOCH).total_seconds()


def load_truth() -> tuple:
    searches, decisions, other = [], {}, []
    for line in l1.TRUTH.read_text(encoding="utf-8").splitlines():
        record = json.loads(line)
        if record["type"] == "search":
            searches.append(record)
        elif record["type"] == "decision":
            decisions[record["request"]] = record
        else:
            other.append(record)
    return searches, decisions, other


def load_dumps() -> dict:
    dumps = {}
    for path in sorted(l1.DUMPS.glob("*.json")):
        dump = json.loads(path.read_text(encoding="utf-8"))
        dumps.setdefault(dump["model"], []).append(dump)
    for model in dumps:
        dumps[model].sort(key=lambda dump: dump["version"])
    return dumps


def version_days(outcomes: list) -> dict:
    """(model, version) -> the simulated day whose night learned it, and whether it was activated."""
    days = {}
    for summary in outcomes:
        for outcome in summary["learn"]:
            if outcome.get("version"):
                days[(outcome["model"], outcome["version"])] = (summary["day"], outcome["activated"])
    return days


def reconcile_a(dumps: list, searches: list, log_times: dict, applications: dict, days: dict, last_day: int) -> tuple:
    rows, problems = [], []
    for dump in dumps:
        day, _ = days[(dump["model"], dump["version"])]
        offset = (last_day - day) * 86400  # moves the dump's times to the database's present
        start, end = seconds(dump["from"]) - offset, seconds(dump["trainingEnd"]) - offset
        expected = {}
        for search in searches:
            at = log_times.get(search["logId"])
            if at is None or not (start < at <= end):
                continue
            one = l1.expected_search(search, at, applications.get(search["owner"], []))
            if one is not None:
                expected[search["logId"]] = one
        dumped = {search["logId"]: search for search in dump["training"]["searches"]}
        missing, extra = set(expected) - set(dumped), set(dumped) - set(expected)
        mismatched, candidate_rows = 0, 0
        for log_id in set(expected) & set(dumped):
            found = l1.compare_rows(expected[log_id]["candidates"], dumped[log_id]["candidates"])
            candidate_rows += len(dumped[log_id]["candidates"])
            if abs(seconds(dumped[log_id]["searchedAt"]) - offset - log_times[log_id]) > 1e-3:
                found.append("searchedAt differs")
            if found:
                mismatched += 1
                problems.append({"version": dump["version"], "logId": log_id, "problems": found[:3]})
        for log_id in missing:
            problems.append({"version": dump["version"], "logId": log_id, "problems": ["expected, not dumped"]})
        for log_id in extra:
            problems.append({"version": dump["version"], "logId": log_id, "problems": ["dumped, not expected"]})
        rows.append({"model": dump["model"], "version": dump["version"], "day": day,
                     "searches_expected": len(expected), "searches_dumped": len(dumped),
                     "candidate_rows": candidate_rows, "missing": len(missing), "extra": len(extra),
                     "mismatched": mismatched})
    return rows, problems


def reconcile_b(dumps: list, decisions: dict, snapshots: dict, request_times: dict, days: dict,
                last_day: int) -> tuple:
    rows, problems = [], []
    for dump in dumps:
        day, _ = days[(dump["model"], dump["version"])]
        offset = (last_day - day) * 86400
        start, end = seconds(dump["from"]) - offset, seconds(dump["trainingEnd"]) - offset
        expected = {}
        for request, decision in decisions.items():
            snapshot = snapshots.get(request)
            times = request_times.get(request)
            if snapshot is None or times is None:
                continue
            one = l1.expected_decision(decision, snapshot, times["created"], times["responded"], end)
            if one is not None and start < one["at"] <= end:
                expected[request] = one
        dumped = {row["requestId"]: row for row in dump["training"]["decisions"]}
        mismatched = 0
        for request in set(expected) & set(dumped):
            want, got = expected[request], dumped[request]
            found = [key for key in ("label", "weight") if abs(want[key] - got[key]) > 1e-9]
            if np.max(np.abs(np.array(want["features"]) - np.array(got["features"]))) > 1e-9:
                found.append("features")
            if abs(seconds(got["at"]) - offset - want["at"]) > 1:
                found.append("at")
            if found:
                mismatched += 1
                problems.append({"version": dump["version"], "request": request, "problems": found})
        missing, extra = set(expected) - set(dumped), set(dumped) - set(expected)
        for request in missing:
            problems.append({"version": dump["version"], "request": request, "problems": ["expected, not dumped"]})
        for request in extra:
            problems.append({"version": dump["version"], "request": request, "problems": ["dumped, not expected"]})
        rows.append({"model": dump["model"], "version": dump["version"], "day": day,
                     "samples_expected": len(expected), "samples_dumped": len(dumped), "missing": len(missing),
                     "extra": len(extra), "mismatched": mismatched})
    return rows, problems


def loop_check(searches: list, contexts: dict, days: dict) -> list:
    """Per day: the version each model should serve (the newest activated one learned on an earlier night) and the
    versions the logs say were used."""
    rows = []
    for day in sorted({search["day"] for search in searches}):
        expected = {}
        for (model, version), (learned_day, activated) in days.items():
            if activated and learned_day < day:
                expected[model] = max(expected.get(model, 0), version)
        used = {"A": {}, "B": {}}
        for search in (s for s in searches if s["day"] == day):
            weights = (contexts.get(search["logId"]) or {}).get("weights") or {}
            for side in ("A", "B"):
                version = (weights.get(side) or {}).get("version")
                used[side][version] = used[side].get(version, 0) + 1
        rows.append({"day": day, "A_expected": expected.get("A_S2T"), "A_used": json.dumps(used["A"]),
                     "B_expected": expected.get("B_S2T"), "B_used": json.dumps(used["B"]),
                     "ok": all(set(used[side]) <= {expected.get(model)}
                               for side, model in (("A", "A_S2T"), ("B", "B_S2T")))})
    return rows


def ranking_gain(holdout: list, logs: dict, weights_a, weights_b) -> dict:
    """Mean expected reward (under the hidden weights) and NDCG@10 of the top 10, learned vs prior, re-ranking the
    logged candidates of the held-out searches offline."""
    def quality(entry):
        return 0.7 + 0.3 * (0.5 if entry.get("q") is None else entry["q"])

    diffs_reward, diffs_ndcg, n = [], [], 0
    for search in holdout:
        entries = sorted(logs.get(search["logId"]) or [], key=lambda e: e.get("rank") or 10 ** 6)
        if len(entries) < l1.SHOWN:
            continue
        x = np.array([l1.vector(e.get("features"), A_S2T) for e in entries])
        z = np.array([l1.vector({"hist": (e.get("willingness") or {}).get("hist"),
                                 **(e.get("willingness") or {})}, B_S2T) for e in entries])
        cap = np.array([(e.get("willingness") or {}).get("cap", 1.0) for e in entries])
        factor = np.array([quality(e) * (e.get("g") or 1.0) for e in entries])
        u = l1.utility(x)
        gain = learning.attraction(u) * learning.level(u)
        examine = learning.EXAMINE ** np.arange(l1.SHOWN)
        discount = 1 / np.log2(np.arange(2, l1.SHOWN + 2))
        ideal = np.sort(gain)[::-1][:l1.SHOWN] @ discount

        def metrics(wa, wb):
            a, b = x @ wa, (z @ wb) * cap
            r = np.where((a > 0) & (b > 0), np.maximum(a, 1e-12) ** 0.65 * np.maximum(b, 1e-12) ** 0.35, 0)
            order = np.argsort(-(r * factor), kind="stable")[:l1.SHOWN]
            return float(examine @ gain[order]), float(gain[order] @ discount / ideal) if ideal > 0 else 0.0

        learned, prior = metrics(weights_a, weights_b), metrics(l1.PRIOR_A, l1.PRIOR_B)
        diffs_reward.append(learned[0] - prior[0])
        diffs_ndcg.append(learned[1] - prior[1])
        n += 1
    rng = np.random.default_rng(l1.SEED)

    def interval(values):
        values = np.array(values)
        boots = [rng.choice(values, len(values)).mean() for _ in range(2000)]
        return float(values.mean()), float(np.percentile(boots, 2.5)), float(np.percentile(boots, 97.5))

    return {"searches": n, "reward": interval(diffs_reward), "ndcg": interval(diffs_ndcg)}


def reference_levels(holdout: list, logs: dict, learned_a, learned_b) -> dict:
    """Mean expected reward of the top 10 on the held-out searches for each pair of weights, the hidden ones as the
    reference: how much of what knowing w* would gain over the prior the learned weights get."""
    pairs = {"prior": (l1.PRIOR_A, l1.PRIOR_B), "learned": (learned_a, learned_b),
             "learned_a_prior_b": (learned_a, l1.PRIOR_B), "hidden_a_prior_b": (l1.W_STAR, l1.PRIOR_B),
             "hidden": (l1.W_STAR, l1.B_STAR)}
    values = {name: [] for name in pairs}
    for search in holdout:
        entries = sorted(logs.get(search["logId"]) or [], key=lambda e: e.get("rank") or 10 ** 6)
        if len(entries) < l1.SHOWN:
            continue
        x = np.array([l1.vector(e.get("features"), A_S2T) for e in entries])
        z = np.array([l1.vector(e.get("willingness"), B_S2T) for e in entries])
        cap = np.array([(e.get("willingness") or {}).get("cap", 1.0) for e in entries])
        factor = np.array([(0.7 + 0.3 * (0.5 if e.get("q") is None else e["q"])) * (e.get("g") or 1.0)
                           for e in entries])
        u = l1.utility(x)
        gain = learning.attraction(u) * learning.level(u)
        for name, (wa, wb) in pairs.items():
            a, b = x @ wa, (z @ wb) * cap
            r = np.where((a > 0) & (b > 0), np.maximum(a, 1e-12) ** 0.65 * np.maximum(b, 1e-12) ** 0.35, 0)
            order = np.argsort(-(r * factor), kind="stable")[:l1.SHOWN]
            values[name].append(float(learning.EXAMINE ** np.arange(l1.SHOWN) @ gain[order]))
    means = {name: float(np.mean(v)) for name, v in values.items()}
    share = lambda learned, hidden: (means[learned] - means["prior"]) / (means[hidden] - means["prior"])
    means["share_of_hidden_gain_a"] = share("learned_a_prior_b", "hidden_a_prior_b")
    means["share_of_hidden_gain"] = share("learned", "hidden")
    return means


def auc(decisions: list, weights) -> float | None:
    """Weighted AUC of Σ w·x, as WeightLearningService.auc; None without both labels."""
    positives = [d for d in decisions if d["label"] >= 0.5]
    negatives = [d for d in decisions if d["label"] < 0.5]
    if not positives or not negatives:
        return None
    w = np.asarray(weights, dtype=float)
    agree = total = 0.0
    for p in positives:
        high = float(np.asarray(p["features"]) @ w)
        for q in negatives:
            low = float(np.asarray(q["features"]) @ w)
            pair = p["weight"] * q["weight"]
            agree += pair * (1.0 if high > low + 1e-12 else 0.5 if abs(high - low) <= 1e-12 else 0.0)
            total += pair
    return agree / total


def reactivation(dumps: list, tolerance: float = 0.002) -> list:
    """Per version of the willingness side: would it be activated by AUC within `tolerance` (12.7 since
    27/10/2026), recomputed from its held-out data, next to the log-loss rule it ran with."""
    rows = []
    for dump in dumps:
        evaluation = dump["evaluation"]
        decisions = dump["heldOut"]["decisions"]
        learned, prior = auc(decisions, dump["weights"]), auc(decisions, dump["prior"])
        enough = bool(evaluation.get("enoughData"))
        rows.append({"version": dump["version"], "enough_data": enough, "auc_learned": learned, "auc_prior": prior,
                     "logloss_learned": evaluation.get("learned"), "logloss_prior": evaluation.get("prior"),
                     "activated_then": dump["activated"],
                     "activated_auc": enough and learned is not None and learned >= prior - tolerance - 1e-9})
    return rows


def write_rows(name: str, rows: list) -> None:
    if not rows:
        return
    l1.RESULTS.mkdir(parents=True, exist_ok=True)
    with (l1.RESULTS / name).open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def plot(errors: list) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    figure, axes = plt.subplots(1, 2, figsize=(10, 3.6))
    for axis, model, title in ((axes[0], "A_S2T", "a (A_S2T): ‖w − w*‖₁"), (axes[1], "B_S2T", "b (B_S2T): ‖w − b*‖₁")):
        rows = [row for row in errors if row["model"] == model]
        axis.plot([row["day"] for row in rows], [row["error"] for row in rows], marker="o", ms=3, label="học được")
        if rows:
            axis.axhline(rows[0]["prior_error"], color="grey", ls="--", label="prior")
        axis.set_title(title)
        axis.set_xlabel("ngày giả lập")
        axis.legend()
    figure.tight_layout()
    figure.savefig(l1.RESULTS / "sai-so-theo-ngay.png", dpi=130)


def main() -> None:
    current = read_json(l1.STATE)
    last_day = current["day"]
    searches, decisions, other = load_truth()
    dumps = load_dumps()
    days = version_days(current["outcomes"])
    log_rows = query_json("SELECT id, created_at::text AS at, query_context AS q, returned_candidate_ids AS c "
                          "FROM recommendation_logs WHERE deleted_at IS NULL AND direction = 'STUDENT_TO_TUTOR'")
    log_times = {row["id"]: seconds(row["at"]) for row in log_rows}
    contexts = {row["id"]: row["q"] for row in log_rows}
    logs = {row["id"]: row["c"] for row in log_rows}
    request_ids = [a["id"] for s in searches for a in s["applications"] if a.get("ok")]
    request_rows = query_json("SELECT id, created_at::text AS created, responded_at::text AS responded "
                              "FROM student_tutor_requests WHERE id IN ("
                              + ",".join(f"'{r}'" for r in request_ids) + ")") if request_ids else []
    request_times = {row["id"]: {"created": seconds(row["created"]), "responded": seconds(row["responded"])}
                     for row in request_rows}
    applications, snapshots = {}, {}
    for search in searches:
        for application in search["applications"]:
            if application.get("ok") and application["id"] in request_times:
                applications.setdefault(search["owner"], []).append(
                    (application["posting"], request_times[application["id"]]["created"]))
                snapshots[application["id"]] = application.get("snapshot")

    a_rows, a_problems = reconcile_a(dumps.get("A_S2T", []), searches, log_times, applications, days, last_day)
    b_rows, b_problems = reconcile_b(dumps.get("B_S2T", []), decisions, snapshots, request_times, days, last_day)
    replays = l1.replay(dumps.get("A_S2T", [])) + l1.replay(dumps.get("B_S2T", []))
    errors = []
    for model, target, prior in (("A_S2T", l1.W_STAR, l1.PRIOR_A), ("B_S2T", l1.B_STAR, l1.PRIOR_B)):
        for dump in dumps.get(model, []):
            day, activated = days[(model, dump["version"])]
            errors.append({"model": model, "version": dump["version"], "day": day, "activated": activated,
                           "error": float(np.abs(np.array(dump["weights"]) - target).sum()),
                           "prior_error": float(np.abs(prior - target).sum()),
                           "weights": " ".join(f"{v:.3f}" for v in dump["weights"])})
    loop = loop_check(searches, contexts, days)
    active = {model: max((d for d in dumps.get(model, []) if days[(model, d["version"])][1]),
                         key=lambda d: d["version"], default=None) for model in ("A_S2T", "B_S2T")}
    holdout = [s for s in searches if s["day"] > last_day - l1.HOLDOUT_DAYS]
    gain = ranking_gain(holdout, logs,
                        np.array(active["A_S2T"]["weights"]) if active["A_S2T"] else l1.PRIOR_A,
                        np.array(active["B_S2T"]["weights"]) if active["B_S2T"] else l1.PRIOR_B)

    reference = reference_levels(holdout, logs,
                                 np.array(active["A_S2T"]["weights"]) if active["A_S2T"] else l1.PRIOR_A,
                                 np.array(active["B_S2T"]["weights"]) if active["B_S2T"] else l1.PRIOR_B)
    reactivated = reactivation(dumps.get("B_S2T", []))
    write_rows("kich-hoat-b-auc.csv", reactivated)
    write_rows("doi-chieu-a.csv", a_rows)
    write_rows("doi-chieu-b.csv", b_rows)
    write_rows("phat-lai-python.csv", replays)
    write_rows("sai-so.csv", errors)
    write_rows("vong-khep-kin.csv", loop)
    plot(errors)
    summary = {
        "days": last_day, "searches": len(searches), "decisions": len(decisions),
        "other_records": {kind: sum(1 for r in other if r["type"] == kind) for kind in {r["type"] for r in other}},
        "versions": {model: len(dumps.get(model, [])) for model in ("A_S2T", "B_S2T")},
        "activated": {model: sum(1 for d in dumps.get(model, []) if days[(model, d["version"])][1])
                      for model in ("A_S2T", "B_S2T")},
        "reconcile_a": {"rows": sum(r["candidate_rows"] for r in a_rows), "problems": len(a_problems)},
        "reconcile_b": {"samples": sum(r["samples_dumped"] for r in b_rows), "problems": len(b_problems)},
        "replay_max_difference": max((max(r["statsA"], r["statsC"], r["mean"], r["weights"]) for r in replays),
                                     default=None),
        "loop_days_ok": f"{sum(r['ok'] for r in loop)}/{len(loop)}",
        "holdout_gain": gain,
        "holdout_reference": reference,
        "b_activation_by_auc": {"enough_data": sum(r["enough_data"] for r in reactivated),
                                "activated_then_logloss": sum(r["activated_then"] for r in reactivated),
                                "activated_by_auc": sum(r["activated_auc"] for r in reactivated)},
        "active_versions": {model: active[model]["version"] if active[model] else None for model in active},
        "first_last_error": {model: [rows[0]["error"], rows[-1]["error"]] if rows else None
                             for model in ("A_S2T", "B_S2T")
                             for rows in [[e for e in errors if e["model"] == model]]},
    }
    l1.RESULTS.mkdir(parents=True, exist_ok=True)
    (l1.RESULTS / "tom-tat.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding="utf-8")
    (l1.RESULTS / "chenh-lech.json").write_text(json.dumps({"a": a_problems[:200], "b": b_problems[:200]},
                                                           ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
