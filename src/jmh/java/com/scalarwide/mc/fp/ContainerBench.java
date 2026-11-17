package com.scalarwide.mc.fp;

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
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for the 256-bit and 640-bit coordinate containers. The 1280-bit container has its own
 * fixture ({@code Binary1280Bench}) because it uses a fully unrolled field layout.
 *
 * <p>Run with {@code -prof gc}: every operation must stay at zero allocation.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ContainerBench {

    private static final long SEED = 12345L;
    /** Generous per-op iteration count: the loop mimics one octave of noise sampling. */
    private static final int OPS = 256;

    @Param({"256", "640"})
    public int width;

    private Container a;
    private Container b;
    private long[] out;
    private long[] chunk;

    @Setup
    public void setup() {
        a = width == 256 ? new Binary256() : new Binary640();
        b = width == 256 ? new Binary256() : new Binary640();
        Random rnd = new Random(SEED);
        a.set(0x1_0000_0000_0000L);
        b.set(3L);
        chunk = new long[a.limbs];
        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = rnd.nextLong();
        }
        chunk[chunk.length - 1] &= (1L << (a.precision - 64 * (chunk.length - 1) - 1)) - 1L;
        chunk[chunk.length - 1] |= 1L;
        out = new long[1];
    }

    @Benchmark
    public void mulSmall(Blackhole bh) {
        Container v = a;
        for (int i = 0; i < OPS; i++) {
            v.mulSmall(0x9E3779B97F4A7C15L);
        }
        bh.consume(v.signum());
    }

    @Benchmark
    public void addSmall(Blackhole bh) {
        Container v = a;
        v.scalePow2(-100);
        for (int i = 0; i < OPS; i++) {
            v.addSmall(i - (OPS >> 1));
        }
        bh.consume(v.signum());
    }

    @Benchmark
    public void scalePow2(Blackhole bh) {
        Container v = a;
        for (int i = 0; i < OPS; i++) {
            v.scalePow2(1);
        }
        bh.consume(v.lsbExponent());
    }

    @Benchmark
    public void floorLow64Frac(Blackhole bh) {
        Container v = a;
        v.scalePow2(-120);
        for (int i = 0; i < OPS; i++) {
            bh.consume(v.floorLow64Frac(out));
        }
    }

    @Benchmark
    public void copyFrom(Blackhole bh) {
        Container v = a;
        Container w = b;
        for (int i = 0; i < OPS; i++) {
            v.copyFrom(w);
        }
        bh.consume(v.signum());
    }

    @Benchmark
    public void compareTo(Blackhole bh) {
        Container v = a;
        Container w = b;
        int s = 0;
        for (int i = 0; i < OPS; i++) {
            s += v.compareTo(w);
        }
        bh.consume(s);
    }

    @Benchmark
    public void setFromLongArray(Blackhole bh) {
        Container v = a;
        for (int i = 0; i < OPS; i++) {
            v.setFrom(chunk);
        }
        bh.consume(v.signum());
    }
}
