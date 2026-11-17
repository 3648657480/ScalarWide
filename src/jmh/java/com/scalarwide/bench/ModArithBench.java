package com.scalarwide.bench;

import com.scalarwide.main.integer.ModArith;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 模运算基准（E3）：{@link ModArith} 的 `mulMod` / `powMod` 在 4 / 6 / 9 肢上的 ns/op，
 * 外加 Montgomery 原语（`mulMont` / `toMont` / `fromMont` / `n0inv`）。
 *
 * <p>口径：肢数 = 数组长度（4 = 256、6 = 384、9 = 576 位，9 肢即 ECC P-521 的载体宽度）；
 * 模数为全宽奇模数（顶肢最高位为 1），被乘数 / 底数取全宽但顶肢最高位为 0，
 * 因此天然 {@code a, b < m}——这正是 `mulMod` / `mulMont` 契约要求的前置条件。
 * 指数取全宽随机（`powMod` 的最坏情形：全部位都参与）。
 * 各基准把结果写进 setup 里预分配的 `out`，基准内不发生结果分配；
 * `mulMod` / `powMod` 自身的临时数组仍按实现分配（`-prof gc` 记录）。
 * 固定种子 12345（仓规）。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class ModArithBench {

    @Param({"4", "6", "9"})
    public int limbs;

    public long[] a;
    public long[] b;
    public long[] odd;
    public long[] even;
    public long[] exp;
    public long[] aMont;
    public long[] bMont;
    public long[] out;

    @Setup
    public void setup() {
        Random rnd = new Random(12345L);
        a = randomBelow(rnd, limbs);
        b = randomBelow(rnd, limbs);
        odd = oddModulus(rnd, limbs);
        even = oddModulus(rnd, limbs);
        even[0] &= ~1L;
        exp = new long[limbs];
        for (int i = 0; i < limbs; i++) {
            exp[i] = rnd.nextLong();
        }
        exp[limbs - 1] |= 1L;
        out = new long[limbs];
        aMont = new long[limbs];
        bMont = new long[limbs];
        ModArith.toMont(a, odd, aMont);
        ModArith.toMont(b, odd, bMont);
    }

    private static long[] randomBelow(Random rnd, int len) {
        long[] v = new long[len];
        for (int i = 0; i < len; i++) {
            v[i] = rnd.nextLong();
        }
        v[len - 1] &= Long.MAX_VALUE;
        return v;
    }

    private static long[] oddModulus(Random rnd, int len) {
        long[] m = new long[len];
        for (int i = 0; i < len; i++) {
            m[i] = rnd.nextLong();
        }
        m[len - 1] |= 1L << 63;
        m[0] |= 1L;
        return m;
    }

    @Benchmark
    public void mulMod(Blackhole bh) {
        ModArith.mulMod(a, b, odd, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void mulModEven(Blackhole bh) {
        ModArith.mulMod(a, b, even, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void mulMont(Blackhole bh) {
        ModArith.mulMont(aMont, bMont, odd, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void toMont(Blackhole bh) {
        ModArith.toMont(a, odd, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void fromMont(Blackhole bh) {
        ModArith.fromMont(aMont, odd, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public long n0inv() {
        return ModArith.n0inv(odd);
    }

    @Benchmark
    public void powMod(Blackhole bh) {
        ModArith.powMod(a, exp, odd, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void powModCt(Blackhole bh) {
        ModArith.powModCt(a, exp, odd, out);
        bh.consume(out[0]);
    }
}
