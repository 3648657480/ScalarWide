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
 * Diagnostic: dense 512-bit division cost breakdown - full API vs engine with
 * fresh scratch vs engine with reused scratch (isolates pack/of and array
 * allocation shares).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class DiagDivBench {

    public Wide512 a;
    public Wide512 b;
    public java.math.BigInteger ra;
    public java.math.BigInteger rb;
    public long[] u;
    public long[] v;
    public long[] qReuse;
    public long[] rReuse;
    public Wide512[] arrA;
    public Wide512[] arrB;
    public java.math.BigInteger[] arrRa;
    public java.math.BigInteger[] arrRb;

    @Setup
    public void setup() {
        Random rnd = new Random(20261011L);
        a = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong());
        b = Wide512.of(rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong(),
                rnd.nextLong(), rnd.nextLong(), rnd.nextLong(), rnd.nextLong() | 1L);
        ra = a.toBigInteger();
        rb = b.toBigInteger();
        u = new long[] {a.limb(0), a.limb(1), a.limb(2), a.limb(3), a.limb(4), a.limb(5), a.limb(6), a.limb(7)};
        v = new long[] {b.limb(0), b.limb(1), b.limb(2), b.limb(3), b.limb(4), b.limb(5), b.limb(6), b.limb(7)};
        qReuse = new long[8];
        rReuse = new long[8];
        arrA = new Wide512[] {a};
        arrB = new Wide512[] {b};
        arrRa = new java.math.BigInteger[] {ra};
        arrRb = new java.math.BigInteger[] {rb};
    }

    @Benchmark
    public Wide512 api() {
        return a.div(b);
    }

    @Benchmark
    public java.math.BigInteger fixedBig() {
        return ra.divide(rb);
    }

    @Benchmark
    public Wide512 arrApi() {
        return arrA[0].div(arrB[0]);
    }

    @Benchmark
    public java.math.BigInteger arrBig() {
        return arrRa[0].divide(arrRb[0]);
    }

    @Benchmark
    public long engineFresh() {
        long[] q = new long[8];
        long[] r = new long[8];
        Limbs.divmod(u, v, q, r);
        return q[0] ^ r[0];
    }

    @Benchmark
    public long engineReused() {
        Limbs.divmod(u, v, qReuse, rReuse);
        return qReuse[0] ^ rReuse[0];
    }

    @Benchmark
    public long packOnly() {
        long[] p = new long[8];
        p[0] = a.limb(0);
        p[1] = a.limb(1);
        p[2] = a.limb(2);
        p[3] = a.limb(3);
        p[4] = a.limb(4);
        p[5] = a.limb(5);
        p[6] = a.limb(6);
        p[7] = a.limb(7);
        long[] o = new long[8];
        o[0] = b.limb(0);
        o[1] = b.limb(1);
        o[2] = b.limb(2);
        o[3] = b.limb(3);
        o[4] = b.limb(4);
        o[5] = b.limb(5);
        o[6] = b.limb(6);
        o[7] = b.limb(7);
        return p[7] ^ o[7];
    }
}
