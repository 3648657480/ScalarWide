package com.scalarwide.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Probe: per-product instruction economics for the int-limb vs long-limb design.
 * long-limb atom building block = one 64x64 (lo + high); int-limb = one 32x32
 * (with zero-extension masks). The 256-bit atom needs 16 long-products or 64
 * int-products, so compare 16x(longProduct) vs 64x(intProduct).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class LimbWidthProbe {

    public long x = 0x9E3779B97F4A7C15L;
    public long y = 0xC2B2AE3D27D4EB4FL;

    @Benchmark
    public long longProduct() {
        long lo = x * y;
        long hi = Math.unsignedMultiplyHigh(x, y);
        return lo ^ hi;
    }

    @Benchmark
    public long intProduct() {
        long p = (x & 0xFFFFFFFFL) * (y & 0xFFFFFFFFL);
        return p ^ (p >>> 32);
    }
}
