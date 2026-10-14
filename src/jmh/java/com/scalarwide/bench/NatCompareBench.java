package com.scalarwide.bench;

import com.scalarwide.MutableWide128;
import com.scalarwide.MutableWide256;
import com.scalarwide.Wide1024;
import com.scalarwide.Wide128;
import com.scalarwide.Wide256;
import com.scalarwide.Wide384;
import com.scalarwide.Wide512;
import com.scalarwide.Wide768;
import org.bouncycastle.math.raw.Nat;
import org.bouncycastle.math.raw.Nat128;
import org.bouncycastle.math.raw.Nat256;
import org.bouncycastle.math.raw.Nat384;
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
 * 同场景对照（issue #1 后续）：BouncyCastle 定宽 limb 算术内核（math.raw.Nat*）。
 *
 * <p>口径（与 BENCH.md《口径声明》一致——只收同映射操作）：</p>
 * <ul>
 *   <li>同域 Z/2^N：{@code add}/{@code sub} = 模 2^N，两侧同映射；</li>
 *   <li>{@code mulFull}（128/256 才有 2N 位结果类）vs BC {@code Nat*.mul}（已实测为全积写
 *       2N 字数组）——同映射；</li>
 *   <li>BC 无低半积 API，我们 384+ 无 mulFull ⇒ 半积不进本批（不造非映射对照）；</li>
 *   <li>BC 侧全部落预分配 {@code int[]}（零分配）；我们的 immutable 每 op 一个结果对象、
 *       Mutable（128/256）零分配——API 形态差异由 {@code -prof gc} 佐证，如实计账。</li>
 * </ul>
 *
 * <p>BC 用 32 位 limb、我们用 64 位 limb；BC 侧 128/256/384 走专用类，512+ 走通用
 * {@code Nat}（无 Nat512 专用类）。</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class NatCompareBench {

    public Wide128 a128;
    public Wide128 b128;
    public Wide256 a256;
    public Wide256 b256;
    public Wide384 a384;
    public Wide384 b384;
    public Wide512 a512;
    public Wide512 b512;
    public Wide768 a768;
    public Wide768 b768;
    public Wide1024 a1024;
    public Wide1024 b1024;

    public final int[] x128 = new int[4];
    public final int[] y128 = new int[4];
    public final int[] za128 = new int[4];
    public final int[] zm128 = new int[8];
    public final int[] x256 = new int[8];
    public final int[] y256 = new int[8];
    public final int[] za256 = new int[8];
    public final int[] zm256 = new int[16];
    public final int[] x384 = new int[12];
    public final int[] y384 = new int[12];
    public final int[] za384 = new int[12];
    public final int[] x512 = new int[16];
    public final int[] y512 = new int[16];
    public final int[] za512 = new int[16];
    public final int[] x768 = new int[24];
    public final int[] y768 = new int[24];
    public final int[] za768 = new int[24];
    public final int[] x1024 = new int[32];
    public final int[] y1024 = new int[32];
    public final int[] za1024 = new int[32];

    public final MutableWide128 m128 = new MutableWide128();
    public final MutableWide256 m256 = new MutableWide256();

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        long top = Long.MAX_VALUE;
        a128 = Wide128.of(rnd.nextLong(), rnd.nextLong() & top);
        b128 = Wide128.of(rnd.nextLong(), rnd.nextLong() & top);
        a256 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        b256 = Wide256.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        a384 = Wide384.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong() & top);
        b384 = Wide384.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong() & top);
        a512 = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        b512 = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        a768 = Wide768.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        b768 = Wide768.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        a1024 = Wide1024.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);
        b1024 = Wide1024.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() & top);

        fill(x128, a128.limb(0), a128.limb(1));
        fill(y128, b128.limb(0), b128.limb(1));
        fill(x256, a256.limb(0), a256.limb(1), a256.limb(2), a256.limb(3));
        fill(y256, b256.limb(0), b256.limb(1), b256.limb(2), b256.limb(3));
        fill(x384, a384.limb(0), a384.limb(1), a384.limb(2), a384.limb(3), a384.limb(4), a384.limb(5));
        fill(y384, b384.limb(0), b384.limb(1), b384.limb(2), b384.limb(3), b384.limb(4), b384.limb(5));
        fill(x512, a512.limb(0), a512.limb(1), a512.limb(2), a512.limb(3),
                a512.limb(4), a512.limb(5), a512.limb(6), a512.limb(7));
        fill(y512, b512.limb(0), b512.limb(1), b512.limb(2), b512.limb(3),
                b512.limb(4), b512.limb(5), b512.limb(6), b512.limb(7));
        fill(x768, a768.limb(0), a768.limb(1), a768.limb(2), a768.limb(3),
                a768.limb(4), a768.limb(5), a768.limb(6), a768.limb(7),
                a768.limb(8), a768.limb(9), a768.limb(10), a768.limb(11));
        fill(y768, b768.limb(0), b768.limb(1), b768.limb(2), b768.limb(3),
                b768.limb(4), b768.limb(5), b768.limb(6), b768.limb(7),
                b768.limb(8), b768.limb(9), b768.limb(10), b768.limb(11));
        fill(x1024, a1024.limb(0), a1024.limb(1), a1024.limb(2), a1024.limb(3),
                a1024.limb(4), a1024.limb(5), a1024.limb(6), a1024.limb(7),
                a1024.limb(8), a1024.limb(9), a1024.limb(10), a1024.limb(11),
                a1024.limb(12), a1024.limb(13), a1024.limb(14), a1024.limb(15));
        fill(y1024, b1024.limb(0), b1024.limb(1), b1024.limb(2), b1024.limb(3),
                b1024.limb(4), b1024.limb(5), b1024.limb(6), b1024.limb(7),
                b1024.limb(8), b1024.limb(9), b1024.limb(10), b1024.limb(11),
                b1024.limb(12), b1024.limb(13), b1024.limb(14), b1024.limb(15));
    }

    private static void fill(int[] w, long... limbs) {
        for (int i = 0; i < limbs.length; i++) {
            w[2 * i] = (int) limbs[i];
            w[2 * i + 1] = (int) (limbs[i] >>> 32);
        }
    }

    // ---- 128 ----

    @Benchmark
    public Wide128 w128Add() {
        return a128.add(b128);
    }

    @Benchmark
    public int w128AddNat() {
        Nat128.add(x128, y128, za128);
        return za128[0] + za128[3];
    }

    @Benchmark
    public long m128Add() {
        m128.add(b128);
        return m128.limb(0);
    }

    @Benchmark
    public Wide128 w128Sub() {
        return a128.sub(b128);
    }

    @Benchmark
    public int w128SubNat() {
        Nat128.sub(x128, y128, za128);
        return za128[0] + za128[3];
    }

    @Benchmark
    public Wide256 w128MulFull() {
        return a128.mulFull(b128);
    }

    @Benchmark
    public int w128MulFullNat() {
        Nat128.mul(x128, y128, zm128);
        return zm128[0] + zm128[7];
    }

    // ---- 256 ----

    @Benchmark
    public Wide256 w256Add() {
        return a256.add(b256);
    }

    @Benchmark
    public int w256AddNat() {
        Nat256.add(x256, y256, za256);
        return za256[0] + za256[7];
    }

    @Benchmark
    public long m256Add() {
        m256.add(b256);
        return m256.limb(0);
    }

    @Benchmark
    public Wide256 w256Sub() {
        return a256.sub(b256);
    }

    @Benchmark
    public int w256SubNat() {
        Nat256.sub(x256, y256, za256);
        return za256[0] + za256[7];
    }

    @Benchmark
    public Wide512 w256MulFull() {
        return a256.mulFull(b256);
    }

    @Benchmark
    public int w256MulFullNat() {
        Nat256.mul(x256, y256, zm256);
        return zm256[0] + zm256[15];
    }

    // ---- 384 ----

    @Benchmark
    public Wide384 w384Add() {
        return a384.add(b384);
    }

    @Benchmark
    public int w384AddNat() {
        Nat.add(12, x384, y384, za384);
        return za384[0] + za384[11];
    }

    @Benchmark
    public Wide384 w384Sub() {
        return a384.sub(b384);
    }

    @Benchmark
    public int w384SubNat() {
        Nat.sub(12, x384, y384, za384);
        return za384[0] + za384[11];
    }

    // ---- 512 ----

    @Benchmark
    public Wide512 w512Add() {
        return a512.add(b512);
    }

    @Benchmark
    public int w512AddNat() {
        Nat.add(16, x512, y512, za512);
        return za512[0] + za512[15];
    }

    @Benchmark
    public Wide512 w512Sub() {
        return a512.sub(b512);
    }

    @Benchmark
    public int w512SubNat() {
        Nat.sub(16, x512, y512, za512);
        return za512[0] + za512[15];
    }

    // ---- 768 ----

    @Benchmark
    public Wide768 w768Add() {
        return a768.add(b768);
    }

    @Benchmark
    public int w768AddNat() {
        Nat.add(24, x768, y768, za768);
        return za768[0] + za768[23];
    }

    @Benchmark
    public Wide768 w768Sub() {
        return a768.sub(b768);
    }

    @Benchmark
    public int w768SubNat() {
        Nat.sub(24, x768, y768, za768);
        return za768[0] + za768[23];
    }

    // ---- 1024 ----

    @Benchmark
    public Wide1024 w1024Add() {
        return a1024.add(b1024);
    }

    @Benchmark
    public int w1024AddNat() {
        Nat.add(32, x1024, y1024, za1024);
        return za1024[0] + za1024[31];
    }

    @Benchmark
    public Wide1024 w1024Sub() {
        return a1024.sub(b1024);
    }

    @Benchmark
    public int w1024SubNat() {
        Nat.sub(32, x1024, y1024, za1024);
        return za1024[0] + za1024[31];
    }
}
