package com.scalarwide.bench;

import com.scalarwide.Wide1024;
import com.scalarwide.Wide128;
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
 * Byte serialization benchmark（自有 API 绝对口径）。
 *
 * <p>比较体系 v2（2026-10-14）：BigInteger 对照已移除（仅正确性对拍；
 * 字节级往返正确性在 ByteIoTest/ConsumerApiTest 中与 BigInteger 互操作验证）。</p>
 *
 * <p>2026-11-05：补 768/1024 两档（原覆盖只到 512，大宽度的编码/解码曲线缺失）。</p>
 */
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
    public Wide768 a768;
    public Wide1024 a1024;
    public byte[] b128;
    public byte[] b256;
    public byte[] b512;
    public byte[] b768;
    public byte[] b1024;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a128 = Wide128.of(rnd.nextLong(), rnd.nextLong());
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
        b128 = a128.toByteArrayFixed();
        b256 = a256.toByteArrayFixed();
        b512 = a512.toByteArrayFixed();
        b768 = a768.toByteArrayFixed();
        b1024 = a1024.toByteArrayFixed();
    }

    @Benchmark
    public byte[] toBytes128() {
        return a128.toByteArray();
    }

    @Benchmark
    public Wide128 fromBytes128() {
        return Wide128.fromByteArray(b128);
    }

    @Benchmark
    public byte[] toBytes256() {
        return a256.toByteArray();
    }

    @Benchmark
    public Wide256 fromBytes256() {
        return Wide256.fromByteArray(b256);
    }

    @Benchmark
    public byte[] toBytes512() {
        return a512.toByteArray();
    }

    @Benchmark
    public Wide512 fromBytes512() {
        return Wide512.fromByteArray(b512);
    }

    @Benchmark
    public byte[] toBytes768() {
        return a768.toByteArray();
    }

    @Benchmark
    public Wide768 fromBytes768() {
        return Wide768.fromByteArray(b768);
    }

    @Benchmark
    public byte[] toBytes1024() {
        return a1024.toByteArray();
    }

    @Benchmark
    public Wide1024 fromBytes1024() {
        return Wide1024.fromByteArray(b1024);
    }

    @Benchmark
    public byte[] toBytesFixed1024() {
        return a1024.toByteArrayFixed();
    }
}
