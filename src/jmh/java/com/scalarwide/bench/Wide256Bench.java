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

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 算术基准（比较体系 v2，2026-10-14）：性能对照 = 同场景加速库（{@link NatCompareBench}）
 * 与自有版本前后对比；BigInteger 仅用于正确性对拍（测试套件），性能基准中不再出现。
 */
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

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        b = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
    }

    @Benchmark
    public Wide256 add() {
        return a.add(b);
    }

    @Benchmark
    public Wide256 sub() {
        return a.sub(b);
    }

    @Benchmark
    public Wide256 mul() {
        return a.mul(b);
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
    public int hash() {
        return a.hashCode();
    }

    @Benchmark
    public int compare() {
        return a.compareUnsigned(b);
    }

    @Benchmark
    public Wide256 shl() {
        return a.shl(SHIFT);
    }

    @Benchmark
    public Wide256 ushr() {
        return a.ushr(SHIFT);
    }
}
