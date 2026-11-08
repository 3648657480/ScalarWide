package com.scalarwide.bench;

import com.scalarwide.Wide1024;
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

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 批量重叠探针：{@code mulHigh} 的 1 / 2 / 4 条链交错。
 *
 * <p>读法：{@code @OperationsPerInvocation(k)} 把时间归一成 ns/**结果**。
 * <ul>
 *   <li>若各条链完全串行（零重叠）→ ns/结果 与 k 无关（恒定）；</li>
 *   <li>若吞吐受限（链能重叠）→ ns/结果 随 k 下降，最多降到 1/k。</li>
 * </ul>
 * 因此 {@code mh4} 相对 {@code mh1} 的下降幅度 = **可兑现的重叠收益**，不是猜的。
 *
 * <p>锚点：{@code mhProd} 调生产实现（已展开内层），值约 299 ns。交错版要真的可用，
 * 必须**低于这个锚点**，而不只是低于 {@code mh1Loop}（循环版单链本身可能更慢）。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class BatchOverlapBench {

    private static final int L = 16;

    public Wide1024 pa;
    public Wide1024 pb;

    public final long[][] x = new long[4][L];
    public final long[][] y = new long[4][L];
    public final long[][] acc = new long[4][2 * L];

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        for (int k = 0; k < 4; k++) {
            for (int i = 0; i < L; i++) {
                x[k][i] = rnd.nextLong();
                y[k][i] = rnd.nextLong();
            }
        }
        pa = Wide1024.of(x[0][0], x[0][1], x[0][2], x[0][3], x[0][4], x[0][5], x[0][6], x[0][7],
                x[0][8], x[0][9], x[0][10], x[0][11], x[0][12], x[0][13], x[0][14], x[0][15]);
        pb = Wide1024.of(y[0][0], y[0][1], y[0][2], y[0][3], y[0][4], y[0][5], y[0][6], y[0][7],
                y[0][8], y[0][9], y[0][10], y[0][11], y[0][12], y[0][13], y[0][14], y[0][15]);
    }

    /** 一个乘积步：把 ai*bj 累进 acc[k]，回传新的进位。 */
    private static long step(long ai, long bj, long[] a, int k, long carry) {
        long lo = ai * bj;
        long hi = Math.unsignedMultiplyHigh(ai, bj);
        long t = a[k] + carry;
        long c1 = Long.compareUnsigned(t, a[k]) < 0 ? 1 : 0;
        long v = t + lo;
        long c2 = Long.compareUnsigned(v, t) < 0 ? 1 : 0;
        a[k] = v;
        return hi + c1 + c2;
    }

    /** 单链（k=1）：这是"零重叠"的参考形态。 */
    @Benchmark
    @OperationsPerInvocation(1)
    public long mh1() {
        long[] a = x[0];
        long[] b = y[0];
        long[] s = acc[0];
        java.util.Arrays.fill(s, 0L);
        for (int i = 0; i < L; i++) {
            long ai = a[i];
            long c = 0;
            for (int j = 0; j < L; j++) {
                c = step(ai, b[j], s, i + j, c);
            }
            s[i + L] = c;
        }
        return s[L] ^ s[L + 1];
    }

    /** 两链交错（k=2）。 */
    @Benchmark
    @OperationsPerInvocation(2)
    public long mh2() {
        long[] a0 = x[0];
        long[] b0 = y[0];
        long[] s0 = acc[0];
        long[] a1 = x[1];
        long[] b1 = y[1];
        long[] s1 = acc[1];
        java.util.Arrays.fill(s0, 0L);
        java.util.Arrays.fill(s1, 0L);
        for (int i = 0; i < L; i++) {
            long q0 = a0[i];
            long q1 = a1[i];
            long c0 = 0;
            long c1 = 0;
            for (int j = 0; j < L; j++) {
                c0 = step(q0, b0[j], s0, i + j, c0);
                c1 = step(q1, b1[j], s1, i + j, c1);
            }
            s0[i + L] = c0;
            s1[i + L] = c1;
        }
        return (s0[L] ^ s0[L + 1]) + (s1[L] ^ s1[L + 1]);
    }

    /** 四链交错（k=4）：最激进的形态。 */
    @Benchmark
    @OperationsPerInvocation(4)
    public long mh4() {
        long[] a0 = x[0];
        long[] b0 = y[0];
        long[] s0 = acc[0];
        long[] a1 = x[1];
        long[] b1 = y[1];
        long[] s1 = acc[1];
        long[] a2 = x[2];
        long[] b2 = y[2];
        long[] s2 = acc[2];
        long[] a3 = x[3];
        long[] b3 = y[3];
        long[] s3 = acc[3];
        java.util.Arrays.fill(s0, 0L);
        java.util.Arrays.fill(s1, 0L);
        java.util.Arrays.fill(s2, 0L);
        java.util.Arrays.fill(s3, 0L);
        for (int i = 0; i < L; i++) {
            long q0 = a0[i];
            long q1 = a1[i];
            long q2 = a2[i];
            long q3 = a3[i];
            long c0 = 0;
            long c1 = 0;
            long c2 = 0;
            long c3 = 0;
            for (int j = 0; j < L; j++) {
                c0 = step(q0, b0[j], s0, i + j, c0);
                c1 = step(q1, b1[j], s1, i + j, c1);
                c2 = step(q2, b2[j], s2, i + j, c2);
                c3 = step(q3, b3[j], s3, i + j, c3);
            }
            s0[i + L] = c0;
            s1[i + L] = c1;
            s2[i + L] = c2;
            s3[i + L] = c3;
        }
        return (s0[L] ^ s0[L + 1]) + (s1[L] ^ s1[L + 1])
                + (s2[L] ^ s2[L + 1]) + (s3[L] ^ s3[L + 1]);
    }

    /** 生产实现的绝对锚点（已展开内层，约 299 ns）。 */
    @Benchmark
    public long mhProd() {
        return pa.mulHigh(pb).limb(0);
    }
}
