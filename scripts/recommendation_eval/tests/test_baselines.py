import baselines


def run_with(side_query, candidates):
    return {"results": {side_query["id"]: {"candidates": candidates}}}


def test_popularity_on_the_student_side_follows_q():
    query = {"id": "s", "side": "S2T"}
    run = run_with(query, [{"candidateId": "a", "q": 0.4}, {"candidateId": "b", "q": 0.9}, {"candidateId": "c"}])

    assert baselines.popularity_ranking(run, query) == ["b", "a", "c"]


def test_popularity_on_the_tutor_side_follows_the_offers_received_not_the_v2a_order():
    query = {"id": "t", "side": "T2S"}
    run = run_with(query, [{"candidateId": "a", "q": None}, {"candidateId": "b", "q": None},
                           {"candidateId": "c", "q": None}])

    ranking = baselines.popularity_ranking(run, query, offers={"c": 3, "b": 1})

    assert ranking == ["c", "b", "a"]
    assert baselines.popularity_ranking(run, query, offers={}) == ["a", "b", "c"], "ties keep the logged order"
