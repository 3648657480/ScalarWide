package com.scalarwide;

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
 * mulHigh variants comparison: current public implementation, the block-
 * composed oracle (768) / monolithic loop (1024), and the OI-style column
 * accumulation.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class DiagMulHighBench {

    public Wide768 a768;
    public Wide768 b768;
    public Wide1024 a1024;
    public Wide1024 b1024;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a768 = Wide768.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        b768 = Wide768.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        a1024 = Wide1024.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        b1024 = Wide1024.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
    }

    @Benchmark
    public Wide768 mh768Current() {
        return a768.mulHigh(b768);
    }

    @Benchmark
    public Wide1024 mh1024Current() {
        return a1024.mulHigh(b1024);
    }

    @Benchmark
    public Wide1024 mh1024Composed() {
        return a1024.mulHighComposed(b1024);
    }

    /**
     * 2026-11-05 修复前的原实现：内层用 {@code o.limb(j)} 运行时索引访问器
     * （每次调用 256 次 16-way switch）。保留为同 run 的新旧对拍基线。
     */
    @Benchmark
    public Wide1024 mh1024LegacySwitch() {
        return legacyMulHighSwitch(a1024, b1024);
    }

    static Wide1024 legacyMulHighSwitch(Wide1024 x, Wide1024 y) {
        long[] acc = new long[32];
        for (int i = 0; i < 16; i++) {
            long ai = x.limb(i);
            long carry = 0;
            for (int j = 0; j < 16; j++) {
                long bj = y.limb(j);
                long lo = ai * bj;
                long hi = Math.unsignedMultiplyHigh(ai, bj);
                long t = acc[i + j] + carry;
                long c1 = Long.compareUnsigned(t, acc[i + j]) < 0 ? 1 : 0;
                long xx = t + lo;
                long c2 = Long.compareUnsigned(xx, t) < 0 ? 1 : 0;
                acc[i + j] = xx;
                carry = hi + c1 + c2;
            }
            acc[i + 16] = carry;
        }
        return Wide1024.of(acc[16], acc[17], acc[18], acc[19], acc[20], acc[21],
                acc[22], acc[23], acc[24], acc[25], acc[26], acc[27],
                acc[28], acc[29], acc[30], acc[31]);
    }
}
