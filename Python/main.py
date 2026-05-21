"""
main.py — Hospital ER M/M/c Queue Simulation (Python)
Runs Sequential → Parallel → Results Formatter in one go.

Parameters
----------
  500 patients | 4 servers | λ=10 p/min | μ=3 p/min | 10 runs

Usage
-----
  pip install psutil        # one-time
  python main.py

Output files produced
---------------------
  python_sequential_results.csv
  python_parallel_results.csv
  python_final_results.txt
"""

# ── IMPORTANT: multiprocessing guard ─────────────────────────────────────────
# On Windows, ProcessPoolExecutor uses 'spawn' to create new processes.
# Without this guard, each spawned worker would re-execute main() — causing
# infinite recursion. The guard must wrap ALL top-level executable code.
# ─────────────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    import config as C
    import sequential_simulation
    import parallel_simulation
    import results_formatter

    def banner(title: str) -> None:
        line = "=" * 60
        print(f"\n{line}\n  {title}\n{line}")

    # ── Ensure results directory exists ──────────────────────────────────────
    import os
    os.makedirs(C.RESULTS_DIR, exist_ok=True)

    # ── Header ────────────────────────────────────────────────────────────────
    banner("PYTHON M/M/c HOSPITAL ER QUEUE SIMULATION")
    print(
        f"  Patients: {C.NUM_PATIENTS}  |  Servers: {C.NUM_SERVERS}  |  "
        f"lambda={C.LAMBDA:.1f}  |  mu={C.MU:.1f}  |  Runs: {C.RUNS}\n"
    )

    # ── Step 1 — Sequential Baseline ─────────────────────────────────────────
    banner("STEP 1 — Sequential Baseline")
    seq_avg_s = sequential_simulation.run()

    # ── Step 2 — Parallel Simulation ─────────────────────────────────────────
    banner("STEP 2 — Parallel (4 server processes via ProcessPoolExecutor)")
    parallel_simulation.run(seq_avg_s)

    # ── Step 3 — Comparison Report ────────────────────────────────────────────
    banner("STEP 3 — Comparison Report")
    results_formatter.run()
