import java.util.Random;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class ParallelSimulation {

    // ---- Single parallel run ----
    public static double runOnce(Random rng) throws Exception {
        // 1. Generate all patients using primitive arrays to avoid object overhead
        double[] arrivalTimes = new double[Main.NUM_PATIENTS];
        double[] serviceTimes = new double[Main.NUM_PATIENTS];
        double t = 0;
        for (int i = 0; i < Main.NUM_PATIENTS; i++) {
            t += -Math.log(1 - rng.nextDouble()) / Main.LAMBDA;
            arrivalTimes[i] = t;
            serviceTimes[i] = -Math.log(1 - rng.nextDouble()) / Main.MU;
        }

        // 2. Dispatch: assign each patient to the server that becomes free soonest
        int[] assignments = new int[Main.NUM_PATIENTS];
        double[] serverFreeAt = new double[Main.NUM_SERVERS];

        for (int i = 0; i < Main.NUM_PATIENTS; i++) {
            int best = 0;
            for (int s = 1; s < Main.NUM_SERVERS; s++) {
                if (serverFreeAt[s] < serverFreeAt[best]) best = s;
            }
            double start = Math.max(arrivalTimes[i], serverFreeAt[best]);
            serverFreeAt[best] = start + serviceTimes[i];
            assignments[i] = best;
        }

        // 3. Process server queues in parallel using common ForkJoinPool
        double totalWait = java.util.stream.IntStream.range(0, Main.NUM_SERVERS).parallel().mapToDouble(s -> {
            double serverClock = 0.0;
            double wait = 0.0;
            for (int i = 0; i < Main.NUM_PATIENTS; i++) {
                if (assignments[i] == s) {
                    double start = Math.max(arrivalTimes[i], serverClock);
                    wait += start - arrivalTimes[i];
                    serverClock = start + serviceTimes[i];
                }
            }
            return wait;
        }).sum();

        return totalWait / Main.NUM_PATIENTS;   // avg wait in sim-minutes
    }

    // ---- CPU load helper (kept public for Main to reuse as needed) ----
    public static double getCpuLoad() {
        try {
            OperatingSystemMXBean b = ManagementFactory.getOperatingSystemMXBean();
            if (b instanceof com.sun.management.OperatingSystemMXBean) {
                double v = ((com.sun.management.OperatingSystemMXBean) b).getProcessCpuLoad() * 100.0;
                return v >= 0 ? v : 0.0;
            }
        } catch (Exception ignored) {}
        return 0.0;
    }
}
