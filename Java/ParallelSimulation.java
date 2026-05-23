import java.util.Random;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class ParallelSimulation {

    // ---- Single run (no internal parallelization, only parallelized across runs in Main) ----
    public static double runOnce(Random rng) throws Exception {
        double[] serverFreeAt = new double[Main.NUM_SERVERS];
        double currentTime    = 0.0;
        double totalWait      = 0.0;

        for (int i = 0; i < Main.NUM_PATIENTS; i++) {
            currentTime += -Math.log(1 - rng.nextDouble()) / Main.LAMBDA;

            int    bestServer  = 0;
            double bestFreeAt  = serverFreeAt[0];
            for (int s = 1; s < Main.NUM_SERVERS; s++) {
                if (serverFreeAt[s] < bestFreeAt) {
                    bestFreeAt = serverFreeAt[s];
                    bestServer = s;
                }
            }

            double waitTime = Math.max(0.0, serverFreeAt[bestServer] - currentTime);
            totalWait += waitTime;

            double serviceStart = Math.max(currentTime, serverFreeAt[bestServer]);
            double serviceTime  = -Math.log(1 - rng.nextDouble()) / Main.MU;
            serverFreeAt[bestServer] = serviceStart + serviceTime;
        }

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
