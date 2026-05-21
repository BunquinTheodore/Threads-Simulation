## Scenario

**Hospital Emergency Room Queue (M/M/c model)**
- Patients arrive randomly using a Poisson process (exponential inter-arrival times)
- Multiple parallel servers (doctors) handle patients concurrently using real threads/processes
- FIFO queue discipline

---

## Languages & Frameworks

| Group | Language | Parallel Framework |
|-------|----------|--------------------|
| Dev A | Python | `multiprocessing` + `concurrent.futures.ProcessPoolExecutor` |
| Dev B | Java | `java.util.concurrent` — `ExecutorService` + `LinkedBlockingQueue` |

---

## Simulation Parameters (use IDENTICAL values in both languages)

```
Patients (entities):     500
Servers (c):               4
Arrival rate (lambda):    10 patients/min
Service rate (mu):         3 patients/min per server
Runs/iterations:          10  (average the results)
```

---

## Performance Metrics to Measure

1. **Total Execution Time** — wall-clock time in seconds/milliseconds
2. **CPU Utilization** — % CPU used during the simulation
3. **Throughput** — patients processed per second
4. **Average Wait Time** — mean time a patient spends waiting in queue
5. **Speedup Factor** — parallel time ÷ sequential baseline time (same language)
6. **Memory Usage** — peak RAM in MB

---

## Output Format Required

Each group must produce a CSV + printed table like this:

```
Run | Exec Time (s) | CPU (%) | Throughput (p/s) | Avg Wait (s) | Speedup | Memory (MB)
  1 |          X.XX |    XX.X |             XX.X |         X.XX |    X.XX |        XX.X
...
AVG |          X.XX |    XX.X |             XX.X |         X.XX |    X.XX |        XX.X
```

---

## Environment Setup

### Dev Team A — Python
```bash
pip install psutil
# Everything else is stdlib: multiprocessing, concurrent.futures, tracemalloc, time, csv, queue
python --version  # Must be 3.8+
```

### Dev Team B — Java
```bash
java -version   # Must be Java 17+
# No external deps — uses java.util.concurrent and java.lang.management only
```

---

## Dev Team A — Python Prompts

### PROMPT 1 — Sequential Baseline
```
Write a Python script that simulates a hospital emergency room queue using a 
sequential (single-threaded) M/M/c queueing model.

Parameters:
- 500 patients total
- 4 servers (doctors)
- Arrival rate lambda = 10 patients/min
- Service rate mu = 3 patients/min per server
- Inter-arrival times: random.expovariate(lambda)
- Service times: random.expovariate(mu)
- Queue discipline: FIFO using queue.Queue

Measure and print:
1. Total wall-clock execution time using time.perf_counter()
2. Average patient wait time in queue
3. Throughput (patients per second)
4. Peak memory usage in MB using tracemalloc

Run 10 times in a loop, print individual + average results.
Save to 'python_sequential_results.csv'.
```

### PROMPT 2 — Parallel Implementation
```
Write a Python script that simulates the same hospital ER queue using PARALLEL 
COMPUTING via Python's multiprocessing and concurrent.futures modules.

Same parameters as sequential:
- 500 patients, 4 servers, lambda=10, mu=3
- Inter-arrival and service times use exponential distribution
- Queue discipline: FIFO
- Use multiprocessing.Queue as the shared queue between processes
- Use concurrent.futures.ProcessPoolExecutor to manage the 4 server processes

Each server process should:
1. Pull a patient from the shared queue
2. Simulate service time with time.sleep(service_time_scaled)
3. Record when the patient was served

The main process should:
1. Generate all 500 patients with arrival timestamps
2. Feed them into the shared queue
3. Launch and manage 4 server processes via ProcessPoolExecutor
4. Collect all results after completion

Measure and print:
1. Total wall-clock execution time (time.perf_counter())
2. Average patient wait time (time_served - time_arrived)
3. Throughput (patients per second)
4. CPU utilization using psutil.cpu_percent(interval=1)
5. Peak memory in MB using tracemalloc
6. Speedup = sequential_avg_time / this_parallel_time
   (hardcode the sequential average from the previous script)

Run 10 times, print individual + average.
Save to 'python_parallel_results.csv'.
```

### PROMPT 3 — Results Formatter
```
Write a Python script that reads 'python_sequential_results.csv' and 
'python_parallel_results.csv', then prints a clean formatted comparison table 
to console AND saves it to 'python_final_results.txt'.

Columns:
Run | Seq Time (s) | Par Time (s) | Speedup | CPU% | Avg Wait (s) | Memory (MB) | Throughput (p/s)

Also compute and display:
- Average, Min, and Max rows
- One-line summary: "Python parallel was X.XX times faster than sequential on average."
```

---

## Dev Team B — Java Prompts

### PROMPT 1 — Sequential Baseline
```
Write a Java program that simulates a hospital emergency room queue using a 
sequential (single-threaded) M/M/c queueing model.

Parameters:
- 500 patients total
- 4 servers (doctors)
- Arrival rate lambda = 10 patients/min
- Service rate mu = 3 patients/min per server
- Inter-arrival times: -Math.log(1 - Math.random()) / lambda
- Service times: -Math.log(1 - Math.random()) / mu
- Queue discipline: FIFO using java.util.LinkedList
- Process patients one at a time, single thread

Measure and print:
1. Total wall-clock time using System.nanoTime(), convert to milliseconds
2. Average patient wait time
3. Throughput (patients per second)
4. Peak memory in MB using Runtime.getRuntime().totalMemory() - freeMemory()

Run 10 times, print individual + average.
Save to 'java_sequential_results.csv'.
Use only the Java standard library.
```

### PROMPT 2 — Parallel Implementation
```
Write a Java program that simulates the same hospital ER queue using PARALLEL 
COMPUTING via java.util.concurrent.

Same parameters: 500 patients, 4 servers, lambda=10, mu=3

Implementation:
- Create a Patient class with fields: id, arrivalTime, serviceTime, 
  startServiceTime, endServiceTime
- Use LinkedBlockingQueue<Patient> as the shared thread-safe queue
- Use Executors.newFixedThreadPool(4) for the 4 server threads
- One producer thread generates all 500 patients and adds to the queue
- 4 consumer threads call queue.take(), simulate service with 
  Thread.sleep(scaledServiceTime), record timestamps
- Use CountDownLatch or ExecutorService.shutdown() + awaitTermination() 
  to wait for completion
- Use AtomicLong or synchronized blocks for shared counters

Measure and print:
1. Total wall-clock time using System.nanoTime()
2. Average patient wait time (startServiceTime - arrivalTime)
3. Throughput (patients per second)
4. CPU utilization via ManagementFactory.getOperatingSystemMXBean(), 
   cast to com.sun.management.OperatingSystemMXBean, call getProcessCpuLoad()
5. Peak memory: Runtime.getRuntime().totalMemory() - freeMemory()
6. Speedup = sequential_avg_time / this_parallel_time
   (hardcode the sequential average from the previous program)

Run 10 times, print individual + average.
Save to 'java_parallel_results.csv'.
Use only java.util.concurrent and java.lang.management.
```

### PROMPT 3 — Results Formatter
```
Write a Java program that reads 'java_sequential_results.csv' and 
'java_parallel_results.csv', then prints a clean formatted comparison table 
to console AND saves it to 'java_final_results.txt'.

Columns:
Run | Seq Time (ms) | Par Time (ms) | Speedup | CPU% | Avg Wait (ms) | Memory (MB) | Throughput (p/s)

Also compute:
- Average, Min, Max rows
- One-line summary: "Java parallel was X.XX times faster than sequential on average."
```

---

## Important Notes

- Run both simulations on the **same machine** for a fair comparison.
- Source code is NOT submitted — only the results tables.
- No UI needed — tabulate results only.
- Record the machine specs (OS, CPU cores, RAM) when running — needed for context.
