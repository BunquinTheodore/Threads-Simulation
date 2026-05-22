import os
import time
import random
import psutil
from concurrent.futures import ProcessPoolExecutor, as_completed
import config as C
import sequential_simulation

# ── Worker for Parallel Runs ────────────────────────────────────────────────
# This function runs completely independently in a child OS process!
def run_parallel_worker(seed):
    import time
    import random
    import psutil
    import sequential_simulation

    # Initialize child process monitoring
    p = psutil.Process()
    p.cpu_percent(interval=None) # Prime CPU reading
    
    t0 = time.perf_counter()
    rng = random.Random(seed)
    
    wait = sequential_simulation.run_once(rng)
    
    t1 = time.perf_counter()
    
    exec_time = (t1 - t0) * 1000.0  # ms
    cpu = p.cpu_percent(interval=None) / psutil.cpu_count()
    
    # Absolute RSS memory footprint of the child process
    mem_mb = p.memory_info().rss / (1024.0 * 1024.0)
    
    return wait, exec_time, cpu, mem_mb

if __name__ == "__main__":
    os.makedirs(C.RESULTS_DIR, exist_ok=True)
    
    p_main = psutil.Process()
    p_main.cpu_percent(interval=None) # Prime main process CPU
    
    print("=" * 88)
    print("  PYTHON M/M/c HOSPITAL ER QUEUE SIMULATION")
    print(f"  Patients: {C.NUM_PATIENTS} | Servers: {C.NUM_SERVERS} | lambda={C.LAMBDA:.1f} | mu={C.MU:.1f} | Runs: {C.RUNS}")
    print("=" * 88 + "\n")

    # --- 1. SEQUENTIAL ---
    print("Running 10 attempts SEQUENTIALLY using normal sequential implementation...")
    
    seq_waits = [0.0] * C.RUNS
    seq_times = [0.0] * C.RUNS
    seq_cpus  = [0.0] * C.RUNS
    seq_mems  = [0.0] * C.RUNS
    seq_thrs  = [0.0] * C.RUNS
    
    print(f"  {'Run':<4} | {'Exec Time (ms)':<14} | {'Avg Wait (min)':<14} | {'CPU (%)':<10} | {'Memory (MB)':<11} | {'Throughput (p/s)':<16}")
    print("  " + "-" * 84)

    seq_start = time.perf_counter()
    
    for i in range(C.RUNS):
        t0 = time.perf_counter()
        
        rng = random.Random(42 + i)
        seq_waits[i] = sequential_simulation.run_once(rng)
        
        t1 = time.perf_counter()
        
        seq_times[i] = (t1 - t0) * 1000.0
        seq_cpus[i] = p_main.cpu_percent(interval=None) / psutil.cpu_count()
        seq_mems[i] = p_main.memory_info().rss / (1024.0 * 1024.0)
        seq_thrs[i] = C.NUM_PATIENTS / (seq_times[i] / 1000.0) if seq_times[i] > 0 else 0
        
        print(f"  {i + 1:<4} | {seq_times[i]:>14.3f} | {seq_waits[i]:>14.6f} | {seq_cpus[i]:>10.2f} | {seq_mems[i]:>11.3f} | {seq_thrs[i]:>16.2f}")
    
    seq_end = time.perf_counter()
    
    seq_total_time_ms = (seq_end - seq_start) * 1000.0
    seq_avg_wait = sum(seq_waits) / C.RUNS
    seq_avg_time = sum(seq_times) / C.RUNS
    seq_avg_cpu = sum(seq_cpus) / C.RUNS
    seq_avg_mem = sum(seq_mems) / C.RUNS
    seq_avg_thr = sum(seq_thrs) / C.RUNS
    
    seq_total_throughput = (C.NUM_PATIENTS * C.RUNS) / (seq_total_time_ms / 1000.0) if seq_total_time_ms > 0 else 0

    print("  " + "-" * 84)
    print(f"  {'AVG':<4} | {seq_avg_time:>14.3f} | {seq_avg_wait:>14.6f} | {seq_avg_cpu:>10.2f} | {seq_avg_mem:>11.3f} | {seq_avg_thr:>16.2f}\n")

    # --- 2. PARALLEL ---
    print("Running 10 attempts IN PARALLEL using ProcessPoolExecutor...")
    
    par_waits = [0.0] * C.RUNS
    par_times = [0.0] * C.RUNS
    par_cpus  = [0.0] * C.RUNS
    par_mems  = [0.0] * C.RUNS
    par_thrs  = [0.0] * C.RUNS
    
    print(f"  {'Run':<4} | {'Exec Time (ms)':<14} | {'Avg Wait (min)':<14} | {'CPU (%)':<10} | {'Memory (MB)':<11} | {'Throughput (p/s)':<16}")
    print("  " + "-" * 84)

    par_start = time.perf_counter()
    
    with ProcessPoolExecutor(max_workers=C.RUNS) as executor:
        futures = {executor.submit(run_parallel_worker, 42 + i): i for i in range(C.RUNS)}
        for future in as_completed(futures):
            i = futures[future]
            wait, exec_time, cpu, mem_mb = future.result()
            par_waits[i] = wait
            par_times[i] = exec_time
            par_cpus[i] = cpu
            par_mems[i] = mem_mb
            par_thrs[i] = C.NUM_PATIENTS / (exec_time / 1000.0) if exec_time > 0 else 0
            
    par_end = time.perf_counter()

    for i in range(C.RUNS):
        print(f"  {i + 1:<4} | {par_times[i]:>14.3f} | {par_waits[i]:>14.6f} | {par_cpus[i]:>10.2f} | {par_mems[i]:>11.3f} | {par_thrs[i]:>16.2f}")

    par_total_time_ms = (par_end - par_start) * 1000.0
    par_avg_wait = sum(par_waits) / C.RUNS
    par_avg_time = sum(par_times) / C.RUNS
    par_avg_cpu = sum(par_cpus) / C.RUNS
    par_avg_mem = sum(par_mems) / C.RUNS
    par_avg_thr = sum(par_thrs) / C.RUNS
    
    par_total_throughput = (C.NUM_PATIENTS * C.RUNS) / (par_total_time_ms / 1000.0) if par_total_time_ms > 0 else 0

    print("  " + "-" * 84)
    print(f"  {'AVG':<4} | {par_avg_time:>14.3f} | {par_avg_wait:>14.6f} | {par_avg_cpu:>10.2f} | {par_avg_mem:>11.3f} | {par_avg_thr:>16.2f}\n")

    # --- 3. COMPARISON ---
    speedup = seq_total_time_ms / par_total_time_ms if par_total_time_ms > 0 else 0
    cpu_diff = par_avg_cpu - seq_avg_cpu
    mem_diff = par_avg_mem - seq_avg_mem

    out = []
    div = "=" * 108
    sep = "-" * 108

    out.append(div)
    out.append("  FINAL RESULTS: SEQUENTIAL vs PARALLEL (Totals for 10 Runs)")
    out.append(div)
    out.append(f"  {'Mode':<12} | {'Total Time (ms)':<16} | {'Throughput (p/s)':<16} | {'Avg Wait (min)':<16} | {'Avg CPU %':<10} | {'Avg Mem MB':<10}")
    out.append(sep)
    out.append(f"  {'SEQUENTIAL':<12} | {seq_total_time_ms:>16.3f} | {seq_total_throughput:>16.2f} | {seq_avg_wait:>16.6f} | {seq_avg_cpu:>10.2f} | {seq_avg_mem:>10.3f}")
    out.append(f"  {'PARALLEL':<12} | {par_total_time_ms:>16.3f} | {par_total_throughput:>16.2f} | {par_avg_wait:>16.6f} | {par_avg_cpu:>10.2f} | {par_avg_mem:>10.3f}")
    out.append(sep)
    out.append("  COMPARISON HIGHLIGHTS:")
    out.append(f"  -> Time Speedup : Parallel was {speedup:.2f}x faster (Total Time: {par_total_time_ms:.3f} ms vs {seq_total_time_ms:.3f} ms)")
    
    cpu_word = "higher" if cpu_diff > 0 else "lower"
    out.append(f"  -> Average CPU  : Parallel used {abs(cpu_diff):.2f}% {cpu_word} CPU on average ({par_avg_cpu:.2f}% vs {seq_avg_cpu:.2f}%)")
    
    mem_word = "more" if mem_diff > 0 else "less"
    out.append(f"  -> Average Mem  : Parallel used {abs(mem_diff):.3f} MB {mem_word} memory per run ({par_avg_mem:.3f} MB vs {seq_avg_mem:.3f} MB)")
    out.append(div)

    final_text = "\n".join(out)
    print(final_text)

    with open(C.FINAL_TXT, "w") as f:
        f.write(final_text + "\n")
    print(f"  >>> Saved: {C.FINAL_TXT}")
