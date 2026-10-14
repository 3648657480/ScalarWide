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

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 256-bit far-coordinate keys sampled from the practical workload
 * distribution (log-uniform magnitudes 2^20..2^127; see RealKeyStats).
 *
 * <p>比较体系 v2（2026-10-14）：BigInteger 对照已移除——仅用于正确性对拍
 * （测试套件），性能基准中不再出现；对照见 {@link NatCompareBench} 与自有版本前后对比。</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class RealKeyBench {

    public Wide256 a;
    public Wide256 b;
    public Wide256 ua;
    public Wide256 ub;

    @Setup
    public void setup() {
        Random rnd = new Random(20261010L);
        a = RealKeyStats.packKey(RealKeyStats.sampleCoord(rnd), RealKeyStats.sampleCoord(rnd));
        b = RealKeyStats.packKey(RealKeyStats.sampleCoord(rnd), RealKeyStats.sampleCoord(rnd));
        ua = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
        ub = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & Long.MAX_VALUE);
    }

    @Benchmark
    public Wide256 add() {
        return a.add(b);
    }

    @Benchmark
    public Wide256 addUniform() {
        return ua.add(ub);
    }

    @Benchmark
    public Wide256 mul() {
        return a.mul(b);
    }

    @Benchmark
    public Wide256 mulUniform() {
        return ua.mul(ub);
    }

    @Benchmark
    public int hash() {
        return a.hashCode();
    }

    @Benchmark
    public int compare() {
        return a.compareUnsigned(b);
    }
}
