"""
sequential_simulation.py — Sequential M/M/c Baseline
Hospital ER Queue Simulation (Python)

Mirrors SequentialSimulation.java + the sequential block in Main.java.

Algorithm
---------
  • Patients arrive at exponential inter-arrival times (rate = LAMBDA).
  • Each patient is dispatched to the server that becomes free soonest (greedy).
  • Service times are exponential (rate = MU).
  • Everything runs in a single process/thread — no parallelism.

Metrics recorded per run
-------------------------
  exec_time_s   – wall-clock seconds  (time.perf_counter)
  cpu_pct       – "N/A"  (no meaningful per-process CPU for sequential)
  throughput_ps – patients / second
  avg_wait_min  – mean patient wait in simulation minutes
  speedup       – always 1.00 (this IS the baseline)
  memory_mb     – peak heap delta in MB  (tracemalloc)

Output
------
  python_sequential_results.csv
"""

import csv
import random
import time
import tracemalloc
from typing import Tuple

import config as C


# ── Core simulation ───────────────────────────────────────────────────────────

def _run_once(rng: random.Random) -> Tuple[float, float]:
    """
    Simulate one full pass through NUM_PATIENTS patients sequentially.

    Returns
    -------
    avg_wait_min : float
        Mean time a patient waits before service begins (simulation minutes).
    """
    server_free_at = [0.0] * C.NUM_SERVERS
    current_time   = 0.0
    total_wait     = 0.0

    for _ in range(C.NUM_PATIENTS):
        # Next patient arrives
        current_time += rng.expovariate(C.LAMBDA)

        # Find the server that will be free soonest
        best = min(range(C.NUM_SERVERS), key=lambda s: server_free_at[s])

        # Wait = how long patient sits in queue before that server is free
        wait = max(0.0, server_free_at[best] - current_time)
        total_wait += wait

        # Update server schedule
        service_start          = max(current_time, server_free_at[best])
        server_free_at[best]   = service_start + rng.expovariate(C.MU)

    return total_wait / C.NUM_PATIENTS


# ── Public entry point ────────────────────────────────────────────────────────

def run() -> float:
    """
    Run the sequential simulation RUNS times, print a result table, write CSV.

    Returns
    -------
    seq_avg_s : float
        Average wall-clock execution time (seconds) across all runs.
        Used by parallel_simulation.py to compute speedup.
    """
    rng = random.Random(42)

    exec_times:   list[float] = []
    avg_waits:    list[float] = []
    throughputs:  list[float] = []
    memories:     list[float] = []

    # ── Table header (matches Java format) ───────────────────────────────────
    _print_header(
        "Exec Time (s)", "CPU (%)", "Throughput (p/s)",
        "Avg Wait (min)", "Speedup", "Memory (MB)"
    )

    with open(C.SEQ_CSV, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([
            "run", "exec_time_s", "cpu_pct",
            "throughput_ps", "avg_wait_min", "speedup", "memory_mb"
        ])

        for run_num in range(1, C.RUNS + 1):
            # ── Memory baseline ───────────────────────────────────────────
            tracemalloc.start()

            t0          = time.perf_counter()
            avg_wait    = _run_once(rng)
            t1          = time.perf_counter()

            _, peak     = tracemalloc.get_traced_memory()
            tracemalloc.stop()

            # ── Derive metrics ────────────────────────────────────────────
            elapsed_s   = t1 - t0
            throughput  = C.NUM_PATIENTS / elapsed_s
            memory_mb   = peak / (1024 * 1024)

            exec_times.append(elapsed_s)
            avg_waits.append(avg_wait)
            throughputs.append(throughput)
            memories.append(memory_mb)

            # ── Print row ─────────────────────────────────────────────────
            print(f"  {run_num:<4} | {elapsed_s:>13.6f} | {'N/A':>7} | "
                  f"{throughput:>15.2f} | {avg_wait:>14.6f} | "
                  f"{'1.00':>7} | {memory_mb:>10.3f}")

            # ── Write CSV row ─────────────────────────────────────────────
            writer.writerow([
                run_num,
                f"{elapsed_s:.6f}",
                "N/A",
                f"{throughput:.4f}",
                f"{avg_wait:.6f}",
                "1.00",
                f"{memory_mb:.3f}",
            ])

        # ── Averages ──────────────────────────────────────────────────────
        seq_avg_s    = sum(exec_times)   / len(exec_times)
        avg_wait_avg = sum(avg_waits)    / len(avg_waits)
        thr_avg      = sum(throughputs)  / len(throughputs)
        mem_avg      = sum(memories)     / len(memories)

        _print_divider()
        print(f"  {'AVG':<4} | {seq_avg_s:>13.6f} | {'N/A':>7} | "
              f"{thr_avg:>15.2f} | {avg_wait_avg:>14.6f} | "
              f"{'1.00':>7} | {mem_avg:>10.3f}")

        writer.writerow([
            "AVG",
            f"{seq_avg_s:.6f}",
            "N/A",
            f"{thr_avg:.4f}",
            f"{avg_wait_avg:.6f}",
            "1.00",
            f"{mem_avg:.3f}",
        ])

    print(f"\n  >>> Sequential avg: {seq_avg_s:.6f} s  |  Saved: {C.SEQ_CSV}\n")
    return seq_avg_s


# ── Formatting helpers ────────────────────────────────────────────────────────

def _print_header(*cols: str) -> None:
    print(f"  {'Run':<4} | {cols[0]:>13} | {cols[1]:>7} | "
          f"{cols[2]:>15} | {cols[3]:>14} | {cols[4]:>7} | {cols[5]:>10}")
    _print_divider()


def _print_divider() -> None:
    print("  " + "-" * 90)
