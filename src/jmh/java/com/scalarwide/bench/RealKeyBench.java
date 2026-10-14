package com.scalarwide.bench;

import com.scalarwide.Wide256;
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

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 256-bit far-coordinate keys sampled from the practical workload
 * distribution (log-uniform magnitudes 2^20..2^127; see RealKeyStats).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class RealKeyBench {

    private static final BigInteger MASK = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

    public Wide256 a;
    public Wide256 b;
    public BigInteger ra;
    public BigInteger rb;
    public Wide256 ua;
    public Wide256 ub;
    public BigInteger rua;
    public BigInteger rub;

    @Setup
    public void setup() {
        Random rnd = new Random(20261010L);
        a = RealKeyStats.packKey(RealKeyStats.sampleCoord(rnd), RealKeyStats.sampleCoord(rnd));
        b = RealKeyStats.packKey(RealKeyStats.sampleCoord(rnd), RealKeyStats.sampleCoord(rnd));
        ra = a.toBigInteger();
        rb = b.toBigInteger();
        ua = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        ub = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        rua = ua.toBigInteger();
        rub = ub.toBigInteger();
    }

    @Benchmark
    public Wide256 add() {
        return a.add(b);
    }

    @Benchmark
    public BigInteger bigAdd() {
        return ra.add(rb);
    }

    @Benchmark
    public Wide256 addUniform() {
        return ua.add(ub);
    }

    @Benchmark
    public BigInteger bigAddUniform() {
        return rua.add(rub);
    }

    @Benchmark
    public Wide256 mul() {
        return a.mul(b);
    }

    @Benchmark
    public BigInteger bigMul() {
        return ra.multiply(rb);
    }

    // ---- 同映射对照（issue #1 修正）：低 256 位积 ----

    @Benchmark
    public BigInteger bigMulLow() {
        return ra.multiply(rb).and(MASK);
    }

    @Benchmark
    public BigInteger bigMulLowUniform() {
        return rua.multiply(rub).and(MASK);
    }

    @Benchmark
    public Wide256 mulUniform() {
        return ua.mul(ub);
    }

    @Benchmark
    public BigInteger bigMulUniform() {
        return rua.multiply(rub);
    }

    @Benchmark
    public int hash() {
        return a.hashCode();
    }

    @Benchmark
    public int bigHash() {
        return ra.hashCode();
    }

    @Benchmark
    public int compare() {
        return a.compareUnsigned(b);
    }

    @Benchmark
    public int bigCompare() {
        return ra.compareTo(rb);
    }
}
