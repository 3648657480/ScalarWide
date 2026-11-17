package com.scalarwide.mc.integer;

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

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

/**
 * MC-side {@code WideChunkPos.of} path. The long overload is the hot constructor: it allocates the
 * position plus two 1024-bit coordinates. Allocation is measured with {@code -prof gc} on the
 * same run ({@code gc.alloc.rate.norm}).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class WideChunkPosBench {

    private static final long X = 0x0123_4567_89AB_CDEFL;
    private static final long Z = -0x7EDC_BA98_7654_3210L;
    private static final BigInteger BX = BigInteger.ONE.shiftLeft(1000).add(BigInteger.valueOf(12345));
    private static final BigInteger BZ = BigInteger.ONE.shiftLeft(1000).negate();

    private WideChunkPos pos;

    @Setup(Level.Iteration)
    public void setup() {
        pos = WideChunkPos.of(X, Z);
    }

    @Benchmark
    public WideChunkPos ofLong() {
        return WideChunkPos.of(X, Z);
    }

    @Benchmark
    public WideChunkPos ofBig() {
        return WideChunkPos.of(BX, BZ);
    }

    @Benchmark
    public WideChunkPos neighborLong() {
        return pos.neighbor(1L, -1L);
    }

    @Benchmark
    public int hashCodeOp() {
        return pos.hashCode();
    }

    @Benchmark
    public boolean equalsOp() {
        return pos.equals(WideChunkPos.of(X, Z));
    }

    @Benchmark
    public byte[] toByteArray() {
        return pos.toByteArray();
    }
}
