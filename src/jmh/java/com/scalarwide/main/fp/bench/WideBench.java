package com.scalarwide.main.fp.bench;

import com.scalarwide.main.fp.Binary1280;
import com.scalarwide.main.fp.Binary640;
import com.scalarwide.main.fp.BinaryWide;
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
 * Benchmarks for the general wide formats ({@code Binary640}, {@code Binary1280}). The 256-bit
 * format has its own measurements in {@link FpBench}; these two were added later and had never been
 * measured.
 *
 * <p>Run with {@code -prof gc}: allocation is the first thing to fix here, because every operation
 * currently allocates a {@code WideOps.Value} record plus its frames.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
@SuppressWarnings({"rawtypes", "unchecked"})
public class WideBench {

    private static final int N = 256;
    private static final long SEED = 12345L;

    /** Decimal digits of the significand / limb count of the format under test. */
    @Param({"640", "1280"})
    public int width;

    @SuppressWarnings("rawtypes")
    private BinaryWide[] a;
    @SuppressWarnings("rawtypes")
    private BinaryWide[] b;
    private long[] out;

    @Setup
    public void setup() {
        Random rnd = new Random(SEED);
        a = new BinaryWide[N];
        b = new BinaryWide[N];
        for (int i = 0; i < N; i++) {
            double d = (rnd.nextDouble() + 0.5) * Math.pow(2, rnd.nextInt(40) - 20);
            double e = (rnd.nextDouble() + 0.5) * Math.pow(2, rnd.nextInt(40) - 20);
            a[i] = make(d);
            b[i] = make(e);
        }
    }

    @SuppressWarnings("rawtypes")
    private BinaryWide make(double v) {
        return width == 640 ? Binary640.of(v) : Binary1280.of(v);
    }

    @Benchmark
    public void add(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].add(b[i]));
        }
    }

    @Benchmark
    public void subtract(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].subtract(b[i]));
        }
    }

    @Benchmark
    public void multiply(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].multiply(b[i]));
        }
    }

    @Benchmark
    public void divide(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].divide(b[i]));
        }
    }

    @Benchmark
    public void sqrt(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].abs().sqrt());
        }
    }

    @Benchmark
    public void compareTo(Blackhole bh) {
        int s = 0;
        for (int i = 0; i < N; i++) {
            s += a[i].compareTo(b[i]);
        }
        bh.consume(s);
    }

    @Benchmark
    public void ofDouble(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(make(i * 0.7071067811865476));
        }
    }

    @Benchmark
    public void toString40(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].toString(40));
        }
    }
}
