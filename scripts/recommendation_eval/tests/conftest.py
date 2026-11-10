import sys
from pathlib import Path

# The evaluation scripts import each other by module name.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
