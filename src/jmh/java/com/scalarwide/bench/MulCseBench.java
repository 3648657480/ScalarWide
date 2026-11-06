package com.scalarwide.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * 探针：{@code a*b}（低半）与 {@code Math.unsignedMultiplyHigh(a,b)}（高半）是否合成一条 {@code mulx}。
 *
 * <p><b>反常量折叠</b>：操作数每轮自变（{@code aa += K} / {@code bb ^= K}），且与累加器 {@code s}
 * 无关，所以既不能被 C2 常量折叠，也不能被 CSE；乘法的操作数组合每轮都不同。
 * 前两版用"只读数组 + 末尾写回"，两次都被整个折掉（`loOnly` 与 `addOnly` 测出同一个数）。
 *
 * <p><b>灵敏度对照</b>：{@code twoLo} 把**同一个表达式写两遍**——CSE 必然合并，所以它必须与
 * {@code loOnly} 持平；若它明显变慢，说明本探针测不出 CSE，结论作废。
 *
 * <p>读法（每 N 次归一）：{@code both} ≈ {@code loOnly} ⇒ 已合成；{@code both} ≈
 * {@code loOnly + hiOnly − addOnly} ⇒ 没合成。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class MulCseBench {

    private static final int N = 256;
    private static final long KA = 0x9E3779B97F4A7C15L;
    private static final long KB = 0xBF58476D1CE4E5B9L;

    private long startA;
    private long startB;

    @Setup
    public void setup() {
        startA = 0x243F6A8885A308D3L;
        startB = 0x13198A2E03707344L;
    }

    @Benchmark
    @OperationsPerInvocation(N)
    public long addOnly() {
        long s = 0;
        long aa = startA;
        long bb = startB;
        for (int i = 0; i < N; i++) {
            s += aa + bb;
            aa += KA;
            bb ^= KB;
        }
        startA = s;
        return s;
    }

    @Benchmark
    @OperationsPerInvocation(N)
    public long loOnly() {
        long s = 0;
        long aa = startA;
        long bb = startB;
        for (int i = 0; i < N; i++) {
            s += aa * bb;
            aa += KA;
            bb ^= KB;
        }
        startA = s;
        return s;
    }

    @Benchmark
    @OperationsPerInvocation(N)
    public long hiOnly() {
        long s = 0;
        long aa = startA;
        long bb = startB;
        for (int i = 0; i < N; i++) {
            s += Math.unsignedMultiplyHigh(aa, bb);
            aa += KA;
            bb ^= KB;
        }
        startA = s;
        return s;
    }

    @Benchmark
    @OperationsPerInvocation(N)
    public long both() {
        long s = 0;
        long t = 0;
        long aa = startA;
        long bb = startB;
        for (int i = 0; i < N; i++) {
            s += aa * bb;
            t += Math.unsignedMultiplyHigh(aa, bb);
            aa += KA;
            bb ^= KB;
        }
        startA = s ^ t;
        return s ^ t;
    }

    /** 灵敏度对照：同一表达式两遍，CSE 必须合并 ⇒ 应与 {@code loOnly} 持平。 */
    @Benchmark
    @OperationsPerInvocation(N)
    public long twoLo() {
        long s = 0;
        long t = 0;
        long aa = startA;
        long bb = startB;
        for (int i = 0; i < N; i++) {
            long p = aa * bb;
            s += p;
            t += p;
            aa += KA;
            bb ^= KB;
        }
        startA = s ^ t;
        return s ^ t;
    }
}
