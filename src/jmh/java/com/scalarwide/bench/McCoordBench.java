package com.scalarwide.bench;

import com.scalarwide.mc.WideChunkCoord1024;
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

/**
 * MC-side 1024-bit chunk coordinate ops. {@code shiftRight} is on the chunk-to-region
 * decomposition path ({@code WideRegionPos}); it used to round-trip through
 * {@link BigInteger} (encode 128 bytes, parse, shift, then re-extract 16 limbs).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class McCoordBench {

    private WideChunkCoord1024 a;
    private WideChunkCoord1024 b;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        byte[] x = new byte[128];
        byte[] z = new byte[128];
        rnd.nextBytes(x);
        rnd.nextBytes(z);
        a = WideChunkCoord1024.fromByteArray(x);
        b = WideChunkCoord1024.fromByteArray(z);
    }

    @Benchmark
    public WideChunkCoord1024 shiftRight5() {
        return a.shiftRight(5);
    }

    @Benchmark
    public WideChunkCoord1024 shiftRight9() {
        return a.shiftRight(9);
    }

    @Benchmark
    public WideChunkCoord1024 shiftRight64() {
        return a.shiftRight(64);
    }

    @Benchmark
    public WideChunkCoord1024 neighbor() {
        return a.neighbor(1L);
    }

    @Benchmark
    public int compareTo() {
        return a.compareTo(b);
    }

    @Benchmark
    public int hashCodeOp() {
        return a.hashCode();
    }

    /** Control: the encode-to-bytes path that `shiftRight` used to sit on top of. */
    @Benchmark
    public byte[] toByteArray() {
        return a.toByteArray();
    }
}
