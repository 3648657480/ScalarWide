package com.scalarwide.bench;

import com.scalarwide.Wide256;

import java.math.BigInteger;
import java.util.Random;

/**
 * Batch / SoA probe: 1M far keys in three layouts, sequential and random-order
 * passes, to quantify object-layout cache effects.
 * Run: {@code BatchProbe obj|flat|big}.
 */
public final class BatchProbe {

    private static final int N = 1_000_000;
    private static final int REPS = 5;

    public static void main(String[] args) {
        String layout = args.length > 0 ? args[0] : "obj";
        Random rnd = new Random(20261010L);
        int[] seq = new int[N];
        for (int i = 0; i < N; i++) {
            seq[i] = i;
        }
        for (int i = N - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = seq[i];
            seq[i] = seq[j];
            seq[j] = tmp;
        }
        switch (layout) {
            case "obj" -> runObj(rnd, seq);
            case "flat" -> runFlat(rnd, seq);
            case "big" -> runBig(rnd, seq);
            default -> throw new IllegalArgumentException(layout);
        }
    }

    private static void runObj(Random rnd, int[] seq) {
        Wide256[] keys = new Wide256[N];
        for (int i = 0; i < N; i++) {
            keys[i] = sampleKey(rnd);
        }
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long s = 0;
            for (int i = 0; i < N; i++) {
                s += keys[i].hashCode();
            }
            long dt = System.nanoTime() - t0;
            System.out.printf("RESULT obj  hashSeq  %6.2f ns/key (s=%d)%n", dt / (double) N, s);
        }
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long s = 0;
            for (int i = 0; i < N; i++) {
                s += keys[seq[i]].hashCode();
            }
            long dt = System.nanoTime() - t0;
            System.out.printf("RESULT obj  hashRnd  %6.2f ns/key (s=%d)%n", dt / (double) N, s);
        }
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            Wide256 acc = Wide256.of(0, 0, 0, 0);
            for (int i = 0; i < N; i++) {
                acc = acc.add(keys[i]);
            }
            long dt = System.nanoTime() - t0;
            System.out.printf("RESULT obj  addSeq   %6.2f ns/key (acc.l0=%d)%n", dt / (double) N, acc.limb(0));
        }
        System.out.println("memory obj = ~56 B/key");
    }

    private static void runFlat(Random rnd, int[] seq) {
        long[] d = new long[4 * N];
        for (int i = 0; i < N; i++) {
            sampleCoords(rnd, d, 4 * i);
        }
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long s = 0;
            for (int i = 0; i < N; i++) {
                s += hashFlat(d, 4 * i);
            }
            long dt = System.nanoTime() - t0;
            System.out.printf("RESULT flat hashSeq  %6.2f ns/key (s=%d)%n", dt / (double) N, s);
        }
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long s = 0;
            for (int i = 0; i < N; i++) {
                s += hashFlat(d, 4 * seq[i]);
            }
            long dt = System.nanoTime() - t0;
            System.out.printf("RESULT flat hashRnd  %6.2f ns/key (s=%d)%n", dt / (double) N, s);
        }
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long a0 = 0;
            long a1 = 0;
            long a2 = 0;
            long a3 = 0;
            for (int i = 0; i < N; i++) {
                int o = 4 * i;
                long r0 = a0 + d[o];
                long c = Long.compareUnsigned(r0, a0) < 0 ? 1 : 0;
                long t1 = a1 + d[o + 1];
                long r1 = t1 + c;
                c = (Long.compareUnsigned(t1, a1) < 0 ? 1 : 0) + (Long.compareUnsigned(r1, t1) < 0 ? 1 : 0);
                long t2 = a2 + d[o + 2];
                long r2 = t2 + c;
                c = (Long.compareUnsigned(t2, a2) < 0 ? 1 : 0) + (Long.compareUnsigned(r2, t2) < 0 ? 1 : 0);
                long r3 = a3 + d[o + 3] + c;
                a0 = r0;
                a1 = r1;
                a2 = r2;
                a3 = r3;
            }
            long dt = System.nanoTime() - t0;
            System.out.printf("RESULT flat addSeq   %6.2f ns/key (acc.l0=%d)%n", dt / (double) N, a0);
        }
        System.out.println("memory flat = ~32 B/key");
    }

    private static void runBig(Random rnd, int[] seq) {
        BigInteger[] keys = new BigInteger[N];
        long[] tmp = new long[4];
        for (int i = 0; i < N; i++) {
            sampleCoords(rnd, tmp, 0);
            keys[i] = Wide256.of(tmp[0], tmp[1], tmp[2], tmp[3]).toBigInteger();
        }
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long s = 0;
            for (int i = 0; i < N; i++) {
                s += keys[i].hashCode();
            }
            long dt = System.nanoTime() - t0;
            System.out.printf("RESULT big  hashSeq  %6.2f ns/key (s=%d)%n", dt / (double) N, s);
        }
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long s = 0;
            for (int i = 0; i < N; i++) {
                s += keys[seq[i]].hashCode();
            }
            long dt = System.nanoTime() - t0;
            System.out.printf("RESULT big  hashRnd  %6.2f ns/key (s=%d)%n", dt / (double) N, s);
        }
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            BigInteger acc = BigInteger.ZERO;
            for (int i = 0; i < N; i++) {
                acc = acc.add(keys[i]);
            }
            long dt = System.nanoTime() - t0;
            System.out.printf("RESULT big  addSeq   %6.2f ns/key (acc.bitlen=%d)%n", dt / (double) N, acc.bitLength());
        }
        System.out.println("memory big = ~100+ B/key");
    }

    private static int hashFlat(long[] d, int o) {
        long h = 0x243F6A8885A308D3L;
        h = (h ^ d[o]) * 0x9E3779B97F4A7C15L;
        h = (h ^ d[o + 1]) * 0x9E3779B97F4A7C15L;
        h = (h ^ d[o + 2]) * 0x9E3779B97F4A7C15L;
        h = (h ^ d[o + 3]) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (int) (h ^ (h >>> 32));
    }

    private static Wide256 sampleKey(Random rnd) {
        long[] c = new long[4];
        sampleCoords(rnd, c, 0);
        return Wide256.of(c[0], c[1], c[2], c[3]);
    }

    /** Writes {xLo, xHi, zLo, zHi} for a far key at {@code out[off..]}. */
    private static void sampleCoords(Random rnd, long[] out, int off) {
        for (int k = 0; k < 2; k++) {
            int e = 20 + rnd.nextInt(108);
            long lo = rnd.nextLong();
            long hi = rnd.nextLong();
            if (e <= 64) {
                out[off + 2 * k] = (lo >>> (65 - e)) | (1L << (e - 1));
                out[off + 2 * k + 1] = 0;
            } else {
                out[off + 2 * k] = lo;
                out[off + 2 * k + 1] = (hi >>> (129 - e)) | (1L << (e - 65));
            }
        }
    }

    private BatchProbe() {
    }
}
