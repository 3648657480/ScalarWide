package com.scalarwide.fp.bench;

import com.scalarwide.fp.Binary1280;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * The three operations the G1.1 binary1280 budget constrains
 * ({@code docs/B1280-CONTAINER.md}). Each invocation performs {@value #N} operations, so the
 * reported ns/op is divided by {@code N} before comparing with the budget. Run with {@code -prof gc}
 * to confirm the zero-allocation requirement.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class B1280Bench {

    private static final int N = 1024;

    /** Leading-bit exponent of the operand, at the two extremes the budget names. */
    @Param({"53", "1027"})
    public int lead;

    private Binary1280 v;
    private Binary1280 other;
    private long[] out;
    private double fracSink;

    @Setup
    public void setup() {
        v = new Binary1280();
        v.set(0x1FFFFFFFFFFFFFL);
        v.scalePow2(lead - 53);
        other = new Binary1280().copyFrom(v);
        out = new long[1];
    }

    @Benchmark
    public void b1280ScalePow2(Blackhole bh) {
        Binary1280 x = v;
        for (int i = 0; i < N; i++) {
            x.scalePow2(1);
        }
        x.scalePow2(-N);
        bh.consume(x.lsbExponent());
    }

    @Benchmark
    public void b1280FloorLow64Frac(Blackhole bh) {
        Binary1280 x = v;
        long[] o = out;
        double s = 0;
        for (int i = 0; i < N; i++) {
            s += x.floorLow64Frac(o);
            s += o[0];
        }
        bh.consume(s);
    }

    @Benchmark
    public void b1280MulSmall(Blackhole bh) {
        Binary1280 x = v;
        for (int i = 0; i < N; i++) {
            x.mulSmall(0x9E3779B97F4A7C15L);
        }
        bh.consume(x.lsbExponent());
    }

    @Benchmark
    public void b1280Compare(Blackhole bh) {
        Binary1280 x = v;
        Binary1280 y = other;
        int s = 0;
        for (int i = 0; i < N; i++) {
            s += x.compareTo(y);
        }
        bh.consume(s);
    }
}
