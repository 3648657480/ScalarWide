package com.scalarwide.bench;

import com.scalarwide.Wide128;
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

/** Division/remainder benchmark (v1 binary engine vs BigInteger divide). */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class DivisionBench {

    public Wide128 a128;
    public Wide128 b128;
    public BigInteger r128a;
    public BigInteger r128b;
    public Wide256 a256;
    public Wide256 b256;
    public BigInteger r256a;
    public BigInteger r256b;
    public Wide512 a512;
    public Wide512 b512;
    public BigInteger r512a;
    public BigInteger r512b;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a128 = Wide128.of(rnd.nextLong(), rnd.nextLong());
        b128 = Wide128.of(rnd.nextLong() | 1L, 0);
        r128a = a128.toBigInteger();
        r128b = b128.toBigInteger();
        a256 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        b256 = Wide256.of(rnd.nextLong() | 1L, rnd.nextLong(), 0, 0);
        r256a = a256.toBigInteger();
        r256b = b256.toBigInteger();
        a512 = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        b512 = Wide512.of(rnd.nextLong() | 1L, rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                0, 0, 0, 0);
        r512a = a512.toBigInteger();
        r512b = b512.toBigInteger();
    }

    @Benchmark
    public Wide128 div128() {
        return a128.div(b128);
    }

    @Benchmark
    public BigInteger bigDiv128() {
        return r128a.divide(r128b);
    }

    @Benchmark
    public Wide128 rem128() {
        return a128.rem(b128);
    }

    @Benchmark
    public BigInteger bigRem128() {
        return r128a.remainder(r128b);
    }

    @Benchmark
    public Wide256 div256() {
        return a256.div(b256);
    }

    @Benchmark
    public BigInteger bigDiv256() {
        return r256a.divide(r256b);
    }

    @Benchmark
    public Wide256 rem256() {
        return a256.rem(b256);
    }

    @Benchmark
    public BigInteger bigRem256() {
        return r256a.remainder(r256b);
    }

    @Benchmark
    public Wide512 div512() {
        return a512.div(b512);
    }

    @Benchmark
    public BigInteger bigDiv512() {
        return r512a.divide(r512b);
    }

    @Benchmark
    public Wide512 rem512() {
        return a512.rem(b512);
    }

    @Benchmark
    public BigInteger bigRem512() {
        return r512a.remainder(r512b);
    }
}
