package com.scalarwide.bench;

import com.scalarwide.Wide1024;
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

/**
 * BigInteger 桥与字符串形态的绝对口径（自有 API，非跨库对照）。
 *
 * <p>比较体系 v2（2026-10-14）：BigInteger 在性能基准中只作**本库 API 的形状**
 * 出现（{@code toBigInteger}/{@code fromBigInteger} 的签名就是它），不设任何
 * {@code big*} 对照方法；正确性对拍仍在测试套件。</p>
 *
 * <p>这批基准回答一个问题：消费者的"逃生舱"（把宽值换成 BigInteger 再取 long /
 * bitLength / 字符串）到底付了多少。加 {@code -prof gc} 同时读 B/op。</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class ConversionBench {

    public Wide256 a256;
    public Wide512 a512;
    public Wide1024 a1024;

    private BigInteger b256;
    private BigInteger b512;
    private BigInteger b1024;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        b256 = new BigInteger(256, rnd);
        b512 = new BigInteger(512, rnd);
        b1024 = new BigInteger(1024, rnd);
        a256 = Wide256.fromBigInteger(b256);
        a512 = Wide512.fromBigInteger(b512);
        a1024 = Wide1024.fromBigInteger(b1024);
    }

    @Benchmark
    public BigInteger toBigInteger256() {
        return a256.toBigInteger();
    }

    @Benchmark
    public BigInteger toBigInteger512() {
        return a512.toBigInteger();
    }

    @Benchmark
    public BigInteger toBigInteger1024() {
        return a1024.toBigInteger();
    }

    @Benchmark
    public Wide256 fromBigInteger256() {
        return Wide256.fromBigInteger(b256);
    }

    @Benchmark
    public Wide512 fromBigInteger512() {
        return Wide512.fromBigInteger(b512);
    }

    @Benchmark
    public Wide1024 fromBigInteger1024() {
        return Wide1024.fromBigInteger(b1024);
    }

    @Benchmark
    public String toDecimalString1024() {
        return a1024.toString();
    }

    @Benchmark
    public String toHexString1024() {
        return a1024.toHexString();
    }
}
