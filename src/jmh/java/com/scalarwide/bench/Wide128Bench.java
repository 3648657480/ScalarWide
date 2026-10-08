package com.scalarwide.bench;

import com.scalarwide.Wide128;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class Wide128Bench {

    private static final int SHIFT = 37;

    public Wide128 a;
    public Wide128 b;
    public BigInteger ra;
    public BigInteger rb;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a = Wide128.of(rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        b = Wide128.of(rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        ra = a.toBigInteger();
        rb = b.toBigInteger();
    }

    @Benchmark
    public Wide128 add() {
        return a.add(b);
    }

    @Benchmark
    public BigInteger bigAdd() {
        return ra.add(rb);
    }

    @Benchmark
    public Wide128 sub() {
        return a.sub(b);
    }

    @Benchmark
    public BigInteger bigSub() {
        return ra.subtract(rb);
    }

    @Benchmark
    public Wide128 mul() {
        return a.mul(b);
    }

    @Benchmark
    public BigInteger bigMul() {
        return ra.multiply(rb);
    }

    @Benchmark
    public Wide128 and() {
        return a.and(b);
    }

    @Benchmark
    public BigInteger bigAnd() {
        return ra.and(rb);
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

    @Benchmark
    public Wide128 shl() {
        return a.shl(SHIFT);
    }

    @Benchmark
    public BigInteger bigShl() {
        return ra.shiftLeft(SHIFT);
    }

    @Benchmark
    public Wide128 ushr() {
        return a.ushr(SHIFT);
    }

    @Benchmark
    public BigInteger bigUshr() {
        return ra.shiftRight(SHIFT);
    }
}
