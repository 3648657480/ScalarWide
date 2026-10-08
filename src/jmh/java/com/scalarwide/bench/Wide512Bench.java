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
public class Wide512Bench {

    public Wide512 a;
    public Wide512 b;
    public BigInteger ra;
    public BigInteger rb;
    public Wide256 aLow;
    public Wide256 aHigh;
    public Wide256 bLow;
    public Wide256 bHigh;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        long top = Long.MAX_VALUE;
        a = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        b = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        ra = a.toBigInteger();
        rb = b.toBigInteger();
        aLow = Wide256.of(a.limb(0), a.limb(1), a.limb(2), a.limb(3));
        aHigh = Wide256.of(a.limb(4), a.limb(5), a.limb(6), a.limb(7));
        bLow = Wide256.of(b.limb(0), b.limb(1), b.limb(2), b.limb(3));
        bHigh = Wide256.of(b.limb(4), b.limb(5), b.limb(6), b.limb(7));
    }

    @Benchmark
    public Wide512 mulFullOne() {
        return aLow.mulFull(bLow);
    }

    @Benchmark
    public Wide512 mulFullTwo() {
        return aLow.mulFull(bLow).add(aHigh.mulFull(bHigh));
    }

    @Benchmark
    public Wide512 add() {
        return a.add(b);
    }

    @Benchmark
    public BigInteger bigAdd() {
        return ra.add(rb);
    }

    @Benchmark
    public Wide512 mul() {
        return a.mul(b);
    }

    @Benchmark
    public Wide512 mulHigh() {
        return a.mulHigh(b);
    }

    @Benchmark
    public BigInteger bigMul() {
        return ra.multiply(rb);
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
