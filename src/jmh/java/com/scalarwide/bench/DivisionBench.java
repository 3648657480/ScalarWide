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

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Division/remainder benchmark（无符号口径；除数半宽保证非短路）。
 *
 * <p>比较体系 v2（2026-10-14）：BigInteger 对照已移除（其 divide 为有符号、
 * 与无符号口径非同映射）；正确性对拍在测试套件（DivisionInternalTest 的
 * fast vs binary vs BigInteger oracle）。</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class DivisionBench {

    public Wide128 a128;
    public Wide128 b128;
    public Wide256 a256;
    public Wide256 b256;
    public Wide512 a512;
    public Wide512 b512;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a128 = Wide128.of(rnd.nextLong(), rnd.nextLong());
        b128 = Wide128.of(rnd.nextLong() | 1L, 0);
        a256 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        b256 = Wide256.of(rnd.nextLong() | 1L, rnd.nextLong(), 0, 0);
        a512 = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        b512 = Wide512.of(rnd.nextLong() | 1L, rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                0, 0, 0, 0);
    }

    @Benchmark
    public Wide128 div128() {
        return a128.div(b128);
    }

    @Benchmark
    public Wide128 rem128() {
        return a128.rem(b128);
    }

    @Benchmark
    public Wide256 div256() {
        return a256.div(b256);
    }

    @Benchmark
    public Wide256 rem256() {
        return a256.rem(b256);
    }

    @Benchmark
    public Wide512 div512() {
        return a512.div(b512);
    }

    @Benchmark
    public Wide512 rem512() {
        return a512.rem(b512);
    }
}
