package com.scalarwide.bench;

import com.scalarwide.Wide1024;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 宽键 vs 裸 {@code long} 键——同 harness、同一批查找序列、同一张表规模。
 *
 * <p>这是"竞争对手如果是 long 键"的正面回答：宽键在**键操作**（hash/equals/查表）上到底贵多少，
 * 以及在**算术**上贵多少。两条曲线必须分开看——map 热路径只跑前者，后者只在真的要算数时才付。
 *
 * <p>表规模 10 万键（1024 位键约 14 MB 工作集，落在 7950X 的 L3 内）；查找 100 万次。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class LongBaselineBench {

    private static final int N = 100_000;
    private static final int LOOKUPS = 1_000_000;

    private Long[] longKeys;
    private Wide1024[] wideKeys;
    private Wide1024[] wideMiss;
    private Long[] longMiss;
    private int[] seq;
    private HashMap<Long, Integer> longMap;
    private HashMap<Wide1024, Integer> wideMap;

    private int cursor;
    private Wide1024 probeA;
    private Wide1024 probeB;
    private Long boxA;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        longKeys = new Long[N];
        longMiss = new Long[N];
        wideKeys = new Wide1024[N];
        wideMiss = new Wide1024[N];
        longMap = new HashMap<>(N * 2);
        wideMap = new HashMap<>(N * 2);
        for (int i = 0; i < N; i++) {
            long v = rnd.nextLong();
            longKeys[i] = v;
            longMiss[i] = v ^ 0x5DEECE66DL;
            Wide1024 w = Wide1024.of(v, rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                    rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                    rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                    rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
            wideKeys[i] = w;
            wideMiss[i] = Wide1024.of(v ^ 0x5DEECE66DL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            longMap.put(v, i);
            wideMap.put(w, i);
        }
        seq = new int[LOOKUPS];
        for (int i = 0; i < LOOKUPS; i++) {
            seq[i] = rnd.nextInt(N);
        }
        probeA = wideKeys[0];
        probeB = wideKeys[1];
        boxA = longKeys[0];
        // prime both caches
        longMap.get(longKeys[0]);
        wideMap.get(wideKeys[0]);
        wideKeys[0].hashCode();
    }

    @Benchmark
    @OperationsPerInvocation(LOOKUPS)
    public long longMapGet() {
        int[] s = seq;
        HashMap<Long, Integer> m = longMap;
        Long[] k = longKeys;
        long sum = 0;
        for (int i = 0; i < LOOKUPS; i++) {
            Integer v = m.get(k[s[i]]);
            sum += v == null ? -1 : v;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(LOOKUPS)
    public long wideMapGet() {
        int[] s = seq;
        HashMap<Wide1024, Integer> m = wideMap;
        Wide1024[] k = wideKeys;
        long sum = 0;
        for (int i = 0; i < LOOKUPS; i++) {
            Integer v = m.get(k[s[i]]);
            sum += v == null ? -1 : v;
        }
        return sum;
    }

    /** 同一序列但键不在表里：把 equals 的失配路径也覆盖到。 */
    @Benchmark
    @OperationsPerInvocation(LOOKUPS)
    public long longMapMiss() {
        int[] s = seq;
        HashMap<Long, Integer> m = longMap;
        Long[] k = longMiss;
        long sum = 0;
        for (int i = 0; i < LOOKUPS; i++) {
            Integer v = m.get(k[s[i]]);
            sum += v == null ? -1 : v;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(LOOKUPS)
    public long wideMapMiss() {
        int[] s = seq;
        HashMap<Wide1024, Integer> m = wideMap;
        Wide1024[] k = wideMiss;
        long sum = 0;
        for (int i = 0; i < LOOKUPS; i++) {
            Integer v = m.get(k[s[i]]);
            sum += v == null ? -1 : v;
        }
        return sum;
    }

    @Benchmark
    public int longHash() {
        return boxA.hashCode();
    }

    @Benchmark
    public int wideHash() {
        return probeA.hashCode();
    }

    @Benchmark
    public boolean longEquals() {
        return longKeys[0].equals(boxA);
    }

    @Benchmark
    public boolean wideEquals() {
        return probeA.equals(probeB);
    }
}
