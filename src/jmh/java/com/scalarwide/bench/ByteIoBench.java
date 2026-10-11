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

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Byte serialization: ours vs BigInteger.toByteArray / BigInteger(byte[]). */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class ByteIoBench {

    public Wide128 a128;
    public Wide256 a256;
    public Wide512 a512;
    public BigInteger r128;
    public BigInteger r256;
    public BigInteger r512;
    public byte[] b128;
    public byte[] b256;
    public byte[] b512;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a128 = Wide128.of(rnd.nextLong(), rnd.nextLong());
        a256 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        a512 = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        r128 = a128.toBigInteger();
        r256 = a256.toBigInteger();
        r512 = a512.toBigInteger();
        b128 = a128.toByteArray();
        b256 = a256.toByteArray();
        b512 = a512.toByteArray();
    }

    @Benchmark
    public byte[] toBytes128() {
        return a128.toByteArray();
    }

    @Benchmark
    public byte[] bigToBytes128() {
        return r128.toByteArray();
    }

    @Benchmark
    public Wide128 fromBytes128() {
        return Wide128.fromByteArray(b128);
    }

    @Benchmark
    public BigInteger bigFromBytes128() {
        return new BigInteger(1, b128);
    }

    @Benchmark
    public byte[] toBytes256() {
        return a256.toByteArray();
    }

    @Benchmark
    public byte[] bigToBytes256() {
        return r256.toByteArray();
    }

    @Benchmark
    public Wide256 fromBytes256() {
        return Wide256.fromByteArray(b256);
    }

    @Benchmark
    public BigInteger bigFromBytes256() {
        return new BigInteger(1, b256);
    }

    @Benchmark
    public byte[] toBytes512() {
        return a512.toByteArray();
    }

    @Benchmark
    public byte[] bigToBytes512() {
        return r512.toByteArray();
    }

    @Benchmark
    public Wide512 fromBytes512() {
        return Wide512.fromByteArray(b512);
    }

    @Benchmark
    public BigInteger bigFromBytes512() {
        return new BigInteger(1, b512);
    }
}
