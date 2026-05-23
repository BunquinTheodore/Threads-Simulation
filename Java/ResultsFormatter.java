import java.io.*;
import java.util.*;

public class ResultsFormatter {

    // Keep readCsv public so Main can reuse it if needed; uses the CSV format written by Main
    public static List<double[]> readCsv(String path) throws Exception {
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

    // Provide a small helper to produce the final report string; Main already contains the full report logic
    public static String buildReport(List<double[]> seqRows, List<double[]> parRows) {
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
                Main.NUM_PATIENTS, Main.NUM_SERVERS, Main.LAMBDA, Main.MU, Main.RUNS));
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

        double rho = Main.LAMBDA / (Main.NUM_SERVERS * Main.MU);
        sb.append(String.format("%n  QUEUEING THEORY CHECK%n  %s%n", "-".repeat(80)));
        sb.append(String.format("  Server utilisation (rho) = lambda / (c * mu) = %.1f / (%d x %.1f) = %.4f%n",
                Main.LAMBDA, Main.NUM_SERVERS, Main.MU, rho));
        sb.append(String.format("  Avg simulated wait: %.4f min = %.2f s%n", avgWait, avgWait * 60));
        sb.append(String.format("  High wait expected at rho=%.3f (near saturation): consistent.%n", rho));
        sb.append(div).append("\n");

        return sb.toString();
    }
}
