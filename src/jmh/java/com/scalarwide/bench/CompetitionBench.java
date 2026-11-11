package com.scalarwide.bench;

import com.scalarwide.Wide1024;
import com.scalarwide.Wide256;
import com.scalarwide.Wide512;
import com.inf.long2.ints.Int1024;
import com.inf.long2.ints.Int256;
import com.inf.long2.ints.Int512;
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
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * ScalarWide vs Long2 on the 20 shared operations, three widths.
 *
 * <p>Input pools are the same 256 values, same seed and order as {@link Precheck}, so a
 * timing row and its correctness row describe the same inputs. Each call rotates the cursor
 * one element, matching the reference report method: one target operation per call, result
 * returned to JMH.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class CompetitionBench {

    private static final int N = 256;
    private static final long SEED = 12345L;
    private static final BigInteger M64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    static BigInteger[] pool(int width, long salt) {
        Random rnd = new Random(SEED + salt);
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        BigInteger[] v = new BigInteger[N];
        for (int i = 0; i < N; i++) {
            if (i == 0) { v[i] = BigInteger.ZERO; }
            else if (i == 1) { v[i] = BigInteger.ONE; }
            else if (i == 2) { v[i] = mask; }
            else if (i == 3) { v[i] = BigInteger.ONE.shiftLeft(width - 1); }
            else { v[i] = new BigInteger(width, rnd).and(mask); }
        }
        return v;
    }

    static long[] limbs(BigInteger v, int limbs) {
        long[] out = new long[limbs];
        for (int i = 0; i < limbs; i++) {
            out[limbs - 1 - i] = v.shiftRight(64 * i).longValue();
        }
        return out;
    }

    static byte[] fixedBe(BigInteger v, int width) {
        byte[] out = new byte[width / 8];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) v.shiftRight(8 * (out.length - 1 - i)).intValue();
        }
        return out;
    }

    @State(Scope.Thread)
    public static class S256 {
        Wide256[] wa; Wide256[] wb;
        Int256[] la; Int256[] lb;
        byte[][] raw256;
        int c;

        @Setup
        public void setup() {
            BigInteger[] a = pool(256, 1);
            BigInteger[] b = pool(256, 2);
            wa = new Wide256[N]; wb = new Wide256[N];
            la = new Int256[N]; lb = new Int256[N];
            raw256 = new byte[N][];
            for (int i = 0; i < N; i++) {
                wa[i] = Wide256.fromBigInteger(a[i]);
                wb[i] = Wide256.fromBigInteger(b[i]);
                la[i] = Int256.of(limbs(a[i], 4));
                lb[i] = Int256.of(limbs(b[i], 4));
                raw256[i] = fixedBe(a[i], 256);
            }
        }
    }

    @State(Scope.Thread)
    public static class S512 {
        Wide512[] wa; Wide512[] wb;
        Int512[] la; Int512[] lb;
        byte[][] raw512;
        int c;

        @Setup
        public void setup() {
            BigInteger[] a = pool(512, 1);
            BigInteger[] b = pool(512, 2);
            wa = new Wide512[N]; wb = new Wide512[N];
            la = new Int512[N]; lb = new Int512[N];
            raw512 = new byte[N][];
            for (int i = 0; i < N; i++) {
                wa[i] = Wide512.fromBigInteger(a[i]);
                wb[i] = Wide512.fromBigInteger(b[i]);
                la[i] = Int512.of(limbs(a[i], 8));
                lb[i] = Int512.of(limbs(b[i], 8));
                raw512[i] = fixedBe(a[i], 512);
            }
        }
    }

    @State(Scope.Thread)
    public static class S1024 {
        Wide1024[] wa; Wide1024[] wb;
        Int1024[] la; Int1024[] lb;
        byte[][] raw1024;
        int c;

        @Setup
        public void setup() {
            BigInteger[] a = pool(1024, 1);
            BigInteger[] b = pool(1024, 2);
            wa = new Wide1024[N]; wb = new Wide1024[N];
            la = new Int1024[N]; lb = new Int1024[N];
            raw1024 = new byte[N][];
            for (int i = 0; i < N; i++) {
                wa[i] = Wide1024.fromBigInteger(a[i]);
                wb[i] = Wide1024.fromBigInteger(b[i]);
                la[i] = Int1024.of(limbs(a[i], 16));
                lb[i] = Int1024.of(limbs(b[i], 16));
                raw1024[i] = fixedBe(a[i], 1024);
            }
        }
    }

    @Benchmark
    public Wide256 w256_add(S256 s) { int i = s.c++ & 255; return s.wa[i].add(s.wb[i]); }

    @Benchmark
    public Int256 l256_add(S256 s) { int i = s.c++ & 255; return s.la[i].add(s.lb[i]); }

    @Benchmark
    public Wide256 w256_subtract(S256 s) { int i = s.c++ & 255; return s.wa[i].sub(s.wb[i]); }

    @Benchmark
    public Int256 l256_subtract(S256 s) { int i = s.c++ & 255; return s.la[i].subtract(s.lb[i]); }

    @Benchmark
    public Wide256 w256_multiply(S256 s) { int i = s.c++ & 255; return s.wa[i].mul(s.wb[i]); }

    @Benchmark
    public Int256 l256_multiply(S256 s) { int i = s.c++ & 255; return s.la[i].multiply(s.lb[i]); }

    @Benchmark
    public Wide256 w256_and(S256 s) { int i = s.c++ & 255; return s.wa[i].and(s.wb[i]); }

    @Benchmark
    public Int256 l256_and(S256 s) { int i = s.c++ & 255; return s.la[i].and(s.lb[i]); }

    @Benchmark
    public Wide256 w256_or(S256 s) { int i = s.c++ & 255; return s.wa[i].or(s.wb[i]); }

    @Benchmark
    public Int256 l256_or(S256 s) { int i = s.c++ & 255; return s.la[i].or(s.lb[i]); }

    @Benchmark
    public Wide256 w256_xor(S256 s) { int i = s.c++ & 255; return s.wa[i].xor(s.wb[i]); }

    @Benchmark
    public Int256 l256_xor(S256 s) { int i = s.c++ & 255; return s.la[i].xor(s.lb[i]); }

    @Benchmark
    public int w256_compareTo(S256 s) { int i = s.c++ & 255; return s.wa[i].compareSigned(s.wb[i]); }

    @Benchmark
    public int l256_compareTo(S256 s) { int i = s.c++ & 255; return s.la[i].compareTo(s.lb[i]); }

    @Benchmark
    public boolean w256_equals(S256 s) { int i = s.c++ & 255; return s.wa[i].equals(s.wb[i]); }

    @Benchmark
    public boolean l256_equals(S256 s) { int i = s.c++ & 255; return s.la[i].equals(s.lb[i]); }

    @Benchmark
    public Wide512 w512_add(S512 s) { int i = s.c++ & 255; return s.wa[i].add(s.wb[i]); }

    @Benchmark
    public Int512 l512_add(S512 s) { int i = s.c++ & 255; return s.la[i].add(s.lb[i]); }

    @Benchmark
    public Wide512 w512_subtract(S512 s) { int i = s.c++ & 255; return s.wa[i].sub(s.wb[i]); }

    @Benchmark
    public Int512 l512_subtract(S512 s) { int i = s.c++ & 255; return s.la[i].subtract(s.lb[i]); }

    @Benchmark
    public Wide512 w512_multiply(S512 s) { int i = s.c++ & 255; return s.wa[i].mul(s.wb[i]); }

    @Benchmark
    public Int512 l512_multiply(S512 s) { int i = s.c++ & 255; return s.la[i].multiply(s.lb[i]); }

    @Benchmark
    public Wide512 w512_and(S512 s) { int i = s.c++ & 255; return s.wa[i].and(s.wb[i]); }

    @Benchmark
    public Int512 l512_and(S512 s) { int i = s.c++ & 255; return s.la[i].and(s.lb[i]); }

    @Benchmark
    public Wide512 w512_or(S512 s) { int i = s.c++ & 255; return s.wa[i].or(s.wb[i]); }

    @Benchmark
    public Int512 l512_or(S512 s) { int i = s.c++ & 255; return s.la[i].or(s.lb[i]); }

    @Benchmark
    public Wide512 w512_xor(S512 s) { int i = s.c++ & 255; return s.wa[i].xor(s.wb[i]); }

    @Benchmark
    public Int512 l512_xor(S512 s) { int i = s.c++ & 255; return s.la[i].xor(s.lb[i]); }

    @Benchmark
    public int w512_compareTo(S512 s) { int i = s.c++ & 255; return s.wa[i].compareSigned(s.wb[i]); }

    @Benchmark
    public int l512_compareTo(S512 s) { int i = s.c++ & 255; return s.la[i].compareTo(s.lb[i]); }

    @Benchmark
    public boolean w512_equals(S512 s) { int i = s.c++ & 255; return s.wa[i].equals(s.wb[i]); }

    @Benchmark
    public boolean l512_equals(S512 s) { int i = s.c++ & 255; return s.la[i].equals(s.lb[i]); }

    @Benchmark
    public Wide1024 w1024_add(S1024 s) { int i = s.c++ & 255; return s.wa[i].add(s.wb[i]); }

    @Benchmark
    public Int1024 l1024_add(S1024 s) { int i = s.c++ & 255; return s.la[i].add(s.lb[i]); }

    @Benchmark
    public Wide1024 w1024_subtract(S1024 s) { int i = s.c++ & 255; return s.wa[i].sub(s.wb[i]); }

    @Benchmark
    public Int1024 l1024_subtract(S1024 s) { int i = s.c++ & 255; return s.la[i].subtract(s.lb[i]); }

    @Benchmark
    public Wide1024 w1024_multiply(S1024 s) { int i = s.c++ & 255; return s.wa[i].mul(s.wb[i]); }

    @Benchmark
    public Int1024 l1024_multiply(S1024 s) { int i = s.c++ & 255; return s.la[i].multiply(s.lb[i]); }

    @Benchmark
    public Wide1024 w1024_and(S1024 s) { int i = s.c++ & 255; return s.wa[i].and(s.wb[i]); }

    @Benchmark
    public Int1024 l1024_and(S1024 s) { int i = s.c++ & 255; return s.la[i].and(s.lb[i]); }

    @Benchmark
    public Wide1024 w1024_or(S1024 s) { int i = s.c++ & 255; return s.wa[i].or(s.wb[i]); }

    @Benchmark
    public Int1024 l1024_or(S1024 s) { int i = s.c++ & 255; return s.la[i].or(s.lb[i]); }

    @Benchmark
    public Wide1024 w1024_xor(S1024 s) { int i = s.c++ & 255; return s.wa[i].xor(s.wb[i]); }

    @Benchmark
    public Int1024 l1024_xor(S1024 s) { int i = s.c++ & 255; return s.la[i].xor(s.lb[i]); }

    @Benchmark
    public int w1024_compareTo(S1024 s) { int i = s.c++ & 255; return s.wa[i].compareSigned(s.wb[i]); }

    @Benchmark
    public int l1024_compareTo(S1024 s) { int i = s.c++ & 255; return s.la[i].compareTo(s.lb[i]); }

    @Benchmark
    public boolean w1024_equals(S1024 s) { int i = s.c++ & 255; return s.wa[i].equals(s.wb[i]); }

    @Benchmark
    public boolean l1024_equals(S1024 s) { int i = s.c++ & 255; return s.la[i].equals(s.lb[i]); }

    @Benchmark
    public Wide256 w256_not(S256 s) { int i = s.c++ & 255; return s.wa[i].not(); }

    @Benchmark
    public Int256 l256_not(S256 s) { int i = s.c++ & 255; return s.la[i].not(); }

    @Benchmark
    public Wide256 w256_negate(S256 s) { int i = s.c++ & 255; return s.wa[i].neg(); }

    @Benchmark
    public Int256 l256_negate(S256 s) { int i = s.c++ & 255; return s.la[i].negate(); }

    @Benchmark
    public Wide256 w256_shiftLeft(S256 s) { int i = s.c++ & 255; return s.wa[i].shl(7); }

    @Benchmark
    public Int256 l256_shiftLeft(S256 s) { int i = s.c++ & 255; return s.la[i].shift(7); }

    @Benchmark
    public Wide256 w256_shiftRight(S256 s) { int i = s.c++ & 255; return s.wa[i].shr(7); }

    @Benchmark
    public Int256 l256_shiftRight(S256 s) { int i = s.c++ & 255; return s.la[i].shift(-7); }

    @Benchmark
    public int w256_hashCode(S256 s) { int i = s.c++ & 255; return s.wa[i].hashCode(); }

    @Benchmark
    public int l256_hashCode(S256 s) { int i = s.c++ & 255; return s.la[i].hashCode(); }

    @Benchmark
    public byte[] w256_toBytes(S256 s) { int i = s.c++ & 255; return s.wa[i].toByteArrayFixed(); }

    @Benchmark
    public byte[] l256_toBytes(S256 s) { int i = s.c++ & 255; return s.la[i].toBytes(); }

    @Benchmark
    public String w256_toString(S256 s) { int i = s.c++ & 255; return s.wa[i].toString(); }

    @Benchmark
    public String l256_toString(S256 s) { int i = s.c++ & 255; return s.la[i].toString(); }

    @Benchmark
    public String w256_toHexString(S256 s) { int i = s.c++ & 255; return s.wa[i].toHexString(); }

    @Benchmark
    public String l256_toHexString(S256 s) { int i = s.c++ & 255; return s.la[i].toHexString(); }

    @Benchmark
    public long w256_toLong(S256 s) { int i = s.c++ & 255; return s.wa[i].longValue(); }

    @Benchmark
    public long l256_toLong(S256 s) { int i = s.c++ & 255; return s.la[i].toLong(); }

    @Benchmark
    public int w256_bitLength(S256 s) { int i = s.c++ & 255; return s.wa[i].bitLength(); }

    @Benchmark
    public int l256_bitLength(S256 s) { int i = s.c++ & 255; return s.la[i].bitLength(); }

    @Benchmark
    public Wide256 w256_ofLong(S256 s) { int i = s.c++ & 255; return Wide256.of(s.wa[i].longValue()); }

    @Benchmark
    public Int256 l256_ofLong(S256 s) { int i = s.c++ & 255; return Int256.of(s.la[i].toLong()); }

    @Benchmark
    public Wide256 w256_fromBytes(S256 s) { int i = s.c++ & 255; return Wide256.fromByteArray(s.raw256[i], 0, s.raw256[i].length); }

    @Benchmark
    public Int256 l256_fromBytes(S256 s) { int i = s.c++ & 255; return Int256.of(s.raw256[i]); }

    @Benchmark
    public Wide512 w512_not(S512 s) { int i = s.c++ & 255; return s.wa[i].not(); }

    @Benchmark
    public Int512 l512_not(S512 s) { int i = s.c++ & 255; return s.la[i].not(); }

    @Benchmark
    public Wide512 w512_negate(S512 s) { int i = s.c++ & 255; return s.wa[i].neg(); }

    @Benchmark
    public Int512 l512_negate(S512 s) { int i = s.c++ & 255; return s.la[i].negate(); }

    @Benchmark
    public Wide512 w512_shiftLeft(S512 s) { int i = s.c++ & 255; return s.wa[i].shl(7); }

    @Benchmark
    public Int512 l512_shiftLeft(S512 s) { int i = s.c++ & 255; return s.la[i].shift(7); }

    @Benchmark
    public Wide512 w512_shiftRight(S512 s) { int i = s.c++ & 255; return s.wa[i].shr(7); }

    @Benchmark
    public Int512 l512_shiftRight(S512 s) { int i = s.c++ & 255; return s.la[i].shift(-7); }

    @Benchmark
    public int w512_hashCode(S512 s) { int i = s.c++ & 255; return s.wa[i].hashCode(); }

    @Benchmark
    public int l512_hashCode(S512 s) { int i = s.c++ & 255; return s.la[i].hashCode(); }

    @Benchmark
    public byte[] w512_toBytes(S512 s) { int i = s.c++ & 255; return s.wa[i].toByteArrayFixed(); }

    @Benchmark
    public byte[] l512_toBytes(S512 s) { int i = s.c++ & 255; return s.la[i].toBytes(); }

    @Benchmark
    public String w512_toString(S512 s) { int i = s.c++ & 255; return s.wa[i].toString(); }

    @Benchmark
    public String l512_toString(S512 s) { int i = s.c++ & 255; return s.la[i].toString(); }

    @Benchmark
    public String w512_toHexString(S512 s) { int i = s.c++ & 255; return s.wa[i].toHexString(); }

    @Benchmark
    public String l512_toHexString(S512 s) { int i = s.c++ & 255; return s.la[i].toHexString(); }

    @Benchmark
    public long w512_toLong(S512 s) { int i = s.c++ & 255; return s.wa[i].longValue(); }

    @Benchmark
    public long l512_toLong(S512 s) { int i = s.c++ & 255; return s.la[i].toLong(); }

    @Benchmark
    public int w512_bitLength(S512 s) { int i = s.c++ & 255; return s.wa[i].bitLength(); }

    @Benchmark
    public int l512_bitLength(S512 s) { int i = s.c++ & 255; return s.la[i].bitLength(); }

    @Benchmark
    public Wide512 w512_ofLong(S512 s) { int i = s.c++ & 255; return Wide512.of(s.wa[i].longValue()); }

    @Benchmark
    public Int512 l512_ofLong(S512 s) { int i = s.c++ & 255; return Int512.of(s.la[i].toLong()); }

    @Benchmark
    public Wide512 w512_fromBytes(S512 s) { int i = s.c++ & 255; return Wide512.fromByteArray(s.raw512[i], 0, s.raw512[i].length); }

    @Benchmark
    public Int512 l512_fromBytes(S512 s) { int i = s.c++ & 255; return Int512.of(s.raw512[i]); }

    @Benchmark
    public Wide1024 w1024_not(S1024 s) { int i = s.c++ & 255; return s.wa[i].not(); }

    @Benchmark
    public Int1024 l1024_not(S1024 s) { int i = s.c++ & 255; return s.la[i].not(); }

    @Benchmark
    public Wide1024 w1024_negate(S1024 s) { int i = s.c++ & 255; return s.wa[i].neg(); }

    @Benchmark
    public Int1024 l1024_negate(S1024 s) { int i = s.c++ & 255; return s.la[i].negate(); }

    @Benchmark
    public Wide1024 w1024_shiftLeft(S1024 s) { int i = s.c++ & 255; return s.wa[i].shl(7); }

    @Benchmark
    public Int1024 l1024_shiftLeft(S1024 s) { int i = s.c++ & 255; return s.la[i].shift(7); }

    @Benchmark
    public Wide1024 w1024_shiftRight(S1024 s) { int i = s.c++ & 255; return s.wa[i].shr(7); }

    @Benchmark
    public Int1024 l1024_shiftRight(S1024 s) { int i = s.c++ & 255; return s.la[i].shift(-7); }

    @Benchmark
    public int w1024_hashCode(S1024 s) { int i = s.c++ & 255; return s.wa[i].hashCode(); }

    @Benchmark
    public int l1024_hashCode(S1024 s) { int i = s.c++ & 255; return s.la[i].hashCode(); }

    @Benchmark
    public byte[] w1024_toBytes(S1024 s) { int i = s.c++ & 255; return s.wa[i].toByteArrayFixed(); }

    @Benchmark
    public byte[] l1024_toBytes(S1024 s) { int i = s.c++ & 255; return s.la[i].toBytes(); }

    @Benchmark
    public String w1024_toString(S1024 s) { int i = s.c++ & 255; return s.wa[i].toString(); }

    @Benchmark
    public String l1024_toString(S1024 s) { int i = s.c++ & 255; return s.la[i].toString(); }

    @Benchmark
    public String w1024_toHexString(S1024 s) { int i = s.c++ & 255; return s.wa[i].toHexString(); }

    @Benchmark
    public String l1024_toHexString(S1024 s) { int i = s.c++ & 255; return s.la[i].toHexString(); }

    @Benchmark
    public long w1024_toLong(S1024 s) { int i = s.c++ & 255; return s.wa[i].longValue(); }

    @Benchmark
    public long l1024_toLong(S1024 s) { int i = s.c++ & 255; return s.la[i].toLong(); }

    @Benchmark
    public int w1024_bitLength(S1024 s) { int i = s.c++ & 255; return s.wa[i].bitLength(); }

    @Benchmark
    public int l1024_bitLength(S1024 s) { int i = s.c++ & 255; return s.la[i].bitLength(); }

    @Benchmark
    public Wide1024 w1024_ofLong(S1024 s) { int i = s.c++ & 255; return Wide1024.of(s.wa[i].longValue()); }

    @Benchmark
    public Int1024 l1024_ofLong(S1024 s) { int i = s.c++ & 255; return Int1024.of(s.la[i].toLong()); }

    @Benchmark
    public Wide1024 w1024_fromBytes(S1024 s) { int i = s.c++ & 255; return Wide1024.fromByteArray(s.raw1024[i], 0, s.raw1024[i].length); }

    @Benchmark
    public Int1024 l1024_fromBytes(S1024 s) { int i = s.c++ & 255; return Int1024.of(s.raw1024[i]); }

}
