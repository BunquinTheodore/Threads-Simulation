from pathlib import Path

NUM_PATIENTS: int   = 150000
NUM_SERVERS:  int   = 4
LAMBDA:       float = 10.0
MU:           float = 3.0
RUNS:         int   = 10

_BASE_DIR: Path = Path(__file__).parent
RESULTS_DIR: str = str(_BASE_DIR / "results")
FINAL_TXT: str = str(_BASE_DIR / "results" / "python_final_results.txt")
