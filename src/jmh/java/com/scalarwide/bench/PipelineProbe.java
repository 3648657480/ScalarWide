package com.scalarwide.bench;

import com.scalarwide.Wide256;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Random;

/**
 * Real-pipeline probe: 1M far-coordinate keys in a HashMap; measures insert,
 * mixed hit/miss lookup end to end. Run: {@code PipelineProbe ours|big}.
 */
public final class PipelineProbe {

    private static int N = 1_000_000;
    private static int MISS = 100_000;
    private static int LOOKUPS = 10_000_000;
    private static final int REPS = 3;

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "ours";
        if (args.length > 1) {
            N = Integer.parseInt(args[1]);
            MISS = Math.max(10_000, N / 10);
            LOOKUPS = Math.min(10_000_000, N * 3);
        }
        Random rnd = new Random(777L);
        Wide256[] keys = new Wide256[N];
        for (int i = 0; i < N; i++) {
            keys[i] = sampleKey(rnd);
        }
        Wide256[] miss = new Wide256[MISS];
        for (int i = 0; i < MISS; i++) {
            miss[i] = sampleKey(rnd);
        }
        int[] seq = new int[LOOKUPS];
        for (int l = 0; l < LOOKUPS; l++) {
            int r = rnd.nextInt(N + MISS);
            seq[l] = r < N ? r : ~(r - N);
        }
        if (mode.equals("big")) {
            runBig(keys, miss, seq);
        } else {
            runOurs(keys, miss, seq);
        }
    }

    private static void runOurs(Wide256[] keys, Wide256[] miss, int[] seq) {
        Integer[] vals = new Integer[N];
        for (int i = 0; i < N; i++) {
            vals[i] = Integer.valueOf(i + 1000);
        }
        warm(new HashMap<Wide256, Integer>(4096), keys, Math.min(200_000, N));
        long[] ins = new long[REPS];
        HashMap<Wide256, Integer> map = null;
        for (int r = 0; r < REPS; r++) {
            map = new HashMap<>(N * 4 / 3);
            long t0 = System.nanoTime();
            for (int i = 0; i < N; i++) {
                map.put(keys[i], vals[i]);
            }
            ins[r] = System.nanoTime() - t0;
        }
        long[] look = new long[REPS];
        long checksum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long sum = 0;
            for (int l = 0; l < LOOKUPS; l++) {
                int s = seq[l];
                Integer v = s >= 0 ? map.get(keys[s]) : map.get(miss[~s]);
                sum += v == null ? -1 : v;
            }
            look[r] = System.nanoTime() - t0;
            checksum += sum;
        }
        report("ours", ins, look, checksum);
    }

    private static void runBig(Wide256[] keys, Wide256[] miss, int[] seq) {
        BigInteger[] bk = new BigInteger[N];
        for (int i = 0; i < N; i++) {
            bk[i] = keys[i].toBigInteger();
        }
        BigInteger[] bm = new BigInteger[MISS];
        for (int i = 0; i < MISS; i++) {
            bm[i] = miss[i].toBigInteger();
        }
        Integer[] vals = new Integer[N];
        for (int i = 0; i < N; i++) {
            vals[i] = Integer.valueOf(i + 1000);
        }
        warm(new HashMap<BigInteger, Integer>(4096), bk, Math.min(200_000, N));
        long[] ins = new long[REPS];
        HashMap<BigInteger, Integer> map = null;
        for (int r = 0; r < REPS; r++) {
            map = new HashMap<>(N * 4 / 3);
            long t0 = System.nanoTime();
            for (int i = 0; i < N; i++) {
                map.put(bk[i], vals[i]);
            }
            ins[r] = System.nanoTime() - t0;
        }
        long[] look = new long[REPS];
        long checksum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long sum = 0;
            for (int l = 0; l < LOOKUPS; l++) {
                int s = seq[l];
                Integer v = s >= 0 ? map.get(bk[s]) : map.get(bm[~s]);
                sum += v == null ? -1 : v;
            }
            look[r] = System.nanoTime() - t0;
            checksum += sum;
        }
        report("big", ins, look, checksum);
    }

    private static void warm(HashMap<Wide256, Integer> m, Wide256[] k, int n) {
        for (int i = 0; i < n; i++) {
            m.put(k[i], i);
            m.get(k[i]);
            m.remove(k[i]);
        }
    }

    private static void warm(HashMap<BigInteger, Integer> m, BigInteger[] k, int n) {
        for (int i = 0; i < n; i++) {
            m.put(k[i], i);
            m.get(k[i]);
            m.remove(k[i]);
        }
    }

    private static void report(String mode, long[] ins, long[] look, long checksum) {
        for (int r = 0; r < REPS; r++) {
            System.out.printf("RESULT %s rep%d insert=%8.1f ns/op lookup=%7.2f ns/op%n",
                    mode, r, ins[r] / (double) N, look[r] / (double) LOOKUPS);
        }
        System.out.println("checksum " + mode + " = " + checksum);
    }

    /** Samples a far key: X, Z magnitudes log-uniform in [2^20, 2^128). */
    private static Wide256 sampleKey(Random rnd) {
        long[] c = new long[2];
        coord(rnd, c);
        long xLo = c[0];
        long xHi = c[1];
        coord(rnd, c);
        return Wide256.of(xLo, xHi, c[0], c[1]);
    }

    /** One non-negative coordinate; writes {lo, hi} into {@code out}. */
    private static void coord(Random rnd, long[] out) {
        int e = 20 + rnd.nextInt(108);
        long lo = rnd.nextLong();
        long hi = rnd.nextLong();
        if (e <= 64) {
            out[0] = (lo >>> (65 - e)) | (1L << (e - 1));
            out[1] = 0;
        } else {
            out[0] = lo;
            out[1] = (hi >>> (129 - e)) | (1L << (e - 65));
        }
    }

    private PipelineProbe() {
    }
}
