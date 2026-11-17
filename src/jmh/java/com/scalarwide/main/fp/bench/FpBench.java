package com.scalarwide.main.fp.bench;

import com.scalarwide.main.fp.BigFloat;
import com.scalarwide.main.fp.Binary256;
import com.scalarwide.main.fp.FpFormat;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Floating-point benchmarks. Run with {@code -prof gc} to get the allocation columns; allocation
 * is the statistic that matters most here, because the arithmetic itself is only a few dozen
 * instructions while each operation currently allocates several scratch objects.
 *
 * <p>Fixed seed 12345 per the repository convention.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class FpBench {

    private static final int N = 1024;
    private static final long SEED = 12345L;

    private Binary256[] a = new Binary256[N];
    private Binary256[] b = new Binary256[N];
    private Binary256[] c = new Binary256[N];
    private BigFloat[] ba = new BigFloat[N];
    private BigFloat[] bb = new BigFloat[N];
    private BigFloat[] bc = new BigFloat[N];
    private BigFloat[] wa = new BigFloat[N];
    private BigFloat[] wb = new BigFloat[N];
    private BigFloat[] wc = new BigFloat[N];

    private static final FpFormat F237 = FpFormat.BINARY256;
    private static final FpFormat F616 = FpFormat.BINARY640;

    @Setup
    public void setup() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < N; i++) {
            double d = (rnd.nextDouble() + 0.5) * Math.pow(2, rnd.nextInt(40) - 20);
            double e = (rnd.nextDouble() + 0.5) * Math.pow(2, rnd.nextInt(40) - 20);
            double f = (rnd.nextDouble() + 0.5) * Math.pow(2, rnd.nextInt(40) - 20);
            a[i] = Binary256.of(d);
            b[i] = Binary256.of(e);
            c[i] = Binary256.of(f);
            ba[i] = BigFloat.of(F237, d);
            bb[i] = BigFloat.of(F237, e);
            bc[i] = BigFloat.of(F237, f);
            wa[i] = BigFloat.of(F616, d);
            wb[i] = BigFloat.of(F616, e);
            wc[i] = BigFloat.of(F616, f);
        }
    }

    // ---------------------------------------------------------------- Binary256

    @Benchmark
    public void b256Add(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].add(b[i]));
        }
    }

    @Benchmark
    public void b256Subtract(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].subtract(b[i]));
        }
    }

    @Benchmark
    public void b256Multiply(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].multiply(b[i]));
        }
    }

    @Benchmark
    public void b256Divide(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].divide(b[i]));
        }
    }

    @Benchmark
    public void b256Sqrt(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].sqrt());
        }
    }

    @Benchmark
    public void b256Fma(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].fma(b[i], c[i]));
        }
    }

    @Benchmark
    public void b256CompareTo(Blackhole bh) {
        int s = 0;
        for (int i = 0; i < N; i++) {
            s += a[i].compareTo(b[i]);
        }
        bh.consume(s);
    }

    @Benchmark
    public void b256HashCode(Blackhole bh) {
        int s = 0;
        for (int i = 0; i < N; i++) {
            s += a[i].hashCode();
        }
        bh.consume(s);
    }

    @Benchmark
    public void b256OfLong(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(Binary256.of(i * 0x9E3779B97F4A7C15L));
        }
    }

    @Benchmark
    public void b256OfDouble(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(Binary256.of(i * 0.7071067811865476));
        }
    }

    @Benchmark
    public void b256ToDouble(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].toDouble());
        }
    }

    @Benchmark
    public void b256ToString(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(a[i].toString(40));
        }
    }

    @Benchmark
    public void b256Parse(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(Binary256.of("1.234567890123456789012345678901234567890123456789e-17"));
        }
    }

    // ---------------------------------------------------------------- BigFloat 237

    @Benchmark
    public void bf237Add(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(ba[i].add(bb[i]));
        }
    }

    @Benchmark
    public void bf237Multiply(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(ba[i].multiply(bb[i]));
        }
    }

    @Benchmark
    public void bf237Divide(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(ba[i].divide(bb[i]));
        }
    }

    @Benchmark
    public void bf237Sqrt(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(ba[i].sqrt());
        }
    }

    // ---------------------------------------------------------------- BigFloat 616

    @Benchmark
    public void bf616Add(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(wa[i].add(wb[i]));
        }
    }

    @Benchmark
    public void bf616Multiply(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(wa[i].multiply(wb[i]));
        }
    }

    @Benchmark
    public void bf616Divide(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(wa[i].divide(wb[i]));
        }
    }

    @Benchmark
    public void bf616Sqrt(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(wa[i].sqrt());
        }
    }

    @Benchmark
    public void bf616Fma(Blackhole bh) {
        for (int i = 0; i < N; i++) {
            bh.consume(wa[i].fma(wb[i], wc[i]));
        }
    }
}
