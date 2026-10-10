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
 * Probe: do independent hash chains overlap when interleaved by hand in one
 * method, versus four plain (inlinable) hashCode calls?
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class WindowBench {

    private static final long C = 0x9E3779B97F4A7C15L;
    private static final long INIT = 0x243F6A8885A308D3L;

    public Wide256 a1;
    public Wide256 a2;
    public Wide256 a3;
    public Wide256 a4;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a1 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        a2 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        a3 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        a4 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
    }

    @Benchmark
    public int hashSequential4() {
        return a1.hashCode() ^ a2.hashCode() ^ a3.hashCode() ^ a4.hashCode();
    }

    @Benchmark
    public int hashInterleaved4() {
        long h1 = INIT;
        long h2 = INIT;
        long h3 = INIT;
        long h4 = INIT;
        h1 = (h1 ^ a1.limb(0)) * C;
        h2 = (h2 ^ a2.limb(0)) * C;
        h3 = (h3 ^ a3.limb(0)) * C;
        h4 = (h4 ^ a4.limb(0)) * C;
        h1 = (h1 ^ a1.limb(1)) * C;
        h2 = (h2 ^ a2.limb(1)) * C;
        h3 = (h3 ^ a3.limb(1)) * C;
        h4 = (h4 ^ a4.limb(1)) * C;
        h1 = (h1 ^ a1.limb(2)) * C;
        h2 = (h2 ^ a2.limb(2)) * C;
        h3 = (h3 ^ a3.limb(2)) * C;
        h4 = (h4 ^ a4.limb(2)) * C;
        h1 = (h1 ^ a1.limb(3)) * C;
        h2 = (h2 ^ a2.limb(3)) * C;
        h3 = (h3 ^ a3.limb(3)) * C;
        h4 = (h4 ^ a4.limb(3)) * C;
        h1 ^= h1 >>> 29;
        h2 ^= h2 >>> 29;
        h3 ^= h3 >>> 29;
        h4 ^= h4 >>> 29;
        return (int) ((h1 ^ (h1 >>> 32)) ^ (h2 ^ (h2 >>> 32))
                ^ (h3 ^ (h3 >>> 32)) ^ (h4 ^ (h4 >>> 32)));
    }
}
