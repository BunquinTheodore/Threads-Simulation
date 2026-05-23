import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.management.*;

/**
 * Main.java — Hospital ER M/M/c Queue Simulation
 * Compares Total Time of 10 Sequential Runs vs 10 Parallel Runs, 
 * including per-run metrics.
 */
public class Main {

    public static final int    NUM_PATIENTS = 150000;
    public static final int    NUM_SERVERS  = 4;
    public static final double LAMBDA       = 10.0;
    public static final double MU           = 3.0;
    public static final int    RUNS         = 10;

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(88));
        System.out.println("  JAVA M/M/c HOSPITAL ER QUEUE SIMULATION");
        System.out.printf("  Patients: %d | Servers: %d | lambda=%.1f | mu=%.1f | Runs: %d%n",
                NUM_PATIENTS, NUM_SERVERS, LAMBDA, MU, RUNS);
        System.out.println("=".repeat(88) + "\n");

        Runtime rt = Runtime.getRuntime();

        // --- 1. SEQUENTIAL ---
        System.out.println("Running 10 attempts SEQUENTIALLY using normal sequential implementation...");
        
        rt.gc();
        
        double[] seqWaits = new double[RUNS];
        double[] seqTimes = new double[RUNS];
        double[] seqCpus  = new double[RUNS];
        double[] seqMems  = new double[RUNS];
        double[] seqThrs  = new double[RUNS];
        
        System.out.printf("  %-4s | %-14s | %-14s | %-10s | %-11s | %-16s%n", 
                "Run", "Exec Time (ms)", "Avg Wait (min)", "CPU (%)", "Memory (MB)", "Throughput (p/s)");
        System.out.println("  " + "-".repeat(84));

        long seqStart = System.nanoTime();
        
        for (int i = 0; i < RUNS; i++) {
            rt.gc();
            long memB = rt.totalMemory() - rt.freeMemory();
            long t0 = System.nanoTime();
            
            Random rng = new Random(42 + i);
            seqWaits[i] = SequentialSimulation.runOnce(rng);
            
            long t1 = System.nanoTime();
            long memA = rt.totalMemory() - rt.freeMemory();
            
            seqTimes[i] = (t1 - t0) / 1_000_000.0;
            seqCpus[i] = getCpuLoad();
            seqMems[i] = Math.max(memA - memB, 0) / (1024.0 * 1024.0);
            seqThrs[i] = NUM_PATIENTS / (seqTimes[i] / 1000.0);
            
            System.out.printf("  %-4d | %14.3f | %14.6f | %10.2f | %11.3f | %16.2f%n", 
                    (i + 1), seqTimes[i], seqWaits[i], seqCpus[i], seqMems[i], seqThrs[i]);
        }
        
        long seqEnd = System.nanoTime();
        
        double seqTotalTimeMs = (seqEnd - seqStart) / 1_000_000.0;
        double seqAvgWait = Arrays.stream(seqWaits).average().orElse(0);
        double seqAvgTime = Arrays.stream(seqTimes).average().orElse(0);
        double seqAvgCpu = Arrays.stream(seqCpus).average().orElse(0);
        double seqAvgMem = Arrays.stream(seqMems).average().orElse(0);
        double seqAvgThr = Arrays.stream(seqThrs).average().orElse(0);
        
        double seqTotalThroughput = (NUM_PATIENTS * RUNS) / (seqTotalTimeMs / 1000.0);

        System.out.println("  " + "-".repeat(84));
        System.out.printf("  %-4s | %14.3f | %14.6f | %10.2f | %11.3f | %16.2f%n%n", 
                "AVG", seqAvgTime, seqAvgWait, seqAvgCpu, seqAvgMem, seqAvgThr);

        // --- 2. PARALLEL ---
        System.out.println("Running 10 attempts IN PARALLEL using improved parallel implementation...");
        
        rt.gc();
        
        double[] parWaits = new double[RUNS];
        double[] parTimes = new double[RUNS];
        double[] parCpus  = new double[RUNS];
        double[] parMems  = new double[RUNS];
        double[] parThrs  = new double[RUNS];
        
        System.out.printf("  %-4s | %-14s | %-14s | %-10s | %-11s | %-16s%n", 
                "Run", "Exec Time (ms)", "Avg Wait (min)", "CPU (%)", "Memory (MB)", "Throughput (p/s)");
        System.out.println("  " + "-".repeat(84));

        long parStart = System.nanoTime();
        
        java.util.stream.IntStream.range(0, RUNS).parallel().forEach(i -> {
            try {
                long memB = rt.totalMemory() - rt.freeMemory();
                long t0 = System.nanoTime();
                
                Random rng = new Random(42 + i);
                parWaits[i] = ParallelSimulation.runOnce(rng);
                
                long t1 = System.nanoTime();
                long memA = rt.totalMemory() - rt.freeMemory();
                
                parTimes[i] = (t1 - t0) / 1_000_000.0;
                parCpus[i] = getCpuLoad();
                parMems[i] = Math.max(memA - memB, 0) / (1024.0 * 1024.0);
                parThrs[i] = NUM_PATIENTS / (parTimes[i] / 1000.0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        long parEnd = System.nanoTime();

        // Print parallel results sequentially after they all finish to keep order neat
        for (int i = 0; i < RUNS; i++) {
            System.out.printf("  %-4d | %14.3f | %14.6f | %10.2f | %11.3f | %16.2f%n", 
                    (i + 1), parTimes[i], parWaits[i], parCpus[i], parMems[i], parThrs[i]);
        }

        double parTotalTimeMs = (parEnd - parStart) / 1_000_000.0;
        double parAvgWait = Arrays.stream(parWaits).average().orElse(0);
        double parAvgTime = Arrays.stream(parTimes).average().orElse(0);
        double parAvgCpu = Arrays.stream(parCpus).average().orElse(0);
        double parAvgMem = Arrays.stream(parMems).average().orElse(0);
        double parAvgThr = Arrays.stream(parThrs).average().orElse(0);
        
        double parTotalThroughput = (NUM_PATIENTS * RUNS) / (parTotalTimeMs / 1000.0);

        System.out.println("  " + "-".repeat(84));
        System.out.printf("  %-4s | %14.3f | %14.6f | %10.2f | %11.3f | %16.2f%n%n", 
                "AVG", parAvgTime, parAvgWait, parAvgCpu, parAvgMem, parAvgThr);

        // --- 3. COMPARISON ---
        double speedup = seqTotalTimeMs / parTotalTimeMs;
        double cpuDiff = parAvgCpu - seqAvgCpu;
        double memDiff = parAvgMem - seqAvgMem;

        StringBuilder sb = new StringBuilder();
        String div = "=".repeat(108);
        String sep = "-".repeat(108);

        sb.append(div).append("\n");
        sb.append("  FINAL RESULTS: SEQUENTIAL vs PARALLEL (Totals for 10 Runs)\n");
        sb.append(div).append("\n");
        sb.append(String.format("  %-12s | %-16s | %-16s | %-16s | %-10s | %-10s%n",
                "Mode", "Total Time (ms)", "Throughput (p/s)", "Avg Wait (min)", "Avg CPU %", "Avg Mem MB"));
        sb.append(sep).append("\n");
        sb.append(String.format("  %-12s | %16.3f | %16.2f | %16.6f | %10.2f | %10.3f%n",
                "SEQUENTIAL", seqTotalTimeMs, seqTotalThroughput, seqAvgWait, seqAvgCpu, seqAvgMem));
        sb.append(String.format("  %-12s | %16.3f | %16.2f | %16.6f | %10.2f | %10.3f%n",
                "PARALLEL", parTotalTimeMs, parTotalThroughput, parAvgWait, parAvgCpu, parAvgMem));
        sb.append(sep).append("\n");
        
        sb.append("  COMPARISON HIGHLIGHTS:\n");
        sb.append(String.format("  -> Time Speedup : Parallel was %.2fx faster (Total Time: %.3f ms vs %.3f ms)%n", 
                speedup, parTotalTimeMs, seqTotalTimeMs));
                
        String cpuWord = cpuDiff > 0 ? "higher" : "lower";
        sb.append(String.format("  -> Average CPU  : Parallel used %.2f%% %s CPU on average (%.2f%% vs %.2f%%)%n", 
                Math.abs(cpuDiff), cpuWord, parAvgCpu, seqAvgCpu));
                
        String memWord = memDiff > 0 ? "more" : "less";
        sb.append(String.format("  -> Average Mem  : Parallel used %.3f MB %s memory per run (%.3f MB vs %.3f MB)%n", 
                Math.abs(memDiff), memWord, parAvgMem, seqAvgMem));
                
        sb.append(div).append("\n");

        String output = sb.toString();
        System.out.println(output);

        try (PrintWriter pw = new PrintWriter(new FileWriter("java_final_results.txt"))) {
            pw.print(output);
        }
        System.out.println("  >>> Saved: java_final_results.txt");
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
}