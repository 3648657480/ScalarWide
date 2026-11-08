package com.scalarwide.bench;

import com.scalarwide.Wide1024;
import com.scalarwide.Wide256;
import com.scalarwide.Wide512;
import com.scalarwide.Wide768;
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
 * Minimal-length byte encoders ({@code toByteArray} / {@code toByteArrayLE}) against the
 * fixed-width one as a control. The pair used to be byte-at-a-time loops; at 1024 bits that
 * is 128 byte stores versus 16 limb stores.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class MinBytesBench {

    private Wide256 a256;
    private Wide512 a512;
    private Wide768 a768;
    private Wide1024 a1024;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a256 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        a512 = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        a768 = Wide768.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        a1024 = Wide1024.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
    }

    @Benchmark
    public byte[] w256Min() {
        return a256.toByteArray();
    }

    @Benchmark
    public byte[] w512Min() {
        return a512.toByteArray();
    }

    @Benchmark
    public byte[] w768Min() {
        return a768.toByteArray();
    }

    @Benchmark
    public byte[] w1024Min() {
        return a1024.toByteArray();
    }

    @Benchmark
    public byte[] w1024MinLE() {
        return a1024.toByteArrayLE();
    }

    /** Control: the fixed-width encoder, already VarHandle-based. */
    @Benchmark
    public byte[] w1024Fixed() {
        return a1024.toByteArrayFixed();
    }
}
