"""
config.py — Shared simulation constants
Hospital ER M/M/c Queue Simulation
Identical parameters to the Java implementation.
"""

from pathlib import Path

# ── Simulation Parameters ─────────────────────────────────────────────────────
NUM_PATIENTS: int   = 500     # total patients per run
NUM_SERVERS:  int   = 4       # parallel servers (doctors)
LAMBDA:       float = 10.0    # arrival rate  (patients / sim-minute)
MU:           float = 3.0     # service rate  (patients / sim-minute, per server)
RUNS:         int   = 10      # number of independent repetitions

# ── Derived constant ──────────────────────────────────────────────────────────
RHO: float = LAMBDA / (NUM_SERVERS * MU)  # server utilisation  ρ = λ/(c·μ)

# ── Output directory & file paths ─────────────────────────────────────────────
# Anchor to the directory that contains this file (Python/) so paths resolve
# correctly regardless of the working directory the user runs main.py from.
_BASE_DIR: Path = Path(__file__).parent

RESULTS_DIR: str = str(_BASE_DIR / "results")

SEQ_CSV:   str = str(_BASE_DIR / "results" / "python_sequential_results.csv")
PAR_CSV:   str = str(_BASE_DIR / "results" / "python_parallel_results.csv")
FINAL_TXT: str = str(_BASE_DIR / "results" / "python_final_results.txt")

