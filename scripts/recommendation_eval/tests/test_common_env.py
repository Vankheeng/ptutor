import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(env: dict) -> list:
    code = "import common; print(common.SEED); print(common.OUT.name); print(common.SCENARIO)"
    out = subprocess.run([sys.executable, "-c", code], cwd=ROOT, capture_output=True, text=True,
                         env={**{k: v for k, v in os.environ.items() if not k.startswith("EVAL_")}, **env})
    return out.stdout.split()


def test_the_defaults_keep_the_worlds_of_the_earlier_passes():
    assert read({"EVAL_SCENARIO": "crowded"}) == ["20261020", "crowded", "crowded"]


def test_another_seed_and_working_folder_for_the_final_pass():
    assert read({"EVAL_SCENARIO": "crowded", "EVAL_SEED": "20261027", "EVAL_OUT": "crowded-final"}) ==         ["20261027", "crowded-final", "crowded"]
