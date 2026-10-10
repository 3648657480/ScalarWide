package com.scalarwide.bench;

import com.scalarwide.Wide768;
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
public class Wide768Bench {

    public Wide768 a;
    public Wide768 b;
    public BigInteger ra;
    public BigInteger rb;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a = Wide768.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        b = Wide768.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        ra = a.toBigInteger();
        rb = b.toBigInteger();
    }

    @Benchmark
    public Wide768 mul() {
        return a.mul(b);
    }

    @Benchmark
    public Wide768 mulHigh() {
        return a.mulHigh(b);
    }

    @Benchmark
    public BigInteger bigMul() {
        return ra.multiply(rb);
    }

    @Benchmark
    public Wide768 add() {
        return a.add(b);
    }

    @Benchmark
    public BigInteger bigAdd() {
        return ra.add(rb);
    }

    @Benchmark
    public Wide768 sub() {
        return a.sub(b);
    }

    @Benchmark
    public BigInteger bigSub() {
        return ra.subtract(rb);
    }

    @Benchmark
    public Wide768 and() {
        return a.and(b);
    }

    @Benchmark
    public BigInteger bigAnd() {
        return ra.and(rb);
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
    public int hash() {
        return a.hashCode();
    }

    @Benchmark
    public int bigHash() {
        return ra.hashCode();
    }

    @Benchmark
    public Wide768 shl() {
        return a.shl(123);
    }

    @Benchmark
    public BigInteger bigShl() {
        return ra.shiftLeft(123);
    }

    @Benchmark
    public Wide768 ushr() {
        return a.ushr(123);
    }

    @Benchmark
    public BigInteger bigUshr() {
        return ra.shiftRight(123);
    }

    @Benchmark
    public BigInteger bigMulHigh() {
        return ra.multiply(rb).shiftRight(768);
    }

    @Benchmark
    public Wide768 shr() {
        return a.shr(123);
    }

    @Benchmark
    public BigInteger bigShr() {
        return ra.shiftRight(123);
    }
}
