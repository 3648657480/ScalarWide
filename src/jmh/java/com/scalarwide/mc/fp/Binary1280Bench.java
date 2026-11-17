package com.scalarwide.mc.fp.bench;

import com.scalarwide.mc.fp.Binary1280;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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
 * MC-side 1280-bit container ops at the two far-domain magnitudes the G1.1 budget names
 * (2^53 and 2^1027). Every op mutates the receiver in place, so each benchmark consumes the
 * receiver in a blackhole and restores the canonical operand per measurement iteration.
 *
 * <p>Allocation is checked separately with {@code -prof gc} on the same run.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class Binary1280Bench {

    private static final long MUL_W = 0x9E3779B97F4A7C15L;

    private final long[] out = new long[1];
    private final long[] chunk = new long[16];
    private Binary1280 v53;
    private Binary1280 v1027;
    private Binary1280 w53;
    private Binary1280 w1027;

    @Setup
    public void setup() {
        v53 = new Binary1280();
        v1027 = new Binary1280();
        w53 = new Binary1280();
        w1027 = new Binary1280();
        Random rnd = new Random(12345L);
        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = rnd.nextLong();
        }
        chunk[chunk.length - 1] |= 1L << 40;
        reset();
    }

    @Setup(Level.Iteration)
    public void reset() {
        v53.set(1L << 53).addSmall(15L);
        v1027.set(1L);
        v1027.scalePow2(1027);
        v1027.addSmall(15L);
        w53.set(1L << 53);
        w1027.set(1L);
        w1027.scalePow2(1027);
    }

    @Benchmark
    public double scalePow2_53() {
        v53.scalePow2(1);
        return v53.lsbExponent();
    }

    @Benchmark
    public double floorLow64Frac_53() {
        double f = v53.floorLow64Frac(out);
        return f + out[0];
    }

    @Benchmark
    public double floorLow64Frac_1027() {
        double f = v1027.floorLow64Frac(out);
        return f + out[0];
    }

    @Benchmark
    public Binary1280 mulSmall_53() {
        v53.mulSmall(MUL_W);
        return v53;
    }

    @Benchmark
    public Binary1280 mulSmall_1027() {
        v1027.mulSmall(MUL_W);
        return v1027;
    }

    @Benchmark
    public Binary1280 addSmall_53() {
        v53.addSmall(15L);
        return v53;
    }

    @Benchmark
    public Binary1280 addSmall_1027() {
        v1027.addSmall(15L);
        return v1027;
    }

    @Benchmark
    public int compareTo_53() {
        return v53.compareTo(w53);
    }

    @Benchmark
    public int compareTo_1027() {
        return v1027.compareTo(w1027);
    }

    @Benchmark
    public Binary1280 copyFrom_53() {
        w53.copyFrom(v53);
        return w53;
    }

    @Benchmark
    public Binary1280 setFrom_chunk() {
        v53.setFrom(chunk);
        return v53;
    }
}
