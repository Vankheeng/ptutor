import latency


def test_the_shipped_configuration_is_measured_in_one_pass():
    assert latency.passes("V2-final", False) == [("rerank tắt", "V2-final", False, False)]


def test_v2_full_keeps_its_cold_and_warm_passes():
    assert latency.passes("V2-full", False) == [("cold", "V2-full", False, False),
                                                ("warm", "V2-full-busy", True, False)]
    assert latency.passes("V2-full", True) == [("cold", "V2-full", False, True), ("warm", "V2-full", True, False)]
