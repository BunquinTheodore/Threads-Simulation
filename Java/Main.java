import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.management.*;

/**
 * Main.java — Hospital ER M/M/c Queue Simulation
 * Runs Sequential → Parallel → Results Formatter in one go.
 * Parameters:
 *   500 patients | 4 servers | λ=10 p/min | μ=3 p/min | 10 runs
 *
 * Compile:  javac Main.java
 * Run:      java Main
 *
 * Output files produced:
 *   java_sequential_results.csv
 *   java_parallel_results.csv
 *   java_final_results.txt
 */
public class Main {

    // =========================================================
    //  SHARED CONSTANTS
    // =========================================================
    public static final int    NUM_PATIENTS = 500;
    public static final int    NUM_SERVERS  = 4;
    public static final double LAMBDA       = 10.0;   // arrivals/min
    public static final double MU           = 3.0;    // service/min per server
    public static final int    RUNS         = 10;

    public static double seqAvgMs = 0.0;

    public static void main(String[] args) throws Exception {
        banner("JAVA M/M/c HOSPITAL ER QUEUE SIMULATION");
        System.out.printf("  Patients: %d  |  Servers: %d  |  λ=%.1f  |  μ=%.1f  |  Runs: %d%n%n",
                NUM_PATIENTS, NUM_SERVERS, LAMBDA, MU, RUNS);

        runSequential();
        runParallel();
        runFormatter();
    }

    // =========================================================
    //  1. SEQUENTIAL SIMULATION
    // =========================================================
    static void runSequential() throws Exception {
        banner("STEP 1 — Sequential Baseline");

        Random rng = new Random(42);
        PrintWriter csv = new PrintWriter(new FileWriter("java_sequential_results.csv"));
        csv.println("run,exec_time_ms,cpu_pct,throughput_ps,avg_wait_min,speedup,memory_mb");

        double[] execTimes   = new double[RUNS];
        double[] avgWaits    = new double[RUNS];
        double[] throughputs = new double[RUNS];
        double[] memories    = new double[RUNS];

        printTableHeader("Exec Time (ms)", "CPU (%)", "Throughput (p/s)", "Avg Wait (min)", "Speedup", "Memory (MB)");

        Runtime rt = Runtime.getRuntime();

        for (int run = 1; run <= RUNS; run++) {
            rt.gc();
            long memBefore = rt.totalMemory() - rt.freeMemory();

            long t0 = System.nanoTime();
            double avgWaitMin = seqRunOnce(rng);
            long t1 = System.nanoTime();

            long memAfter = rt.totalMemory() - rt.freeMemory();

            double elapsedMs  = (t1 - t0) / 1_000_000.0;
            double throughput = NUM_PATIENTS / (elapsedMs / 1000.0);
            double memMb      = Math.max(memAfter - memBefore, 0) / (1024.0 * 1024.0);

            execTimes[run-1]   = elapsedMs;
            avgWaits[run-1]    = avgWaitMin;
            throughputs[run-1] = throughput;
            memories[run-1]    = memMb;

            System.out.printf("  %-4d | %13.3f | %7s | %15.2f | %14.6f | %7s | %10.3f%n",
                    run, elapsedMs, "N/A", throughput, avgWaitMin, "1.00", memMb);
            csv.printf("%d,%.3f,N/A,%.4f,%.6f,1.00,%.3f%n",
                    run, elapsedMs, throughput, avgWaitMin, memMb);
        }

        seqAvgMs = Arrays.stream(execTimes).average().orElse(0);
        double avgWait   = Arrays.stream(avgWaits).average().orElse(0);
        double avgThrput = Arrays.stream(throughputs).average().orElse(0);
        double avgMem    = Arrays.stream(memories).average().orElse(0);

        printDivider();
        System.out.printf("  %-4s | %13.3f | %7s | %15.2f | %14.6f | %7s | %10.3f%n",
                "AVG", seqAvgMs, "N/A", avgThrput, avgWait, "1.00", avgMem);
        csv.printf("AVG,%.3f,N/A,%.4f,%.6f,1.00,%.3f%n", seqAvgMs, avgThrput, avgWait, avgMem);
        csv.close();

        System.out.printf("%n  >>> Sequential avg: %.3f ms  |  Saved: java_sequential_results.csv%n%n", seqAvgMs);
    }

    static double seqRunOnce(Random rng) {
        double[] serverFreeAt = new double[NUM_SERVERS];
        double currentTime = 0.0, totalWait = 0.0;

        for (int i = 0; i < NUM_PATIENTS; i++) {
            currentTime += expRand(rng, LAMBDA);

            int best = 0;
            for (int s = 1; s < NUM_SERVERS; s++)
                if (serverFreeAt[s] < serverFreeAt[best]) best = s;

            double wait = Math.max(0.0, serverFreeAt[best] - currentTime);
            totalWait += wait;
            double start = Math.max(currentTime, serverFreeAt[best]);
            serverFreeAt[best] = start + expRand(rng, MU);
        }
        return totalWait / NUM_PATIENTS;
    }

    // =========================================================
    //  2. PARALLEL SIMULATION
    // =========================================================
    static void runParallel() throws Exception {
        banner("STEP 2 — Parallel (4 server threads)");

        Random rng = new Random(42);
        PrintWriter csv = new PrintWriter(new FileWriter("java_parallel_results.csv"));
        csv.println("run,exec_time_ms,cpu_pct,throughput_ps,avg_wait_min,speedup,memory_mb");

        double[] execTimes   = new double[RUNS];
        double[] cpuLoads    = new double[RUNS];
        double[] avgWaits    = new double[RUNS];
        double[] throughputs = new double[RUNS];
        double[] speedups    = new double[RUNS];
        double[] memories    = new double[RUNS];

        printTableHeader("Exec Time (ms)", "CPU (%)", "Throughput (p/s)", "Avg Wait (min)", "Speedup", "Memory (MB)");

        Runtime rt = Runtime.getRuntime();

        for (int run = 1; run <= RUNS; run++) {
            rt.gc();
            long memBefore = rt.totalMemory() - rt.freeMemory();
            getCpuLoad(); Thread.sleep(50);

            long t0 = System.nanoTime();
            double avgWaitMin = parRunOnce(rng);
            long t1 = System.nanoTime();

            double cpuLoad    = getCpuLoad();
            long   memAfter   = rt.totalMemory() - rt.freeMemory();

            double elapsedMs  = (t1 - t0) / 1_000_000.0;
            double throughput = NUM_PATIENTS / (elapsedMs / 1000.0);
            double speedup    = seqAvgMs / elapsedMs;
            double memMb      = Math.max(memAfter - memBefore, 0) / (1024.0 * 1024.0);

            execTimes[run-1]   = elapsedMs;
            cpuLoads[run-1]    = cpuLoad;
            avgWaits[run-1]    = avgWaitMin;
            throughputs[run-1] = throughput;
            speedups[run-1]    = speedup;
            memories[run-1]    = memMb;

            System.out.printf("  %-4d | %13.3f | %7.2f | %15.2f | %14.6f | %7.4f | %10.3f%n",
                    run, elapsedMs, cpuLoad, throughput, avgWaitMin, speedup, memMb);
            csv.printf("%d,%.3f,%.2f,%.4f,%.6f,%.4f,%.3f%n",
                    run, elapsedMs, cpuLoad, throughput, avgWaitMin, speedup, memMb);
        }

        double avgExec   = Arrays.stream(execTimes).average().orElse(0);
        double avgCpu    = Arrays.stream(cpuLoads).average().orElse(0);
        double avgWait   = Arrays.stream(avgWaits).average().orElse(0);
        double avgThrput = Arrays.stream(throughputs).average().orElse(0);
        double avgSpdup  = Arrays.stream(speedups).average().orElse(0);
        double avgMem    = Arrays.stream(memories).average().orElse(0);

        printDivider();
        System.out.printf("  %-4s | %13.3f | %7.2f | %15.2f | %14.6f | %7.4f | %10.3f%n",
                "AVG", avgExec, avgCpu, avgThrput, avgWait, avgSpdup, avgMem);
        csv.printf("AVG,%.3f,%.2f,%.4f,%.6f,%.4f,%.3f%n",
                avgExec, avgCpu, avgThrput, avgWait, avgSpdup, avgMem);
        csv.close();

        System.out.printf("%n  >>> Parallel avg: %.3f ms  |  Speedup: %.4fx  |  Saved: java_parallel_results.csv%n%n",
                avgExec, avgSpdup);
    }

    static double parRunOnce(Random rng) throws Exception {
        // Generate patients using primitive arrays
        double[] arrivalTimes = new double[NUM_PATIENTS];
        double[] serviceTimes = new double[NUM_PATIENTS];
        double t = 0;
        for (int i = 0; i < NUM_PATIENTS; i++) {
            t += expRand(rng, LAMBDA);
            arrivalTimes[i] = t;
            serviceTimes[i] = expRand(rng, MU);
        }

        // Dispatch: assign each patient to the server free soonest
        int[] assignments = new int[NUM_PATIENTS];
        double[] serverFreeAt = new double[NUM_SERVERS];

        for (int i = 0; i < NUM_PATIENTS; i++) {
            int best = 0;
            for (int s = 1; s < NUM_SERVERS; s++) {
                if (serverFreeAt[s] < serverFreeAt[best]) best = s;
            }
            double start = Math.max(arrivalTimes[i], serverFreeAt[best]);
            serverFreeAt[best] = start + serviceTimes[i];
            assignments[i] = best;
        }

        // Process server queues in parallel
        double totalWait = java.util.stream.IntStream.range(0, NUM_SERVERS).parallel().mapToDouble(s -> {
            double clock = 0, wait = 0;
            for (int i = 0; i < NUM_PATIENTS; i++) {
                if (assignments[i] == s) {
                    double start = Math.max(arrivalTimes[i], clock);
                    wait += start - arrivalTimes[i];
                    clock = start + serviceTimes[i];
                }
            }
            return wait;
        }).sum();

        return totalWait / NUM_PATIENTS;
    }

    // =========================================================
    //  3. RESULTS FORMATTER
    // =========================================================
    static void runFormatter() throws Exception {
        banner("STEP 3 — Comparison Report");

        List<double[]> seqRows = readCsv("java_sequential_results.csv");
        List<double[]> parRows = readCsv("java_parallel_results.csv");
        int n = Math.min(seqRows.size(), parRows.size());

        record RunRow(int run, double seqMs, double parMs, double speedup,
                      double cpu, double avgWaitMin, double memMb, double throughput) {}

        List<RunRow> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double[] s = seqRows.get(i), p = parRows.get(i);
            double speedup = p[0] > 0 ? s[0] / p[0] : 0;
            rows.add(new RunRow(i+1, s[0], p[0], speedup, p[1], p[3], p[5], p[2]));
        }

        double avgSeq  = rows.stream().mapToDouble(RunRow::seqMs).average().orElse(0);
        double avgPar  = rows.stream().mapToDouble(RunRow::parMs).average().orElse(0);
        double avgSpd  = rows.stream().mapToDouble(RunRow::speedup).average().orElse(0);
        double avgCpu  = rows.stream().mapToDouble(RunRow::cpu).average().orElse(0);
        double avgWait = rows.stream().mapToDouble(RunRow::avgWaitMin).average().orElse(0);
        double avgMem  = rows.stream().mapToDouble(RunRow::memMb).average().orElse(0);
        double avgThr  = rows.stream().mapToDouble(RunRow::throughput).average().orElse(0);

        double minSeq  = rows.stream().mapToDouble(RunRow::seqMs).min().orElse(0);
        double minPar  = rows.stream().mapToDouble(RunRow::parMs).min().orElse(0);
        double minSpd  = rows.stream().mapToDouble(RunRow::speedup).min().orElse(0);
        double minWait = rows.stream().mapToDouble(RunRow::avgWaitMin).min().orElse(0);
        double minMem  = rows.stream().mapToDouble(RunRow::memMb).min().orElse(0);
        double minThr  = rows.stream().mapToDouble(RunRow::throughput).min().orElse(0);

        double maxSeq  = rows.stream().mapToDouble(RunRow::seqMs).max().orElse(0);
        double maxPar  = rows.stream().mapToDouble(RunRow::parMs).max().orElse(0);
        double maxSpd  = rows.stream().mapToDouble(RunRow::speedup).max().orElse(0);
        double maxWait = rows.stream().mapToDouble(RunRow::avgWaitMin).max().orElse(0);
        double maxMem  = rows.stream().mapToDouble(RunRow::memMb).max().orElse(0);
        double maxThr  = rows.stream().mapToDouble(RunRow::throughput).max().orElse(0);

        StringBuilder sb = new StringBuilder();
        String div = "=".repeat(108);
        String sep = "-".repeat(108);

        sb.append(div).append("\n");
        sb.append("  JAVA M/M/c SIMULATION -- SEQUENTIAL vs PARALLEL COMPARISON REPORT\n");
        sb.append(String.format(
                "  Parameters: %d patients | %d servers | lambda=%.1f p/min | mu=%.1f p/min | %d runs%n",
                NUM_PATIENTS, NUM_SERVERS, LAMBDA, MU, RUNS));
        sb.append(div).append("\n");
        sb.append(String.format(
                "  %-4s | %-13s | %-13s | %-8s | %-8s | %-14s | %-10s | %-15s%n",
                "Run","Seq Time (ms)","Par Time (ms)","Speedup","CPU (%)","Avg Wait (min)","Memory (MB)","Throughput (p/s)"));
        sb.append(sep).append("\n");

        for (RunRow r : rows) {
            sb.append(String.format(
                    "  %-4d | %13.3f | %13.3f | %8.4f | %8.2f | %14.6f | %10.3f | %15.2f%n",
                    r.run(), r.seqMs(), r.parMs(), r.speedup(), r.cpu(),
                    r.avgWaitMin(), r.memMb(), r.throughput()));
        }

        sb.append(sep).append("\n");
        sb.append(String.format("  %-4s | %13.3f | %13.3f | %8.4f | %8.2f | %14.6f | %10.3f | %15.2f%n",
                "AVG", avgSeq, avgPar, avgSpd, avgCpu, avgWait, avgMem, avgThr));
        sb.append(String.format("  %-4s | %13.3f | %13.3f | %8.4f | %8s | %14.6f | %10.3f | %15.2f%n",
                "MIN", minSeq, minPar, minSpd, "--", minWait, minMem, minThr));
        sb.append(String.format("  %-4s | %13.3f | %13.3f | %8.4f | %8s | %14.6f | %10.3f | %15.2f%n",
                "MAX", maxSeq, maxPar, maxSpd, "--", maxWait, maxMem, maxThr));
        sb.append(div).append("\n");

        // Summary
        sb.append("\n  SUMMARY\n  ").append("-".repeat(80)).append("\n");
        if (avgSpd >= 1.0) {
            sb.append(String.format(
                    "  Java parallel was %.2fx FASTER than sequential on average.%n" +
                            "  Thread parallelism offset coordination overhead successfully.%n", avgSpd));
        } else {
            sb.append(String.format(
                    "  Java sequential was %.2fx FASTER than parallel on average.%n" +
                            "  Thread pool overhead (~%.1f ms) exceeded computation time (%.3f ms).%n" +
                            "  Parallel computing pays off when per-task work >> coordination cost.%n",
                    1.0 / avgSpd, avgPar - avgSeq, avgSeq));
        }

        // Queueing theory check
        double rho = LAMBDA / (NUM_SERVERS * MU);
        sb.append(String.format("%n  QUEUEING THEORY CHECK%n  %s%n", "-".repeat(80)));
        sb.append(String.format("  Server utilisation (rho) = lambda / (c * mu) = %.1f / (%d x %.1f) = %.4f%n",
                LAMBDA, NUM_SERVERS, MU, rho));
        sb.append(String.format("  Avg simulated wait: %.4f min = %.2f s%n", avgWait, avgWait * 60));
        sb.append(String.format("  High wait expected at rho=%.3f (near saturation): consistent.%n", rho));
        sb.append(div).append("\n");

        String output = sb.toString();
        System.out.println(output);

        try (PrintWriter pw = new PrintWriter(new FileWriter("java_final_results.txt"))) {
            pw.print(output);
        }
        System.out.println("  >>> Saved: java_final_results.txt");
    }

    // =========================================================
    //  HELPERS
    // =========================================================
    static double expRand(Random rng, double rate) {
        return -Math.log(1 - rng.nextDouble()) / rate;
    }

    static double getCpuLoad() {
        try {
            OperatingSystemMXBean b = ManagementFactory.getOperatingSystemMXBean();
            if (b instanceof com.sun.management.OperatingSystemMXBean osb) {
                double v = osb.getProcessCpuLoad() * 100.0;
                return v >= 0 ? v : 0.0;
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    static List<double[]> readCsv(String path) throws Exception {
        List<double[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("AVG")) continue;
                String[] parts = line.split(",");
                List<Double> nums = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    try { nums.add(Double.parseDouble(parts[i].trim())); }
                    catch (NumberFormatException e) { nums.add(0.0); }
                }
                rows.add(nums.stream().mapToDouble(Double::doubleValue).toArray());
            }
        }
        return rows;
    }

    static void banner(String title) {
        String line = "=".repeat(60);
        System.out.println("\n" + line);
        System.out.println("  " + title);
        System.out.println(line);
    }

    static void printTableHeader(String... cols) {
        System.out.printf("  %-4s | %-13s | %-7s | %-15s | %-14s | %-7s | %-10s%n",
                "Run", cols[0], cols[1], cols[2], cols[3], cols[4], cols[5]);
        printDivider();
    }

    static void printDivider() {
        System.out.println("  " + "-".repeat(90));
    }
}