import java.io.*;
import java.util.*;

public class SequentialSimulation {

    /** Exponential variate: -ln(U) / rate  (rate in same time unit) */
    public static double expRand(Random rng, double rate) {
        return -Math.log(1 - rng.nextDouble()) / rate;
    }

    // Keep a helper runOnce that uses Main's shared constants. Returns avg wait (in simulation minutes).
    public static double runOnce(Random rng) {
        double[] serverFreeAt = new double[Main.NUM_SERVERS];
        double currentTime    = 0.0;
        double totalWait      = 0.0;

        for (int i = 0; i < Main.NUM_PATIENTS; i++) {
            currentTime += expRand(rng, Main.LAMBDA);

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
            double serviceTime  = expRand(rng, Main.MU);
            serverFreeAt[bestServer] = serviceStart + serviceTime;
        }

        return totalWait / Main.NUM_PATIENTS;   // avg wait in simulation minutes
    }
}
