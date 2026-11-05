package com.scalarwide;

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
 * 结果交付形态擂台（1024 位）。同一份 limb 数据、同一套算术，只换"操作数怎么放、结果写到哪"：
 *
 * <ul>
 *   <li>{@code *Obj}：{@code Wide1024 × Wide1024 → Wide1024}（当前 immutable API，每次分配结果对象）</li>
 *   <li>{@code *Arr}：{@code long[16] × long[16] → long[16]}（扁平操作数 + 调用方缓冲，零分配）</li>
 *   <li>{@code *ArrToObj}：{@code long[16] × long[16] → Wide1024}（扁平操作数 + 结果对象）</li>
 * </ul>
 *
 * <p>差分 {@code *Arr} 与 {@code *Obj} = "结果对象 + 字段读"的总代价；{@code *ArrToObj} 用来把
 * 这两项再拆开。目的是判断 256+ 的 add/sub/and/shl 到底是算术受限还是"分配 + 写回"受限。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class KernelFormBench {

    private static final int SHIFT = 123;

    public Wide1024 a;
    public Wide1024 b;

    public final long[] a16 = new long[16];
    public final long[] b16 = new long[16];
    public final long[] o16 = new long[16];

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        long[] v = new long[32];
        for (int i = 0; i < 32; i++) {
            v[i] = rnd.nextLong();
        }
        a = Wide1024.of(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7],
                v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15]);
        b = Wide1024.of(v[16], v[17], v[18], v[19], v[20], v[21], v[22], v[23],
                v[24], v[25], v[26], v[27], v[28], v[29], v[30], v[31]);
        for (int i = 0; i < 16; i++) {
            a16[i] = a.limb(i);
            b16[i] = b.limb(i);
        }
    }

    // ---------------- and ----------------

    @Benchmark
    public Wide1024 andObj() {
        return a.and(b);
    }

    @Benchmark
    public long andArr() {
        long[] x = a16;
        long[] y = b16;
        long[] o = o16;
        o[0] = x[0] & y[0];
        o[1] = x[1] & y[1];
        o[2] = x[2] & y[2];
        o[3] = x[3] & y[3];
        o[4] = x[4] & y[4];
        o[5] = x[5] & y[5];
        o[6] = x[6] & y[6];
        o[7] = x[7] & y[7];
        o[8] = x[8] & y[8];
        o[9] = x[9] & y[9];
        o[10] = x[10] & y[10];
        o[11] = x[11] & y[11];
        o[12] = x[12] & y[12];
        o[13] = x[13] & y[13];
        o[14] = x[14] & y[14];
        o[15] = x[15] & y[15];
        return o[0];
    }

    @Benchmark
    public Wide1024 andArrToObj() {
        long[] x = a16;
        long[] y = b16;
        return Wide1024.of(x[0] & y[0], x[1] & y[1], x[2] & y[2], x[3] & y[3],
                x[4] & y[4], x[5] & y[5], x[6] & y[6], x[7] & y[7],
                x[8] & y[8], x[9] & y[9], x[10] & y[10], x[11] & y[11],
                x[12] & y[12], x[13] & y[13], x[14] & y[14], x[15] & y[15]);
    }

    // ---------------- add ----------------

    @Benchmark
    public Wide1024 addObj() {
        return a.add(b);
    }

    @Benchmark
    public long addArr() {
        long[] x = a16;
        long[] y = b16;
        long[] o = o16;
        long r0 = x[0] + y[0];
        long c = Carry.carryOut(x[0], r0);
        long s1 = x[1] + y[1];
        long r1 = s1 + c;
        c = Carry.carryOut(x[1], s1) + Carry.carryOut(s1, r1);
        long s2 = x[2] + y[2];
        long r2 = s2 + c;
        c = Carry.carryOut(x[2], s2) + Carry.carryOut(s2, r2);
        long s3 = x[3] + y[3];
        long r3 = s3 + c;
        c = Carry.carryOut(x[3], s3) + Carry.carryOut(s3, r3);
        long s4 = x[4] + y[4];
        long r4 = s4 + c;
        c = Carry.carryOut(x[4], s4) + Carry.carryOut(s4, r4);
        long s5 = x[5] + y[5];
        long r5 = s5 + c;
        c = Carry.carryOut(x[5], s5) + Carry.carryOut(s5, r5);
        long s6 = x[6] + y[6];
        long r6 = s6 + c;
        c = Carry.carryOut(x[6], s6) + Carry.carryOut(s6, r6);
        long s7 = x[7] + y[7];
        long r7 = s7 + c;
        c = Carry.carryOut(x[7], s7) + Carry.carryOut(s7, r7);
        long s8 = x[8] + y[8];
        long r8 = s8 + c;
        c = Carry.carryOut(x[8], s8) + Carry.carryOut(s8, r8);
        long s9 = x[9] + y[9];
        long r9 = s9 + c;
        c = Carry.carryOut(x[9], s9) + Carry.carryOut(s9, r9);
        long s10 = x[10] + y[10];
        long r10 = s10 + c;
        c = Carry.carryOut(x[10], s10) + Carry.carryOut(s10, r10);
        long s11 = x[11] + y[11];
        long r11 = s11 + c;
        c = Carry.carryOut(x[11], s11) + Carry.carryOut(s11, r11);
        long s12 = x[12] + y[12];
        long r12 = s12 + c;
        c = Carry.carryOut(x[12], s12) + Carry.carryOut(s12, r12);
        long s13 = x[13] + y[13];
        long r13 = s13 + c;
        c = Carry.carryOut(x[13], s13) + Carry.carryOut(s13, r13);
        long s14 = x[14] + y[14];
        long r14 = s14 + c;
        c = Carry.carryOut(x[14], s14) + Carry.carryOut(s14, r14);
        long s15 = x[15] + y[15];
        long r15 = s15 + c;
        o[0] = r0;
        o[1] = r1;
        o[2] = r2;
        o[3] = r3;
        o[4] = r4;
        o[5] = r5;
        o[6] = r6;
        o[7] = r7;
        o[8] = r8;
        o[9] = r9;
        o[10] = r10;
        o[11] = r11;
        o[12] = r12;
        o[13] = r13;
        o[14] = r14;
        o[15] = r15;
        return o[0];
    }

    @Benchmark
    public Wide1024 addArrToObj() {
        long[] x = a16;
        long[] y = b16;
        long r0 = x[0] + y[0];
        long c = Carry.carryOut(x[0], r0);
        long s1 = x[1] + y[1];
        long r1 = s1 + c;
        c = Carry.carryOut(x[1], s1) + Carry.carryOut(s1, r1);
        long s2 = x[2] + y[2];
        long r2 = s2 + c;
        c = Carry.carryOut(x[2], s2) + Carry.carryOut(s2, r2);
        long s3 = x[3] + y[3];
        long r3 = s3 + c;
        c = Carry.carryOut(x[3], s3) + Carry.carryOut(s3, r3);
        long s4 = x[4] + y[4];
        long r4 = s4 + c;
        c = Carry.carryOut(x[4], s4) + Carry.carryOut(s4, r4);
        long s5 = x[5] + y[5];
        long r5 = s5 + c;
        c = Carry.carryOut(x[5], s5) + Carry.carryOut(s5, r5);
        long s6 = x[6] + y[6];
        long r6 = s6 + c;
        c = Carry.carryOut(x[6], s6) + Carry.carryOut(s6, r6);
        long s7 = x[7] + y[7];
        long r7 = s7 + c;
        c = Carry.carryOut(x[7], s7) + Carry.carryOut(s7, r7);
        long s8 = x[8] + y[8];
        long r8 = s8 + c;
        c = Carry.carryOut(x[8], s8) + Carry.carryOut(s8, r8);
        long s9 = x[9] + y[9];
        long r9 = s9 + c;
        c = Carry.carryOut(x[9], s9) + Carry.carryOut(s9, r9);
        long s10 = x[10] + y[10];
        long r10 = s10 + c;
        c = Carry.carryOut(x[10], s10) + Carry.carryOut(s10, r10);
        long s11 = x[11] + y[11];
        long r11 = s11 + c;
        c = Carry.carryOut(x[11], s11) + Carry.carryOut(s11, r11);
        long s12 = x[12] + y[12];
        long r12 = s12 + c;
        c = Carry.carryOut(x[12], s12) + Carry.carryOut(s12, r12);
        long s13 = x[13] + y[13];
        long r13 = s13 + c;
        c = Carry.carryOut(x[13], s13) + Carry.carryOut(s13, r13);
        long s14 = x[14] + y[14];
        long r14 = s14 + c;
        c = Carry.carryOut(x[14], s14) + Carry.carryOut(s14, r14);
        long s15 = x[15] + y[15];
        long r15 = s15 + c;
        return Wide1024.of(r0, r1, r2, r3, r4, r5, r6, r7,
                r8, r9, r10, r11, r12, r13, r14, r15);
    }

    // ---------------- shl(123) ----------------

    @Benchmark
    public Wide1024 shlObj() {
        return a.shl(SHIFT);
    }

    @Benchmark
    public long shlArr() {
        long[] x = a16;
        long[] o = o16;
        int bs = SHIFT & 63;
        int limbShift = SHIFT >>> 6;
        long inv = 64 - bs;
        long b0 = x[0] << bs;
        long b1 = (x[1] << bs) | (x[0] >>> inv);
        long b2 = (x[2] << bs) | (x[1] >>> inv);
        long b3 = (x[3] << bs) | (x[2] >>> inv);
        long b4 = (x[4] << bs) | (x[3] >>> inv);
        long b5 = (x[5] << bs) | (x[4] >>> inv);
        long b6 = (x[6] << bs) | (x[5] >>> inv);
        long b7 = (x[7] << bs) | (x[6] >>> inv);
        long b8 = (x[8] << bs) | (x[7] >>> inv);
        long b9 = (x[9] << bs) | (x[8] >>> inv);
        long b10 = (x[10] << bs) | (x[9] >>> inv);
        long b11 = (x[11] << bs) | (x[10] >>> inv);
        long b12 = (x[12] << bs) | (x[11] >>> inv);
        long b13 = (x[13] << bs) | (x[12] >>> inv);
        long b14 = (x[14] << bs) | (x[13] >>> inv);
        long b15 = (x[15] << bs) | (x[14] >>> inv);
        o[limbShift] = 0;
        o[1 + limbShift] = b0;
        o[2 + limbShift] = b1;
        o[3 + limbShift] = b2;
        o[4 + limbShift] = b3;
        o[5 + limbShift] = b4;
        o[6 + limbShift] = b5;
        o[7 + limbShift] = b6;
        o[8 + limbShift] = b7;
        o[9 + limbShift] = b8;
        o[10 + limbShift] = b9;
        o[11 + limbShift] = b10;
        o[12 + limbShift] = b11;
        o[13 + limbShift] = b12;
        o[14 + limbShift] = b13;
        o[15] = b14;
        return o[limbShift];
    }

    @Benchmark
    public Wide1024 shlArrToObj() {
        long[] x = a16;
        int bs = SHIFT & 63;
        int limbShift = SHIFT >>> 6;
        long inv = 64 - bs;
        long b0 = x[0] << bs;
        long b1 = (x[1] << bs) | (x[0] >>> inv);
        long b2 = (x[2] << bs) | (x[1] >>> inv);
        long b3 = (x[3] << bs) | (x[2] >>> inv);
        long b4 = (x[4] << bs) | (x[3] >>> inv);
        long b5 = (x[5] << bs) | (x[4] >>> inv);
        long b6 = (x[6] << bs) | (x[5] >>> inv);
        long b7 = (x[7] << bs) | (x[6] >>> inv);
        long b8 = (x[8] << bs) | (x[7] >>> inv);
        long b9 = (x[9] << bs) | (x[8] >>> inv);
        long b10 = (x[10] << bs) | (x[9] >>> inv);
        long b11 = (x[11] << bs) | (x[10] >>> inv);
        long b12 = (x[12] << bs) | (x[11] >>> inv);
        long b13 = (x[13] << bs) | (x[12] >>> inv);
        long b14 = (x[14] << bs) | (x[13] >>> inv);
        long b15 = (x[15] << bs) | (x[14] >>> inv);
        return switch (limbShift) {
            case 0 -> Wide1024.of(b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14, b15);
            case 1 -> Wide1024.of(0, b0, b1, b2, b3, b4, b5, b6, b7, b8, b9, b10, b11, b12, b13, b14);
            default -> throw new IllegalStateException();
        };
    }
}
