package com.scalarwide.bench;

import com.scalarwide.Wide128;
import com.scalarwide.Wide256;
import com.scalarwide.mc.McWarmup;

import java.util.Random;

/**
 * Cold-start probe: measures the very first calls (no warmup) versus steady
 * state, to bound "JIT not engaged yet" cost. One op per JVM (fork per op) to
 * avoid class-loading/JIT bootstrap contamination between probes.
 *
 * <p>Args: 128mul | 256mul（可选第二参 "warm" 先跑 McWarmup）。
 * Run with different JIT modes: default, {@code -XX:TieredStopAtLevel=1},
 * {@code -Xint}. Operands rotate through a fixed table so the JIT cannot fold.
 * BigInteger 对照已移除（比较体系 v2：BigInteger 仅正确性对拍）。
 */
public final class ColdStartProbe {

    private static final int N = 64;
    private static final int COLD = 2_000;
    private static final int WARMUP = 2_000_000;
    private static final int STEADY = 2_000_000;

    private static final Wide128[] A128 = new Wide128[N];
    private static final Wide128[] B128 = new Wide128[N];
    private static final Wide256[] A256 = new Wide256[N];
    private static final Wide256[] B256 = new Wide256[N];

    private static Wide128 sink128;
    private static Wide256 sink256;

    private static int i;

    public static void main(String[] args) {
        Random rnd = new Random(424242L);
        for (int k = 0; k < N; k++) {
            A128[k] = Wide128.of(rnd.nextLong(), rnd.nextLong());
            B128[k] = Wide128.of(rnd.nextLong(), rnd.nextLong());
            A256[k] = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            B256[k] = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        }
        String op = args.length > 0 ? args[0] : "128mul";
        if (args.length > 1 && args[1].equals("warm")) {
            McWarmup.run();
        }
        switch (op) {
            case "128mul" -> probe("128.mul", () -> sink128 = A128[idx()].mul(B128[idx()]));
            case "256mul" -> probe("256.mul", () -> sink256 = A256[idx()].mul(B256[idx()]));
            default -> throw new IllegalArgumentException(op);
        }
    }

    private static int idx() {
        return (i++) & (N - 1);
    }

    interface Op {
        void run();
    }

    private static void probe(String name, Op op) {
        long t0 = System.nanoTime();
        for (int k = 0; k < 100; k++) {
            op.run();
        }
        long first100 = System.nanoTime() - t0;
        long t1 = System.nanoTime();
        for (int k = 100; k < COLD; k++) {
            op.run();
        }
        long cold2k = System.nanoTime() - t1;
        for (int k = 0; k < WARMUP; k++) {
            op.run();
        }
        long t2 = System.nanoTime();
        for (int k = 0; k < STEADY; k++) {
            op.run();
        }
        long warm = System.nanoTime() - t2;
        System.out.printf("RESULT %-14s first100=%9.1f  cold2k=%9.1f  steady=%8.2f ns/op  cold/steady=%6.1fx%n",
                name,
                first100 / 100.0,
                cold2k / (COLD - 100.0),
                warm / (double) STEADY,
                (cold2k / (COLD - 100.0)) / (warm / (double) STEADY));
    }

    private ColdStartProbe() {
    }
}
