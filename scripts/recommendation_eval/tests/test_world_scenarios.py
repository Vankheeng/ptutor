"""The calibration of the crowded scenario (design 13, 13.3) must leave the other scenarios exactly as they were."""
import json
import os
import subprocess
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent.parent
CHECK = """
import json, sys
import world
from common import OUT, read_json
saved = read_json(OUT / "world.json")
built = json.loads(json.dumps(world.build(), ensure_ascii=False))
built.pop("scenario")  # added to world.json after the base world was saved (21/10/2026)
saved.pop("scenario", None)
assert built == saved, "the world drawn differs from the one saved"
students = {s["key"]: s for s in saved["students"]}
for tutor in saved["tutors"][:40]:
    for posting in tutor["postings"]:
        for student in saved["students"][:40]:
            world.rel(student, posting, tutor); world.p_accept(student, posting, tutor)
print(json.dumps({"beta": world.BETA, "rel_sum": sum(world.rel(s, p, t) for t in saved["tutors"][:40]
                  for p in t["postings"] for s in saved["students"][:40])}))
"""


@pytest.mark.parametrize("scenario", ["base", "text-only"])
def test_other_scenarios_draw_the_same_world(scenario):
    if not (HERE / "out" / scenario / "world.json").exists():
        pytest.skip(f"no saved world for {scenario}")
    env = {**os.environ, "EVAL_SCENARIO": scenario, "PYTHONIOENCODING": "utf-8"}
    result = subprocess.run([sys.executable, "-c", CHECK], cwd=HERE, env=env, capture_output=True, text=True,
                            encoding="utf-8")
    assert result.returncode == 0, result.stderr[-2000:]
    assert json.loads(result.stdout.strip().splitlines()[-1])["beta"]["b0"] == -1.0
