package com.scalarwide.main.integer;

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
 * Montgomery context benchmarks for the widths that have generated kernels (4/6/9 limbs).
 * The "before" numbers in {@code docs/bench.md} (E3) are the same operations on the generic loop.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class MontgomeryBench {

    @Param({"4", "6", "9"})
    public int limbs;

    private Montgomery ctx;
    private long[] a;
    private long[] b;
    private long[] out;
    private long[] exp;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        long[] m = new long[limbs];
        for (int i = 0; i < limbs; i++) {
            m[i] = rnd.nextLong();
        }
        m[limbs - 1] |= 1L << 40;
        m[0] |= 1L;
        ctx = new Montgomery(m);
        a = new long[limbs];
        b = new long[limbs];
        out = new long[limbs];
        exp = new long[limbs];
        for (int i = 0; i < limbs; i++) {
            a[i] = rnd.nextLong() & Long.MAX_VALUE;
            b[i] = rnd.nextLong() & Long.MAX_VALUE;
            exp[i] = rnd.nextLong();
        }
        // keep operands below the modulus
        a[limbs - 1] &= (1L << 40) - 1L;
        b[limbs - 1] &= (1L << 40) - 1L;
    }

    @Benchmark
    public void mulMont(Blackhole bh) {
        ctx.mulMont(a, b, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void mulMontLooped(Blackhole bh) {
        ctx.mulMontLooped(a, b, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void toMont(Blackhole bh) {
        ctx.toMont(a, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void powMod(Blackhole bh) {
        ctx.powMod(a, exp, out);
        bh.consume(out[0]);
    }
}
