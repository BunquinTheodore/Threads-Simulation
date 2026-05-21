"""
parallel_simulation.py — Parallel M/M/c Simulation
Hospital ER Queue Simulation (Python)

Mirrors ParallelSimulation.java + the parallel block in Main.java.

Architecture
------------
  Main process
  ├── Generates all 500 patients (arrival + service times)
  ├── Dispatches patients to 4 server buckets (greedy, same as sequential)
  └── Submits 4 tasks to ProcessPoolExecutor
        Worker 0 → processes patients assigned to server 0
        Worker 1 → processes patients assigned to server 1
        Worker 2 → processes patients assigned to server 2
        Worker 3 → processes patients assigned to server 3

  Each worker returns the total wait for its bucket.
  Main process sums and divides by NUM_PATIENTS → avg wait.

Using ProcessPoolExecutor (not ThreadPoolExecutor) bypasses the GIL and
gives true CPU-level parallelism — matching the project plan requirement.

Metrics recorded per run
-------------------------
  exec_time_s   – wall-clock seconds  (time.perf_counter)
  cpu_pct       – system-wide CPU %   (psutil.cpu_percent)
  throughput_ps – patients / second
  avg_wait_min  – mean patient wait in simulation minutes
  speedup       – seq_avg_s / this_exec_time_s
  memory_mb     – peak heap delta in MB  (tracemalloc)

Output
------
  python_parallel_results.csv
"""

import csv
import random
import time
import tracemalloc
from concurrent.futures import ProcessPoolExecutor, as_completed
from typing import Tuple

import psutil

import config as C


# ── Worker function (must be top-level for pickle / multiprocessing) ──────────

def _server_worker(
    server_id:     int,
    arrival_times: list[float],
    service_times: list[float],
    assignments:   list[int],
) -> float:
    """
    Compute the total wait for all patients assigned to `server_id`.

    This runs in a separate process spawned by ProcessPoolExecutor.

    Parameters
    ----------
    server_id     : index of this server (0 … NUM_SERVERS-1)
    arrival_times : arrival time for each patient  (length = NUM_PATIENTS)
    service_times : service time for each patient  (length = NUM_PATIENTS)
    assignments   : server assignment for each patient (length = NUM_PATIENTS)

    Returns
    -------
    total_wait : float
        Sum of wait times for every patient routed to this server.
    """
    server_clock = 0.0
    total_wait   = 0.0

    for i in range(len(arrival_times)):
        if assignments[i] == server_id:
            start        = max(arrival_times[i], server_clock)
            total_wait  += start - arrival_times[i]
            server_clock = start + service_times[i]

    return total_wait


# ── Core simulation ───────────────────────────────────────────────────────────

def _run_once(rng: random.Random) -> float:
    """
    One parallel simulation run.

    1. Generate all patient arrival + service times (main process).
    2. Greedy dispatch → assign each patient to the earliest-free server.
    3. Submit one task per server to ProcessPoolExecutor.
    4. Sum partial waits → return avg wait (simulation minutes).
    """
    # 1. Generate patients ────────────────────────────────────────────────────
    arrival_times: list[float] = []
    service_times: list[float] = []
    t = 0.0
    for _ in range(C.NUM_PATIENTS):
        t += rng.expovariate(C.LAMBDA)
        arrival_times.append(t)
        service_times.append(rng.expovariate(C.MU))

    # 2. Greedy dispatch (same as sequential baseline) ─────────────────────────
    assignments:    list[int]   = [0] * C.NUM_PATIENTS
    server_free_at: list[float] = [0.0] * C.NUM_SERVERS

    for i in range(C.NUM_PATIENTS):
        best  = min(range(C.NUM_SERVERS), key=lambda s: server_free_at[s])
        start = max(arrival_times[i], server_free_at[best])
        server_free_at[best] = start + service_times[i]
        assignments[i]       = best

    # 3. Parallel processing ──────────────────────────────────────────────────
    total_wait = 0.0
    with ProcessPoolExecutor(max_workers=C.NUM_SERVERS) as executor:
        futures = {
            executor.submit(
                _server_worker,
                s,
                arrival_times,
                service_times,
                assignments,
            ): s
            for s in range(C.NUM_SERVERS)
        }
        for future in as_completed(futures):
            total_wait += future.result()

    return total_wait / C.NUM_PATIENTS


# ── Public entry point ────────────────────────────────────────────────────────

def run(seq_avg_s: float) -> None:
    """
    Run the parallel simulation RUNS times, print a result table, write CSV.

    Parameters
    ----------
    seq_avg_s : float
        Average sequential execution time (seconds), used to compute speedup.
    """
    rng = random.Random(42)

    exec_times:  list[float] = []
    cpu_loads:   list[float] = []
    avg_waits:   list[float] = []
    throughputs: list[float] = []
    speedups:    list[float] = []
    memories:    list[float] = []

    _print_header(
        "Exec Time (s)", "CPU (%)", "Throughput (p/s)",
        "Avg Wait (min)", "Speedup", "Memory (MB)"
    )

    # Prime psutil so first measurement is accurate
    psutil.cpu_percent(interval=None)

    with open(C.PAR_CSV, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([
            "run", "exec_time_s", "cpu_pct",
            "throughput_ps", "avg_wait_min", "speedup", "memory_mb"
        ])

        for run_num in range(1, C.RUNS + 1):
            # Reset CPU counter right before the run
            psutil.cpu_percent(interval=None)

            tracemalloc.start()

            t0       = time.perf_counter()
            avg_wait = _run_once(rng)
            t1       = time.perf_counter()

            cpu_pct      = psutil.cpu_percent(interval=None)
            _, peak      = tracemalloc.get_traced_memory()
            tracemalloc.stop()

            elapsed_s    = t1 - t0
            throughput   = C.NUM_PATIENTS / elapsed_s
            speedup      = seq_avg_s / elapsed_s if elapsed_s > 0 else 0.0
            memory_mb    = peak / (1024 * 1024)

            exec_times.append(elapsed_s)
            cpu_loads.append(cpu_pct)
            avg_waits.append(avg_wait)
            throughputs.append(throughput)
            speedups.append(speedup)
            memories.append(memory_mb)

            print(f"  {run_num:<4} | {elapsed_s:>13.6f} | {cpu_pct:>7.2f} | "
                  f"{throughput:>15.2f} | {avg_wait:>14.6f} | "
                  f"{speedup:>7.4f} | {memory_mb:>10.3f}")

            writer.writerow([
                run_num,
                f"{elapsed_s:.6f}",
                f"{cpu_pct:.2f}",
                f"{throughput:.4f}",
                f"{avg_wait:.6f}",
                f"{speedup:.4f}",
                f"{memory_mb:.3f}",
            ])

        # ── Averages ──────────────────────────────────────────────────────
        avg_exec  = sum(exec_times)  / len(exec_times)
        avg_cpu   = sum(cpu_loads)   / len(cpu_loads)
        avg_wait_ = sum(avg_waits)   / len(avg_waits)
        avg_thr   = sum(throughputs) / len(throughputs)
        avg_spd   = sum(speedups)    / len(speedups)
        avg_mem   = sum(memories)    / len(memories)

        _print_divider()
        print(f"  {'AVG':<4} | {avg_exec:>13.6f} | {avg_cpu:>7.2f} | "
              f"{avg_thr:>15.2f} | {avg_wait_:>14.6f} | "
              f"{avg_spd:>7.4f} | {avg_mem:>10.3f}")

        writer.writerow([
            "AVG",
            f"{avg_exec:.6f}",
            f"{avg_cpu:.2f}",
            f"{avg_thr:.4f}",
            f"{avg_wait_:.6f}",
            f"{avg_spd:.4f}",
            f"{avg_mem:.3f}",
        ])

    print(f"\n  >>> Parallel avg: {avg_exec:.6f} s  |  "
          f"Speedup: {avg_spd:.4f}x  |  Saved: {C.PAR_CSV}\n")


# ── Formatting helpers ────────────────────────────────────────────────────────

def _print_header(*cols: str) -> None:
    print(f"  {'Run':<4} | {cols[0]:>13} | {cols[1]:>7} | "
          f"{cols[2]:>15} | {cols[3]:>14} | {cols[4]:>7} | {cols[5]:>10}")
    _print_divider()


def _print_divider() -> None:
    print("  " + "-" * 90)
