package com.scalarwide.bench;

import com.scalarwide.Wide1024;
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
public class Wide1024Bench {

    private static final BigInteger MASK = BigInteger.ONE.shiftLeft(1024).subtract(BigInteger.ONE);

    public Wide1024 a;
    public Wide1024 b;
    public BigInteger ra;
    public BigInteger rb;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a = Wide1024.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        b = Wide1024.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        ra = a.toBigInteger();
        rb = b.toBigInteger();
    }

    @Benchmark
    public Wide1024 mul() {
        return a.mul(b);
    }

    @Benchmark
    public Wide1024 mulHigh() {
        return a.mulHigh(b);
    }

    @Benchmark
    public BigInteger bigMul() {
        return ra.multiply(rb);
    }

    // ---- 同映射对照（issue #1 修正）：BigInteger 运算 + 截断到 N 位 ----

    /** 低 1024 位积 = BigInteger 全积 + 截断。 */
    @Benchmark
    public BigInteger bigMulLow() {
        return ra.multiply(rb).and(MASK);
    }

    /** 模 2^1024 加 = BigInteger 加 + 截断。 */
    @Benchmark
    public BigInteger bigAddTrunc() {
        return ra.add(rb).and(MASK);
    }

    /** 模 2^1024 减 = BigInteger 减 + 截断（二补码低 1024 位）。 */
    @Benchmark
    public BigInteger bigSubTrunc() {
        return ra.subtract(rb).and(MASK);
    }

    /** 定宽左移 = BigInteger 左移 + 截断。 */
    @Benchmark
    public BigInteger bigShlTrunc() {
        return ra.shiftLeft(123).and(MASK);
    }

    @Benchmark
    public Wide1024 add() {
        return a.add(b);
    }

    @Benchmark
    public BigInteger bigAdd() {
        return ra.add(rb);
    }

    @Benchmark
    public Wide1024 sub() {
        return a.sub(b);
    }

    @Benchmark
    public BigInteger bigSub() {
        return ra.subtract(rb);
    }

    @Benchmark
    public Wide1024 and() {
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
    public Wide1024 shl() {
        return a.shl(123);
    }

    @Benchmark
    public BigInteger bigShl() {
        return ra.shiftLeft(123);
    }

    @Benchmark
    public Wide1024 ushr() {
        return a.ushr(123);
    }

    @Benchmark
    public BigInteger bigUshr() {
        return ra.shiftRight(123);
    }

    @Benchmark
    public BigInteger bigMulHigh() {
        return ra.multiply(rb).shiftRight(1024);
    }

    @Benchmark
    public Wide1024 shr() {
        return a.shr(123);
    }

    @Benchmark
    public BigInteger bigShr() {
        return ra.shiftRight(123);
    }
}
