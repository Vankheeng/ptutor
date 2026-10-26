import pytest

import market


class FakeSql:
    """Records statements; CREATE fails the first `fail` times, the check answers `exists`."""

    def __init__(self, fail=0, exists=True):
        self.statements, self.fail, self.exists = [], fail, exists

    def __call__(self, statement):
        self.statements.append(statement)
        if statement.startswith("CREATE DATABASE") and self.fail:
            self.fail -= 1
            raise RuntimeError("psql exited with code 137: (no message)")
        if statement.startswith("SELECT 1 FROM pg_database"):
            return "1\n" if self.exists else ""
        return ""


def test_rebuilds_from_the_seed_after_closing_every_connection():
    sql = FakeSql()

    market.restore(sql=sql, busy=lambda port: False, sleep=lambda seconds: None)

    seed = f"{market.DATABASE}_seed"
    assert sql.statements[0].startswith("SELECT pg_terminate_backend") and seed in sql.statements[0]
    assert sql.statements[1] == f"DROP DATABASE IF EXISTS {market.DATABASE} WITH (FORCE);"
    assert sql.statements[2] == f"CREATE DATABASE {market.DATABASE} TEMPLATE {seed};"


def test_a_transient_failure_is_retried():
    sql = FakeSql(fail=1)
    waits = []

    market.restore(sql=sql, busy=lambda port: False, sleep=waits.append)

    assert sum(statement.startswith("CREATE DATABASE") for statement in sql.statements) == 2
    assert len(waits) == 1


def test_gives_up_after_the_last_attempt_and_says_why():
    with pytest.raises(RuntimeError, match="code 137"):
        market.restore(attempts=2, sql=FakeSql(fail=5), busy=lambda port: False, sleep=lambda seconds: None)


def test_a_database_missing_after_create_is_an_error():
    with pytest.raises(RuntimeError, match="missing"):
        market.restore(attempts=1, sql=FakeSql(exists=False), busy=lambda port: False, sleep=lambda seconds: None)


def test_refuses_while_a_backend_is_up():
    sql = FakeSql()

    with pytest.raises(SystemExit, match="8091"):
        market.restore(sql=sql, busy=lambda port: port == 8091, sleep=lambda seconds: None)
    assert sql.statements == [], "nothing is dropped while a helper still writes into the database"


def test_random_reference_shuffles_the_logged_candidates(monkeypatch):
    import random

    monkeypatch.setattr(market, "logged_candidates",
                        lambda result: [{"candidateId": c, "q": q} for c, q in (("a", 0.2), ("b", 0.9), ("c", 0.5))])

    assert market.popularity_order({"logId": "x"}) == ["b", "c", "a"]
    shuffled = market.random_order({"logId": "x"}, random.Random(3))
    assert sorted(shuffled) == ["a", "b", "c"]
    assert shuffled == market.random_order({"logId": "x"}, random.Random(3)), "same seed, same order"


def test_a_decision_draws_the_same_coin_in_every_configuration():
    assert market.coin("decision", "s001", "p1", 1) == market.coin("decision", "s001", "p1", 1)
    assert market.coin("decision", "s001", "p1", 1) != market.coin("decision", "s001", "p1", 2)
    assert 0 <= market.coin("decision", "s002", "p9", 3) < 1
