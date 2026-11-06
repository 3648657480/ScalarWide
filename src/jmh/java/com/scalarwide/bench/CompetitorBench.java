package com.scalarwide.bench;

import com.inf.long2.ints.Int1024;
import com.inf.long2.ints.Int256;
import com.scalarwide.Wide512;
import com.inf.long2.ints.Int512;
import com.scalarwide.Wide1024;
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
 * 同场景对照（比较体系 v2 第 ①类）：Long2 `com.inf.long2` 固定宽度整数。
 *
 * <p>口径：**只收同映射操作**——`multiply`（1024×1024 的低 1024 位 / 256×256 的低 256 位，
 * 两边都是模 2^N 回绕）、`add`、`and`、`hashCode`（同用途，算法不同）。比较类两库都是
 * **16 / 4 个 `final long` 字段 + 全展开**，差别在乘法内核：
 * <ul>
 *   <li>我们 `Wide1024.mul`：256 位块组合（10 块 × 256×256 内核）写入 `long[64]` scratch 再装配；</li>
 *   <li>Long2 `Int1024.multiply`：**逐列累加**（每列一个寄存器内累加器 + 多 bit 溢出计数），
 *       写进 `long[16]`，每列只落一次内存。</li>
 * </ul>
 *
 * <p>操作数由同一颗种子生成、逐肢对应（Long2 的 l0 是最高位组，我们的 l0 是最低位组）。
 * 两库在同一 JVM（JDK 25——Long2 产物为 25 字节码）上跑，交叉可比。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class CompetitorBench {

    public Wide1024 a1024;
    public Wide1024 b1024;
    public Int1024 la1024;
    public Int1024 lb1024;

    public Wide256 a256;
    public Wide256 b256;
    public Int256 la256;
    public Int256 lb256;

    public Wide512 a512;
    public Wide512 b512;
    public Int512 la512;
    public Int512 lb512;

    public byte[] fixed1024;
    public byte[] long2Fixed1024;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        long[] p = new long[32];
        long[] q = new long[8];
        for (int i = 0; i < 32; i++) {
            p[i] = rnd.nextLong();
        }
        for (int i = 0; i < 8; i++) {
            q[i] = rnd.nextLong();
        }
        // 我们：低位组在前
        a1024 = Wide1024.of(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7],
                p[8], p[9], p[10], p[11], p[12], p[13], p[14], p[15]);
        b1024 = Wide1024.of(p[16], p[17], p[18], p[19], p[20], p[21], p[22], p[23],
                p[24], p[25], p[26], p[27], p[28], p[29], p[30], p[31]);
        // Long2：高位组在前
        la1024 = Int1024.of(p[15], p[14], p[13], p[12], p[11], p[10], p[9], p[8],
                p[7], p[6], p[5], p[4], p[3], p[2], p[1], p[0]);
        lb1024 = Int1024.of(p[31], p[30], p[29], p[28], p[27], p[26], p[25], p[24],
                p[23], p[22], p[21], p[20], p[19], p[18], p[17], p[16]);

        a256 = Wide256.of(q[0], q[1], q[2], q[3]);
        b256 = Wide256.of(q[4], q[5], q[6], q[7]);
        la256 = Int256.of(q[3], q[2], q[1], q[0]);
        lb256 = Int256.of(q[7], q[6], q[5], q[4]);

        long[] s = new long[16];
        long[] u = new long[16];
        for (int i = 0; i < 16; i++) {
            s[i] = rnd.nextLong();
            u[i] = rnd.nextLong();
        }
        a512 = Wide512.of(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7]);
        b512 = Wide512.of(u[0], u[1], u[2], u[3], u[4], u[5], u[6], u[7]);
        la512 = Int512.of(s[7], s[6], s[5], s[4], s[3], s[2], s[1], s[0]);
        lb512 = Int512.of(u[7], u[6], u[5], u[4], u[3], u[2], u[1], u[0]);

        fixed1024 = a1024.toByteArrayFixed();
        long2Fixed1024 = la1024.toBytes();
    }

    // ---------------- 1024 ----------------

    @Benchmark
    public Wide1024 w1024Mul() {
        return a1024.mul(b1024);
    }

    @Benchmark
    public Int1024 l1024Mul() {
        return la1024.multiply(lb1024);
    }

    @Benchmark
    public Wide1024 w1024Add() {
        return a1024.add(b1024);
    }

    @Benchmark
    public Int1024 l1024Add() {
        return la1024.add(lb1024);
    }

    @Benchmark
    public Wide1024 w1024And() {
        return a1024.and(b1024);
    }

    @Benchmark
    public Int1024 l1024And() {
        return la1024.and(lb1024);
    }

    @Benchmark
    public int w1024Hash() {
        return a1024.hashCode();
    }

    @Benchmark
    public int l1024Hash() {
        return la1024.hashCode();
    }

    // ---------------- 256 ----------------

    @Benchmark
    public Wide256 w256Mul() {
        return a256.mul(b256);
    }

    @Benchmark
    public Int256 l256Mul() {
        return la256.multiply(lb256);
    }

    @Benchmark
    public Wide256 w256Add() {
        return a256.add(b256);
    }

    @Benchmark
    public Int256 l256Add() {
        return la256.add(lb256);
    }

    @Benchmark
    public int w256Hash() {
        return a256.hashCode();
    }

    @Benchmark
    public int l256Hash() {
        return la256.hashCode();
    }

    // ---------------- 512 ----------------

    @Benchmark
    public Wide512 w512Mul() {
        return a512.mul(b512);
    }

    @Benchmark
    public Int512 l512Mul() {
        return la512.multiply(lb512);
    }

    // ---------------- 字节（固定宽度，同映射）----------------

    @Benchmark
    public byte[] w1024ToBytes() {
        return a1024.toByteArrayFixed();
    }

    @Benchmark
    public byte[] l1024ToBytes() {
        return la1024.toBytes();
    }

    @Benchmark
    public Wide1024 w1024FromBytes() {
        return Wide1024.fromByteArray(fixed1024, 0, fixed1024.length);
    }

    @Benchmark
    public Int1024 l1024FromBytes() {
        return Int1024.of(long2Fixed1024);
    }

    // ---------------- 全项：位运算 / 一元 / 比较 / 度量 / 字符串 ----------------

    @Benchmark
    public Wide1024 w1024Sub() {
        return a1024.sub(b1024);
    }

    @Benchmark
    public Int1024 l1024Sub() {
        return la1024.subtract(lb1024);
    }

    @Benchmark
    public Wide1024 w1024Or() {
        return a1024.or(b1024);
    }

    @Benchmark
    public Int1024 l1024Or() {
        return la1024.or(lb1024);
    }

    @Benchmark
    public Wide1024 w1024Xor() {
        return a1024.xor(b1024);
    }

    @Benchmark
    public Int1024 l1024Xor() {
        return la1024.xor(lb1024);
    }

    @Benchmark
    public Wide1024 w1024Not() {
        return a1024.not();
    }

    @Benchmark
    public Int1024 l1024Not() {
        return la1024.not();
    }

    @Benchmark
    public Wide1024 w1024Neg() {
        return a1024.neg();
    }

    @Benchmark
    public Int1024 l1024Neg() {
        return la1024.negate();
    }

    @Benchmark
    public boolean w1024Equals() {
        return a1024.equals(b1024);
    }

    @Benchmark
    public boolean l1024Equals() {
        return la1024.equals(lb1024);
    }

    /** 两边都是有符号比较：我方 compareSigned 对应对方 compareTo。 */
    @Benchmark
    public int w1024Cmp() {
        return a1024.compareSigned(b1024);
    }

    @Benchmark
    public int l1024Cmp() {
        return la1024.compareTo(lb1024);
    }

    /** 左移 7：我方 shl(7) 对应对方 shift(7)。 */
    @Benchmark
    public Wide1024 w1024Shl() {
        return a1024.shl(7);
    }

    @Benchmark
    public Int1024 l1024Shl() {
        return la1024.shift(7);
    }

    /** 算术右移 7：我方 shr(7) 对应对方 shift(-7)。 */
    @Benchmark
    public Wide1024 w1024Shr() {
        return a1024.shr(7);
    }

    @Benchmark
    public Int1024 l1024Shr() {
        return la1024.shift(-7);
    }

    @Benchmark
    public int w1024BitCount() {
        return a1024.bitCount();
    }

    @Benchmark
    public int l1024BitCount() {
        return la1024.bitCount();
    }

    @Benchmark
    public int w1024BitLength() {
        return a1024.bitLength();
    }

    @Benchmark
    public int l1024BitLength() {
        return la1024.bitLength();
    }

    @Benchmark
    public long w1024ToLong() {
        return a1024.longValue();
    }

    @Benchmark
    public long l1024ToLong() {
        return la1024.toLong();
    }

    @Benchmark
    public String w1024ToHex() {
        return a1024.toHexString();
    }

    @Benchmark
    public String l1024ToHex() {
        return la1024.toHexString();
    }

    @Benchmark
    public String w1024ToStr() {
        return a1024.toString();
    }

    @Benchmark
    public String l1024ToStr() {
        return la1024.toString();
    }
}
