package com.scalarwide.bench;

import com.scalarwide.Wide256;
import com.scalarwide.mc.LongLongFlatMap;
import com.scalarwide.mc.Wide256FlatMap;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Random;

/**
 * Flat-map pipeline probe: 1M far keys, insert + mixed hit/miss lookup.
 * Run: {@code FlatMapProbe flat|hashmap|big [N]}.
 */
public final class FlatMapProbe {

    private static int N = 1_000_000;
    private static int MISS = 100_000;
    private static int LOOKUPS = 10_000_000;
    private static final int REPS = 3;

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "flat";
        if (args.length > 1) {
            N = Integer.parseInt(args[1]);
            MISS = Math.max(10_000, N / 10);
            LOOKUPS = Math.min(10_000_000, N * 3);
        }
        Random rnd = new Random(777L);
        Wide256[] keys = new Wide256[N];
        for (int i = 0; i < N; i++) {
            keys[i] = RealKeyStats.packKey(RealKeyStats.sampleCoord(rnd), RealKeyStats.sampleCoord(rnd));
        }
        Wide256[] miss = new Wide256[MISS];
        for (int i = 0; i < MISS; i++) {
            miss[i] = RealKeyStats.packKey(RealKeyStats.sampleCoord(rnd), RealKeyStats.sampleCoord(rnd));
        }
        int[] seq = new int[LOOKUPS];
        for (int l = 0; l < LOOKUPS; l++) {
            int r = rnd.nextInt(N + MISS);
            seq[l] = r < N ? r : ~(r - N);
        }
        long[] lkeys = new long[N];
        for (int i = 0; i < N; i++) {
            lkeys[i] = rnd.nextLong();
        }
        long[] lmiss = new long[MISS];
        for (int i = 0; i < MISS; i++) {
            lmiss[i] = rnd.nextLong();
        }
        switch (mode) {
            case "flat" -> runFlat(keys, miss, seq);
            case "hashmap" -> runHashmap(keys, miss, seq);
            case "big" -> runBig(keys, miss, seq);
            case "longs" -> runLongs(lkeys, lmiss, seq);
            case "hashmaplong" -> runHashmapLong(lkeys, lmiss, seq);
            default -> throw new IllegalArgumentException(mode);
        }
    }

    private static void runFlat(Wide256[] keys, Wide256[] miss, int[] seq) {
        int n = keys.length;
        warmFlat(keys, Math.min(200_000, n));
        long[] ins = new long[REPS];
        Wide256FlatMap map = null;
        for (int r = 0; r < REPS; r++) {
            map = new Wide256FlatMap(n * 2);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                map.put(keys[i], i + 1000);
            }
            ins[r] = System.nanoTime() - t0;
        }
        long[] look = new long[REPS];
        long checksum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long sum = 0;
            for (int l = 0; l < seq.length; l++) {
                int s = seq[l];
                sum += s >= 0 ? map.getOrDefault(keys[s], -1) : map.getOrDefault(miss[~s], -1);
            }
            look[r] = System.nanoTime() - t0;
            checksum += sum;
        }
        report("flat", ins, look, checksum, n);
    }

    private static void warmFlat(Wide256[] keys, int n) {
        Wide256FlatMap m = new Wide256FlatMap(4096);
        for (int i = 0; i < n; i++) {
            m.put(keys[i], i);
            m.get(keys[i]);
        }
    }

    private static void runHashmap(Wide256[] keys, Wide256[] miss, int[] seq) {
        int n = keys.length;
        Integer[] vals = new Integer[n];
        for (int i = 0; i < n; i++) {
            vals[i] = Integer.valueOf(i + 1000);
        }
        warmHash(keys, Math.min(200_000, n));
        long[] ins = new long[REPS];
        HashMap<Wide256, Integer> map = null;
        for (int r = 0; r < REPS; r++) {
            map = new HashMap<>(n * 2);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                map.put(keys[i], vals[i]);
            }
            ins[r] = System.nanoTime() - t0;
        }
        long[] look = new long[REPS];
        long checksum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long sum = 0;
            for (int l = 0; l < seq.length; l++) {
                int s = seq[l];
                Integer v = s >= 0 ? map.get(keys[s]) : map.get(miss[~s]);
                sum += v == null ? -1 : v;
            }
            look[r] = System.nanoTime() - t0;
            checksum += sum;
        }
        report("hashmap", ins, look, checksum, n);
    }

    private static void warmHash(Wide256[] keys, int n) {
        HashMap<Wide256, Integer> m = new HashMap<>(4096);
        for (int i = 0; i < n; i++) {
            m.put(keys[i], i);
            m.get(keys[i]);
        }
    }

    private static void runBig(Wide256[] keys, Wide256[] miss, int[] seq) {
        int n = keys.length;
        BigInteger[] bk = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            bk[i] = keys[i].toBigInteger();
        }
        BigInteger[] bm = new BigInteger[miss.length];
        for (int i = 0; i < miss.length; i++) {
            bm[i] = miss[i].toBigInteger();
        }
        Integer[] vals = new Integer[n];
        for (int i = 0; i < n; i++) {
            vals[i] = Integer.valueOf(i + 1000);
        }
        long[] ins = new long[REPS];
        HashMap<BigInteger, Integer> map = null;
        for (int r = 0; r < REPS; r++) {
            map = new HashMap<>(n * 2);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                map.put(bk[i], vals[i]);
            }
            ins[r] = System.nanoTime() - t0;
        }
        long[] look = new long[REPS];
        long checksum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long sum = 0;
            for (int l = 0; l < seq.length; l++) {
                int s = seq[l];
                Integer v = s >= 0 ? map.get(bk[s]) : map.get(bm[~s]);
                sum += v == null ? -1 : v;
            }
            look[r] = System.nanoTime() - t0;
            checksum += sum;
        }
        report("big", ins, look, checksum, n);
    }

    private static void runLongs(long[] lk, long[] lm, int[] seq) {
        int n = lk.length;
        warmLongs(lk, Math.min(200_000, n));
        long[] ins = new long[REPS];
        LongLongFlatMap map = null;
        for (int r = 0; r < REPS; r++) {
            map = new LongLongFlatMap(n * 2);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                map.put(lk[i], i + 1000);
            }
            ins[r] = System.nanoTime() - t0;
        }
        long[] look = new long[REPS];
        long checksum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long sum = 0;
            for (int l = 0; l < seq.length; l++) {
                int s = seq[l];
                sum += s >= 0 ? map.getOrDefault(lk[s], -1) : map.getOrDefault(lm[~s], -1);
            }
            look[r] = System.nanoTime() - t0;
            checksum += sum;
        }
        report("longs", ins, look, checksum, n);
    }

    private static void warmLongs(long[] lk, int n) {
        LongLongFlatMap m = new LongLongFlatMap(4096);
        for (int i = 0; i < n; i++) {
            m.put(lk[i], i);
            m.get(lk[i]);
        }
    }

    private static void runHashmapLong(long[] lk, long[] lm, int[] seq) {
        int n = lk.length;
        Integer[] vals = new Integer[n];
        for (int i = 0; i < n; i++) {
            vals[i] = Integer.valueOf(i + 1000);
        }
        warmHashLong(lk, Math.min(200_000, n));
        long[] ins = new long[REPS];
        HashMap<Long, Integer> map = null;
        for (int r = 0; r < REPS; r++) {
            map = new HashMap<>(n * 2);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                map.put(lk[i], vals[i]);
            }
            ins[r] = System.nanoTime() - t0;
        }
        long[] look = new long[REPS];
        long checksum = 0;
        for (int r = 0; r < REPS; r++) {
            long t0 = System.nanoTime();
            long sum = 0;
            for (int l = 0; l < seq.length; l++) {
                int s = seq[l];
                Integer v = s >= 0 ? map.get(lk[s]) : map.get(lm[~s]);
                sum += v == null ? -1 : v;
            }
            look[r] = System.nanoTime() - t0;
            checksum += sum;
        }
        report("hashmaplong", ins, look, checksum, n);
    }

    private static void warmHashLong(long[] lk, int n) {
        HashMap<Long, Integer> m = new HashMap<>(4096);
        for (int i = 0; i < n; i++) {
            m.put(lk[i], i);
            m.get(lk[i]);
        }
    }

    private static void report(String mode, long[] ins, long[] look, long checksum, int n) {
        for (int r = 0; r < REPS; r++) {
            System.out.printf("RESULT %s rep%d insert=%8.1f ns/op lookup=%7.2f ns/op%n",
                    mode, r, ins[r] / (double) n, look[r] / (double) LOOKUPS);
        }
        System.out.println("checksum " + mode + " = " + checksum);
    }

    private FlatMapProbe() {
    }
}
