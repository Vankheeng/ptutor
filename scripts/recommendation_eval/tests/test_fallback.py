import fallback

PHASE = "DEBUG c.p.b.s.RecommendationService : Student search 1f2e phases (ms): context=3 vector=180 rank=40"
FAILED = "WARN c.p.b.client.AiServiceClient : AI service /embed failed: java.net.http.HttpTimeoutException"


def results(*query_vectors):
    return {f"q{i}": {"queryContext": None if value is None else {"queryVector": value}}
            for i, value in enumerate(query_vectors)}


def test_the_log_context_says_which_searches_fell_back():
    run = results("QUERY", "PROFILE_FALLBACK", "PROFILE", "PROFILE_FALLBACK")

    assert fallback.from_contexts(run) == {"q1", "q3"}


def test_a_run_from_before_the_field_has_no_answer_in_its_contexts():
    assert fallback.from_contexts(results(None, None)) is None


def test_older_runs_are_read_from_the_backend_log_in_query_order():
    log = "\n".join(["started", PHASE, FAILED, "AI service /rerank failed: timeout", PHASE, PHASE, FAILED, PHASE])

    assert fallback.from_log(results(None, None, None, None), log) == {"q1", "q3"}


def test_a_log_that_does_not_line_up_with_the_run_is_not_guessed():
    assert fallback.from_log(results(None, None, None), "\n".join([PHASE, PHASE])) is None


def test_flagged_prefers_the_context_and_falls_back_to_the_log(tmp_path):
    log = tmp_path / "backend-x.log"
    log.write_text("\n".join([FAILED, PHASE, PHASE]), encoding="utf-8")

    assert fallback.flagged(results("QUERY", "QUERY"), log) == set(), "the context wins"
    assert fallback.flagged(results(None, None), log) == {"q0"}
    assert fallback.flagged(results(None, None), tmp_path / "missing.log") == set()


def test_the_rerank_queue_is_drained_once_the_worker_says_so_last():
    assert fallback.queue_drained("nothing queued in this run")
    assert not fallback.queue_drained("Rerank worker: scoring 2 pairs, 30 waiting")
    assert fallback.queue_drained("Rerank worker: scoring 1 pairs, 0 waiting\nRerank worker: queue empty")
    assert not fallback.queue_drained(
        "Rerank worker: queue empty\nRerank worker: scoring 2 pairs, 4 waiting")
