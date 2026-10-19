package com.scalarwide.mc;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
public class WideSectionKey2560FlatMapBench {

    private static final BigInteger BASE = BigInteger.ONE.shiftLeft(1018);

    @Param({"4096", "65536", "262144"})
    public int keyCount;

    @Param({"50", "75"})
    public int loadPercent;

    private WideSectionKey2560[] keys;
    private WideSectionKey2560FlatMap map;
    private int cursor;

    @Setup
    public void setup() {
        Random rnd = new Random(0xB16B00B5L);
        keys = new WideSectionKey2560[keyCount];
        map = new WideSectionKey2560FlatMap(keyCount * 4, loadPercent);
        for (int i = 0; i < keyCount; i++) {
            BigInteger dx = BigInteger.valueOf(rnd.nextInt(1 << 20));
            BigInteger dy = BigInteger.valueOf(rnd.nextInt(1 << 20));
            BigInteger dz = BigInteger.valueOf(rnd.nextInt(1 << 20));
            keys[i] = WideSectionKey2560.of(BASE.add(dx), dy, BASE.negate().add(dz));
            map.put(keys[i], i);
        }
        if (map.size() != keyCount || map.capacity() != keyCount * 4) {
            throw new IllegalStateException("expected unique keys and no table growth");
        }
        System.out.printf("[round1-loadfactor-v1] java=%s seed=0xB16B00B5 "
            + "loadPercent=%d size=%d capacity=%d actualLoad=%.2f%n",
            System.getProperty("java.version"), loadPercent, map.size(), map.capacity(),
            (double) map.size() / map.capacity());
    }

    @Benchmark
    public Object getHit() {
        return map.get(keys[next()]);
    }

    @Benchmark
    public boolean containsHit() {
        return map.containsKey(keys[next()]);
    }

    @Benchmark
    public Object putExisting() {
        int i = next();
        return map.put(keys[i], i);
    }

    private int next() {
        return (cursor = (cursor + 1) & (keyCount - 1));
    }
}
