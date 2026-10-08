package com.scalarwide.bench;

import com.scalarwide.Wide256;
import com.scalarwide.Wide512;
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
public class Wide256Bench {

    private static final int SHIFT = 37;

    public Wide256 a;
    public Wide256 b;
    public BigInteger ra;
    public BigInteger rb;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        long top = Long.MAX_VALUE;
        a = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        b = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        ra = a.toBigInteger();
        rb = b.toBigInteger();
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
    public Wide256 sub() {
        return a.sub(b);
    }

    @Benchmark
    public BigInteger bigSub() {
        return ra.subtract(rb);
    }

    @Benchmark
    public Wide256 mul() {
        return a.mul(b);
    }

    @Benchmark
    public BigInteger bigMul() {
        return ra.multiply(rb);
    }

    @Benchmark
    public Wide512 mulFull() {
        return a.mulFull(b);
    }

    @Benchmark
    public Wide256 and() {
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
    public Wide256 shl() {
        return a.shl(SHIFT);
    }

    @Benchmark
    public BigInteger bigShl() {
        return ra.shiftLeft(SHIFT);
    }

    @Benchmark
    public Wide256 ushr() {
        return a.ushr(SHIFT);
    }

    @Benchmark
    public BigInteger bigUshr() {
        return ra.shiftRight(SHIFT);
    }
}
