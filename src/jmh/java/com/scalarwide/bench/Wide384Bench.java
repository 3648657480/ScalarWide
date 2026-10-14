package com.scalarwide.bench;

import com.scalarwide.Wide384;
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
public class Wide384Bench {

    public Wide384 a;
    public Wide384 b;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a = Wide384.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        b = Wide384.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
    }

    @Benchmark
    public Wide384 mul() {
        return a.mul(b);
    }

    @Benchmark
    public Wide384 mulHigh() {
        return a.mulHigh(b);
    }

    @Benchmark
    public int compare() {
        return a.compareUnsigned(b);
    }

    @Benchmark
    public Wide384 add() {
        return a.add(b);
    }

    @Benchmark
    public Wide384 sub() {
        return a.sub(b);
    }

    @Benchmark
    public Wide384 and() {
        return a.and(b);
    }

    @Benchmark
    public Wide384 shl() {
        return a.shl(123);
    }

    @Benchmark
    public Wide384 ushr() {
        return a.ushr(123);
    }

    @Benchmark
    public Wide384 shr() {
        return a.shr(123);
    }
}
