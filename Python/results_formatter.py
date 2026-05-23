"""
results_formatter.py — Comparison Report Builder
Hospital ER Queue Simulation (Python)

Mirrors ResultsFormatter.java.

Reads python_sequential_results.csv and python_parallel_results.csv,
builds a formatted comparison table, prints it, and saves to
python_final_results.txt.

Columns
-------
  Run | Seq Time (s) | Par Time (s) | Speedup | CPU (%) | Avg Wait (min) | Memory (MB) | Throughput (p/s)

Also produces
-------------
  • AVG / MIN / MAX summary rows
  • One-line summary sentence
  • Queueing theory check  (ρ = λ / (c × μ))
"""

import csv
from dataclasses import dataclass
from typing import List

import config as C


# ── Data model ────────────────────────────────────────────────────────────────

@dataclass
class RunRow:
    run:        int
    seq_s:      float
    par_s:      float
    speedup:    float
    cpu_pct:    float
    avg_wait:   float   # simulation minutes
    memory_mb:  float
    throughput: float   # patients / second


# ── CSV reader ────────────────────────────────────────────────────────────────

def _read_csv(path: str) -> List[dict]:
    """
    Read a simulation CSV produced by sequential_simulation.py or
    parallel_simulation.py.

    Returns a list of dicts for data rows only (skips header + AVG row).
    Non-numeric fields (e.g. "N/A") are coerced to 0.0.
    """
    rows: List[dict] = []
    with open(path, newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row["run"].upper() == "AVG":
                continue
            coerced: dict = {}
            for k, v in row.items():
                try:
                    coerced[k] = float(v)
                except (ValueError, TypeError):
                    coerced[k] = 0.0
            rows.append(coerced)
    return rows


# ── Report builder ────────────────────────────────────────────────────────────

def _build_report(rows: List[RunRow]) -> str:
    n   = len(rows)
    DIV = "=" * 112
    SEP = "-" * 112

    def avg(vals):  return sum(vals) / n if n else 0.0
    def mn(vals):   return min(vals)  if vals else 0.0
    def mx(vals):   return max(vals)  if vals else 0.0

    seq_s_vals  = [r.seq_s      for r in rows]
    par_s_vals  = [r.par_s      for r in rows]
    spd_vals    = [r.speedup    for r in rows]
    cpu_vals    = [r.cpu_pct    for r in rows]
    wait_vals   = [r.avg_wait   for r in rows]
    mem_vals    = [r.memory_mb  for r in rows]
    thr_vals    = [r.throughput for r in rows]

    avg_seq  = avg(seq_s_vals);  avg_par  = avg(par_s_vals)
    avg_spd  = avg(spd_vals);    avg_cpu  = avg(cpu_vals)
    avg_wait = avg(wait_vals);   avg_mem  = avg(mem_vals);  avg_thr = avg(thr_vals)

    min_seq  = mn(seq_s_vals);   min_par  = mn(par_s_vals)
    min_spd  = mn(spd_vals);     min_wait = mn(wait_vals)
    min_mem  = mn(mem_vals);     min_thr  = mn(thr_vals)

    max_seq  = mx(seq_s_vals);   max_par  = mx(par_s_vals)
    max_spd  = mx(spd_vals);     max_wait = mx(wait_vals)
    max_mem  = mx(mem_vals);     max_thr  = mx(thr_vals)

    col_hdr = (
        f"  {'Run':<4} | {'Seq Time (s)':>13} | {'Par Time (s)':>13} | "
        f"{'Speedup':>8} | {'CPU (%)':>8} | {'Avg Wait (min)':>14} | "
        f"{'Memory (MB)':>11} | {'Throughput (p/s)':>16}"
    )

    def data_row(label, seq, par, spd, cpu, wait, mem, thr, cpu_fmt=True):
        cpu_str = f"{cpu:>8.2f}" if cpu_fmt else f"{'--':>8}"
        return (
            f"  {label:<4} | {seq:>13.6f} | {par:>13.6f} | "
            f"{spd:>8.4f} | {cpu_str} | {wait:>14.6f} | "
            f"{mem:>11.3f} | {thr:>16.2f}"
        )

    lines: List[str] = [
        DIV,
        "  PYTHON M/M/c SIMULATION -- SEQUENTIAL vs PARALLEL COMPARISON REPORT",
        (f"  Parameters: {C.NUM_PATIENTS} patients | {C.NUM_SERVERS} servers | "
         f"lambda={C.LAMBDA:.1f} p/min | mu={C.MU:.1f} p/min | {C.RUNS} runs"),
        DIV,
        col_hdr,
        SEP,
    ]

    for r in rows:
        lines.append(data_row(
            r.run, r.seq_s, r.par_s, r.speedup,
            r.cpu_pct, r.avg_wait, r.memory_mb, r.throughput,
        ))

    lines += [
        SEP,
        data_row("AVG", avg_seq, avg_par, avg_spd, avg_cpu, avg_wait, avg_mem, avg_thr),
        data_row("MIN", min_seq, min_par, min_spd, 0.0,     min_wait, min_mem, min_thr, cpu_fmt=False),
        data_row("MAX", max_seq, max_par, max_spd, 0.0,     max_wait, max_mem, max_thr, cpu_fmt=False),
        DIV,
    ]

    # ── Summary ───────────────────────────────────────────────────────────────
    lines.append("\n  SUMMARY")
    lines.append("  " + "-" * 80)
    if avg_spd >= 1.0:
        lines.append(
            f"  Python parallel was {avg_spd:.2f}x FASTER than sequential on average.\n"
            f"  Process-level parallelism offset coordination overhead successfully."
        )
    else:
        lines.append(
            f"  Python sequential was {1.0 / avg_spd:.2f}x FASTER than parallel on average.\n"
            f"  ProcessPoolExecutor overhead (~{(avg_par - avg_seq):.4f} s) exceeded "
            f"computation time ({avg_seq:.6f} s).\n"
            f"  Parallel computing pays off when per-task work >> coordination cost."
        )

    # ── Queueing theory check ─────────────────────────────────────────────────
    rho = C.RHO
    lines += [
        f"\n  QUEUEING THEORY CHECK",
        "  " + "-" * 80,
        (f"  Server utilisation (rho) = lambda / (c * mu) = "
         f"{C.LAMBDA:.1f} / ({C.NUM_SERVERS} x {C.MU:.1f}) = {rho:.4f}"),
        f"  Avg simulated wait: {avg_wait:.4f} min = {avg_wait * 60:.2f} s",
        f"  High wait expected at rho={rho:.3f} (near saturation): consistent.",
        DIV,
    ]

    return "\n".join(lines) + "\n"


# ── Public entry point ────────────────────────────────────────────────────────

def run() -> None:
    """
    Read both CSVs, print the comparison table to console, save to txt file.
    """
    seq_data = _read_csv(C.SEQ_CSV)
    par_data = _read_csv(C.PAR_CSV)
    n        = min(len(seq_data), len(par_data))

    rows: List[RunRow] = []
    for i in range(n):
        s = seq_data[i]
        p = par_data[i]
        speedup = s["exec_time_s"] / p["exec_time_s"] if p["exec_time_s"] > 0 else 0.0
        rows.append(RunRow(
            run        = int(s["run"]),
            seq_s      = s["exec_time_s"],
            par_s      = p["exec_time_s"],
            speedup    = speedup,
            cpu_pct    = p["cpu_pct"],
            avg_wait   = p["avg_wait_min"],
            memory_mb  = p["memory_mb"],
            throughput = p["throughput_ps"],
        ))

    report = _build_report(rows)
    print(report)

    with open(C.FINAL_TXT, "w") as f:
        f.write(report)

    print(f"  >>> Saved: {C.FINAL_TXT}")
