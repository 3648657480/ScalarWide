package com.scalarwide;

import java.math.BigInteger;

/**
 * Fixed-width 1024-bit integer held in sixteen little-endian {@code long} limbs
 * ({@code l0} least significant .. {@code l15} most significant); the value is
 * interpreted as unsigned by default, but the bit pattern is two's complement.
 *
 * <p>All arithmetic wraps modulo 2^1024. {@link #compareTo} is unsigned and
 * consistent with {@link #equals} (suitable for map keys); use
 * {@link #compareSigned} for signed order. Shift amounts must lie in
 * {@code [0, 1024)} or {@link IllegalArgumentException} is thrown.
 *
 * <p>Current implementation delegates to the generic {@link Limbs} engine
 * (correctness first); composition from the 256-bit atom (4x256) is planned.
 */
public final class Wide1024 implements Comparable<Wide1024> {

    public static final int BITS = 1024;
    public static final int LIMBS = 16;
    public static final Wide1024 ZERO = new Wide1024(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Wide1024 ONE = new Wide1024(
            1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Wide1024 MAX_VALUE = new Wide1024(
            -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L,
            -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;
    private final long l4;
    private final long l5;
    private final long l6;
    private final long l7;
    private final long l8;
    private final long l9;
    private final long l10;
    private final long l11;
    private final long l12;
    private final long l13;
    private final long l14;
    private final long l15;

    private Wide1024(long l0, long l1, long l2, long l3, long l4, long l5, long l6, long l7,
                     long l8, long l9, long l10, long l11, long l12, long l13, long l14, long l15) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.l4 = l4;
        this.l5 = l5;
        this.l6 = l6;
        this.l7 = l7;
        this.l8 = l8;
        this.l9 = l9;
        this.l10 = l10;
        this.l11 = l11;
        this.l12 = l12;
        this.l13 = l13;
        this.l14 = l14;
        this.l15 = l15;
    }

    /** Zero-extends a {@code long} value. */
    public static Wide1024 of(long v) {
        return new Wide1024(v, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Sign-extends a {@code long} value. */
    public static Wide1024 ofSigned(long v) {
        long fill = v >> 63;
        return new Wide1024(v, fill, fill, fill, fill, fill, fill, fill,
                fill, fill, fill, fill, fill, fill, fill, fill);
    }

    public static Wide1024 of(long l0, long l1, long l2, long l3, long l4, long l5, long l6, long l7,
                              long l8, long l9, long l10, long l11, long l12, long l13, long l14, long l15) {
        return new Wide1024(l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15);
    }

    /**
     * @throws IllegalArgumentException if {@code v} is negative or does not
     *         fit in 1024 bits
     */
    public static Wide1024 fromBigInteger(BigInteger v) {
        if (v.signum() < 0) {
            throw new IllegalArgumentException("negative value: " + v);
        }
        if (v.bitLength() > BITS) {
            throw new IllegalArgumentException("value does not fit in " + BITS + " bits");
        }
        return new Wide1024(
                v.longValue(),
                v.shiftRight(64).longValue(),
                v.shiftRight(128).longValue(),
                v.shiftRight(192).longValue(),
                v.shiftRight(256).longValue(),
                v.shiftRight(320).longValue(),
                v.shiftRight(384).longValue(),
                v.shiftRight(448).longValue(),
                v.shiftRight(512).longValue(),
                v.shiftRight(576).longValue(),
                v.shiftRight(640).longValue(),
                v.shiftRight(704).longValue(),
                v.shiftRight(768).longValue(),
                v.shiftRight(832).longValue(),
                v.shiftRight(896).longValue(),
                v.shiftRight(960).longValue());
    }

    /** Unsigned value as a non-negative {@link BigInteger}. */
    public BigInteger toBigInteger() {
        return Limbs.toBigInteger(pack());
    }

    /** {@code i}-th limb, {@code 0} = least significant. */
    public long limb(int i) {
        return switch (i) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            case 4 -> l4;
            case 5 -> l5;
            case 6 -> l6;
            case 7 -> l7;
            case 8 -> l8;
            case 9 -> l9;
            case 10 -> l10;
            case 11 -> l11;
            case 12 -> l12;
            case 13 -> l13;
            case 14 -> l14;
            case 15 -> l15;
            default -> throw new IndexOutOfBoundsException("limb " + i);
        };
    }

    /** Low 64 bits. */
    public long longValue() {
        return l0;
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3 | l4 | l5 | l6 | l7 | l8 | l9 | l10 | l11 | l12 | l13 | l14 | l15) == 0;
    }

    public int bitLength() {
        if (l15 != 0) {
            return 960 + (64 - Long.numberOfLeadingZeros(l15));
        }
        if (l14 != 0) {
            return 896 + (64 - Long.numberOfLeadingZeros(l14));
        }
        if (l13 != 0) {
            return 832 + (64 - Long.numberOfLeadingZeros(l13));
        }
        if (l12 != 0) {
            return 768 + (64 - Long.numberOfLeadingZeros(l12));
        }
        if (l11 != 0) {
            return 704 + (64 - Long.numberOfLeadingZeros(l11));
        }
        if (l10 != 0) {
            return 640 + (64 - Long.numberOfLeadingZeros(l10));
        }
        if (l9 != 0) {
            return 576 + (64 - Long.numberOfLeadingZeros(l9));
        }
        if (l8 != 0) {
            return 512 + (64 - Long.numberOfLeadingZeros(l8));
        }
        if (l7 != 0) {
            return 448 + (64 - Long.numberOfLeadingZeros(l7));
        }
        if (l6 != 0) {
            return 384 + (64 - Long.numberOfLeadingZeros(l6));
        }
        if (l5 != 0) {
            return 320 + (64 - Long.numberOfLeadingZeros(l5));
        }
        if (l4 != 0) {
            return 256 + (64 - Long.numberOfLeadingZeros(l4));
        }
        if (l3 != 0) {
            return 192 + (64 - Long.numberOfLeadingZeros(l3));
        }
        if (l2 != 0) {
            return 128 + (64 - Long.numberOfLeadingZeros(l2));
        }
        if (l1 != 0) {
            return 64 + (64 - Long.numberOfLeadingZeros(l1));
        }
        if (l0 != 0) {
            return 64 - Long.numberOfLeadingZeros(l0);
        }
        return 0;
    }

    /** Wrapping addition modulo 2^1024. */
    public Wide1024 add(Wide1024 o) {
        long r0 = l0 + o.l0;
        long c = Carry.carryOut(l0, r0);
        long s1 = l1 + o.l1;
        long r1 = s1 + c;
        c = Carry.carryOut(l1, s1) + Carry.carryOut(s1, r1);
        long s2 = l2 + o.l2;
        long r2 = s2 + c;
        c = Carry.carryOut(l2, s2) + Carry.carryOut(s2, r2);
        long s3 = l3 + o.l3;
        long r3 = s3 + c;
        c = Carry.carryOut(l3, s3) + Carry.carryOut(s3, r3);
        long s4 = l4 + o.l4;
        long r4 = s4 + c;
        c = Carry.carryOut(l4, s4) + Carry.carryOut(s4, r4);
        long s5 = l5 + o.l5;
        long r5 = s5 + c;
        c = Carry.carryOut(l5, s5) + Carry.carryOut(s5, r5);
        long s6 = l6 + o.l6;
        long r6 = s6 + c;
        c = Carry.carryOut(l6, s6) + Carry.carryOut(s6, r6);
        long s7 = l7 + o.l7;
        long r7 = s7 + c;
        c = Carry.carryOut(l7, s7) + Carry.carryOut(s7, r7);
        long s8 = l8 + o.l8;
        long r8 = s8 + c;
        c = Carry.carryOut(l8, s8) + Carry.carryOut(s8, r8);
        long s9 = l9 + o.l9;
        long r9 = s9 + c;
        c = Carry.carryOut(l9, s9) + Carry.carryOut(s9, r9);
        long s10 = l10 + o.l10;
        long r10 = s10 + c;
        c = Carry.carryOut(l10, s10) + Carry.carryOut(s10, r10);
        long s11 = l11 + o.l11;
        long r11 = s11 + c;
        c = Carry.carryOut(l11, s11) + Carry.carryOut(s11, r11);
        long s12 = l12 + o.l12;
        long r12 = s12 + c;
        c = Carry.carryOut(l12, s12) + Carry.carryOut(s12, r12);
        long s13 = l13 + o.l13;
        long r13 = s13 + c;
        c = Carry.carryOut(l13, s13) + Carry.carryOut(s13, r13);
        long s14 = l14 + o.l14;
        long r14 = s14 + c;
        c = Carry.carryOut(l14, s14) + Carry.carryOut(s14, r14);
        long s15 = l15 + o.l15;
        long r15 = s15 + c;
        return new Wide1024(r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15);
    }

    /** Wrapping subtraction modulo 2^1024. */
    public Wide1024 sub(Wide1024 o) {
        long r0 = l0 - o.l0;
        long b = Carry.borrowOut(l0, r0);
        long d1 = l1 - o.l1;
        long r1 = d1 - b;
        b = Carry.borrowOut(l1, d1) + Carry.borrowOut(d1, r1);
        long d2 = l2 - o.l2;
        long r2 = d2 - b;
        b = Carry.borrowOut(l2, d2) + Carry.borrowOut(d2, r2);
        long d3 = l3 - o.l3;
        long r3 = d3 - b;
        b = Carry.borrowOut(l3, d3) + Carry.borrowOut(d3, r3);
        long d4 = l4 - o.l4;
        long r4 = d4 - b;
        b = Carry.borrowOut(l4, d4) + Carry.borrowOut(d4, r4);
        long d5 = l5 - o.l5;
        long r5 = d5 - b;
        b = Carry.borrowOut(l5, d5) + Carry.borrowOut(d5, r5);
        long d6 = l6 - o.l6;
        long r6 = d6 - b;
        b = Carry.borrowOut(l6, d6) + Carry.borrowOut(d6, r6);
        long d7 = l7 - o.l7;
        long r7 = d7 - b;
        b = Carry.borrowOut(l7, d7) + Carry.borrowOut(d7, r7);
        long d8 = l8 - o.l8;
        long r8 = d8 - b;
        b = Carry.borrowOut(l8, d8) + Carry.borrowOut(d8, r8);
        long d9 = l9 - o.l9;
        long r9 = d9 - b;
        b = Carry.borrowOut(l9, d9) + Carry.borrowOut(d9, r9);
        long d10 = l10 - o.l10;
        long r10 = d10 - b;
        b = Carry.borrowOut(l10, d10) + Carry.borrowOut(d10, r10);
        long d11 = l11 - o.l11;
        long r11 = d11 - b;
        b = Carry.borrowOut(l11, d11) + Carry.borrowOut(d11, r11);
        long d12 = l12 - o.l12;
        long r12 = d12 - b;
        b = Carry.borrowOut(l12, d12) + Carry.borrowOut(d12, r12);
        long d13 = l13 - o.l13;
        long r13 = d13 - b;
        b = Carry.borrowOut(l13, d13) + Carry.borrowOut(d13, r13);
        long d14 = l14 - o.l14;
        long r14 = d14 - b;
        b = Carry.borrowOut(l14, d14) + Carry.borrowOut(d14, r14);
        long d15 = l15 - o.l15;
        long r15 = d15 - b;
        return new Wide1024(r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15);
    }

    /** Wrapping negation modulo 2^1024. */
    public Wide1024 neg() {
        return ZERO.sub(this);
    }

    /**
     * Low 1024 bits of the 2048-bit product, i.e. {@code (a*b) mod 2^1024},
     * composed from 256-bit atom block products written into a single scratch
     * array (zero intermediate objects; limb-level assembly).
     */
    public Wide1024 mul(Wide1024 o) {
        long[] s = new long[64];
        Wide256.mulFullInto8(s, 0, l0, l1, l2, l3, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
        Wide256.mulFullInto8(s, 8, l0, l1, l2, l3, o.limb(4), o.limb(5), o.limb(6), o.limb(7));
        Wide256.mulFullInto8(s, 16, l4, l5, l6, l7, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
        Wide256.mulFullInto8(s, 24, l0, l1, l2, l3, o.limb(8), o.limb(9), o.limb(10), o.limb(11));
        Wide256.mulFullInto8(s, 32, l4, l5, l6, l7, o.limb(4), o.limb(5), o.limb(6), o.limb(7));
        Wide256.mulFullInto8(s, 40, l8, l9, l10, l11, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
        Wide256.mulLowInto8(s, 48, l0, l1, l2, l3, o.limb(12), o.limb(13), o.limb(14), o.limb(15));
        Wide256.mulLowInto8(s, 52, l4, l5, l6, l7, o.limb(8), o.limb(9), o.limb(10), o.limb(11));
        Wide256.mulLowInto8(s, 56, l8, l9, l10, l11, o.limb(4), o.limb(5), o.limb(6), o.limb(7));
        Wide256.mulLowInto8(s, 60, l12, l13, l14, l15, o.limb(0), o.limb(1), o.limb(2), o.limb(3));

        long carry = 0;
        for (int i = 4; i < 8; i++) {
            long u = s[i] + s[i + 4];
            long k = Carry.addCarryOut(s[i], s[i + 4], u);
            long v = u + s[i + 12];
            k += Carry.addCarryOut(u, s[i + 12], v);
            long w = v + carry;
            k += Carry.addCarryOut(v, carry, w);
            s[i] = w;
            carry = k;
        }
        for (int i = 8; i < 12; i++) {
            long u1 = s[i + 4] + s[i + 12];
            long k = Carry.addCarryOut(s[i + 4], s[i + 12], u1);
            long u2 = u1 + s[i + 16];
            k += Carry.addCarryOut(u1, s[i + 16], u2);
            long u3 = u2 + s[i + 24];
            k += Carry.addCarryOut(u2, s[i + 24], u3);
            long u4 = u3 + s[i + 32];
            k += Carry.addCarryOut(u3, s[i + 32], u4);
            long w = u4 + carry;
            k += Carry.addCarryOut(u4, carry, w);
            s[i] = w;
            carry = k;
        }
        for (int i = 12; i < 16; i++) {
            long u1 = s[i + 16] + s[i + 24];
            long k = Carry.addCarryOut(s[i + 16], s[i + 24], u1);
            long u2 = u1 + s[i + 32];
            k += Carry.addCarryOut(u1, s[i + 32], u2);
            long u3 = u2 + s[i + 36];
            k += Carry.addCarryOut(u2, s[i + 36], u3);
            long u4 = u3 + s[i + 40];
            k += Carry.addCarryOut(u3, s[i + 40], u4);
            long u5 = u4 + s[i + 44];
            k += Carry.addCarryOut(u4, s[i + 44], u5);
            long u6 = u5 + s[i + 48];
            k += Carry.addCarryOut(u5, s[i + 48], u6);
            long w = u6 + carry;
            k += Carry.addCarryOut(u6, carry, w);
            s[i] = w;
            carry = k;
        }
        return Wide1024.of(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7],
                s[8], s[9], s[10], s[11], s[12], s[13], s[14], s[15]);
    }

    private Wide256 block0() {
        return Wide256.of(l0, l1, l2, l3);
    }

    private Wide256 block1() {
        return Wide256.of(l4, l5, l6, l7);
    }

    private Wide256 block2() {
        return Wide256.of(l8, l9, l10, l11);
    }

    private Wide256 block3() {
        return Wide256.of(l12, l13, l14, l15);
    }

    /**
     * High 1024 bits of the 2048-bit product, i.e. {@code (a*b) >>> 1024},
     * composed from 256-bit atom block products written into a single scratch
     * array (zero intermediate objects; limb-level assembly).
     */
    public Wide1024 mulHigh(Wide1024 o) {
        long[] s = new long[128];
        Wide256.mulFullInto8(s, 0, l0, l1, l2, l3, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
        Wide256.mulFullInto8(s, 8, l0, l1, l2, l3, o.limb(4), o.limb(5), o.limb(6), o.limb(7));
        Wide256.mulFullInto8(s, 16, l4, l5, l6, l7, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
        Wide256.mulFullInto8(s, 24, l0, l1, l2, l3, o.limb(8), o.limb(9), o.limb(10), o.limb(11));
        Wide256.mulFullInto8(s, 32, l4, l5, l6, l7, o.limb(4), o.limb(5), o.limb(6), o.limb(7));
        Wide256.mulFullInto8(s, 40, l8, l9, l10, l11, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
        Wide256.mulFullInto8(s, 48, l0, l1, l2, l3, o.limb(12), o.limb(13), o.limb(14), o.limb(15));
        Wide256.mulFullInto8(s, 56, l4, l5, l6, l7, o.limb(8), o.limb(9), o.limb(10), o.limb(11));
        Wide256.mulFullInto8(s, 64, l8, l9, l10, l11, o.limb(4), o.limb(5), o.limb(6), o.limb(7));
        Wide256.mulFullInto8(s, 72, l12, l13, l14, l15, o.limb(0), o.limb(1), o.limb(2), o.limb(3));
        Wide256.mulFullInto8(s, 80, l4, l5, l6, l7, o.limb(12), o.limb(13), o.limb(14), o.limb(15));
        Wide256.mulFullInto8(s, 88, l8, l9, l10, l11, o.limb(8), o.limb(9), o.limb(10), o.limb(11));
        Wide256.mulFullInto8(s, 96, l12, l13, l14, l15, o.limb(4), o.limb(5), o.limb(6), o.limb(7));
        Wide256.mulFullInto8(s, 104, l8, l9, l10, l11, o.limb(12), o.limb(13), o.limb(14), o.limb(15));
        Wide256.mulFullInto8(s, 112, l12, l13, l14, l15, o.limb(8), o.limb(9), o.limb(10), o.limb(11));
        Wide256.mulFullInto8(s, 120, l12, l13, l14, l15, o.limb(12), o.limb(13), o.limb(14), o.limb(15));

        long carry = 0;
        for (int i = 4; i < 8; i++) {
            long u = s[i] + s[i + 4];
            long k = Carry.addCarryOut(s[i], s[i + 4], u);
            long v = u + s[i + 12];
            k += Carry.addCarryOut(u, s[i + 12], v);
            long w = v + carry;
            k += Carry.addCarryOut(v, carry, w);
            carry = k;
        }
        for (int i = 8; i < 12; i++) {
            long u1 = s[i + 4] + s[i + 12];
            long k = Carry.addCarryOut(s[i + 4], s[i + 12], u1);
            long u2 = u1 + s[i + 16];
            k += Carry.addCarryOut(u1, s[i + 16], u2);
            long u3 = u2 + s[i + 24];
            k += Carry.addCarryOut(u2, s[i + 24], u3);
            long u4 = u3 + s[i + 32];
            k += Carry.addCarryOut(u3, s[i + 32], u4);
            long w = u4 + carry;
            k += Carry.addCarryOut(u4, carry, w);
            carry = k;
        }
        for (int i = 12; i < 16; i++) {
            long u1 = s[i + 16] + s[i + 24];
            long k = Carry.addCarryOut(s[i + 16], s[i + 24], u1);
            long u2 = u1 + s[i + 32];
            k += Carry.addCarryOut(u1, s[i + 32], u2);
            long u3 = u2 + s[i + 36];
            k += Carry.addCarryOut(u2, s[i + 36], u3);
            long u4 = u3 + s[i + 44];
            k += Carry.addCarryOut(u3, s[i + 44], u4);
            long u5 = u4 + s[i + 52];
            k += Carry.addCarryOut(u4, s[i + 52], u5);
            long u6 = u5 + s[i + 60];
            k += Carry.addCarryOut(u5, s[i + 60], u6);
            long w = u6 + carry;
            k += Carry.addCarryOut(u6, carry, w);
            carry = k;
        }

        long[] r = new long[16];
        long c = carry;
        for (int j = 0; j < 4; j++) {
            long u1 = s[52 + j] + s[60 + j];
            long k = Carry.addCarryOut(s[52 + j], s[60 + j], u1);
            long u2 = u1 + s[68 + j];
            k += Carry.addCarryOut(u1, s[68 + j], u2);
            long u3 = u2 + s[76 + j];
            k += Carry.addCarryOut(u2, s[76 + j], u3);
            long u4 = u3 + s[80 + j];
            k += Carry.addCarryOut(u3, s[80 + j], u4);
            long u5 = u4 + s[88 + j];
            k += Carry.addCarryOut(u4, s[88 + j], u5);
            long u6 = u5 + s[96 + j];
            k += Carry.addCarryOut(u5, s[96 + j], u6);
            long w = u6 + c;
            k += Carry.addCarryOut(u6, c, w);
            r[j] = w;
            c = k;
        }
        for (int j = 0; j < 4; j++) {
            long u1 = s[84 + j] + s[92 + j];
            long k = Carry.addCarryOut(s[84 + j], s[92 + j], u1);
            long u2 = u1 + s[100 + j];
            k += Carry.addCarryOut(u1, s[100 + j], u2);
            long u3 = u2 + s[104 + j];
            k += Carry.addCarryOut(u2, s[104 + j], u3);
            long u4 = u3 + s[112 + j];
            k += Carry.addCarryOut(u3, s[112 + j], u4);
            long w = u4 + c;
            k += Carry.addCarryOut(u4, c, w);
            r[4 + j] = w;
            c = k;
        }
        for (int j = 0; j < 4; j++) {
            long u1 = s[108 + j] + s[116 + j];
            long k = Carry.addCarryOut(s[108 + j], s[116 + j], u1);
            long u2 = u1 + s[120 + j];
            k += Carry.addCarryOut(u1, s[120 + j], u2);
            long w = u2 + c;
            k += Carry.addCarryOut(u2, c, w);
            r[8 + j] = w;
            c = k;
        }
        for (int j = 0; j < 4; j++) {
            long w = s[124 + j] + c;
            r[12 + j] = w;
            c = Carry.addCarryOut(s[124 + j], c, w);
        }
        return Wide1024.of(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7],
                r[8], r[9], r[10], r[11], r[12], r[13], r[14], r[15]);
    }

    public Wide1024 and(Wide1024 o) {
        return new Wide1024(l0 & o.l0, l1 & o.l1, l2 & o.l2, l3 & o.l3, l4 & o.l4, l5 & o.l5,
                l6 & o.l6, l7 & o.l7, l8 & o.l8, l9 & o.l9, l10 & o.l10, l11 & o.l11,
                l12 & o.l12, l13 & o.l13, l14 & o.l14, l15 & o.l15);
    }

    public Wide1024 or(Wide1024 o) {
        return new Wide1024(l0 | o.l0, l1 | o.l1, l2 | o.l2, l3 | o.l3, l4 | o.l4, l5 | o.l5,
                l6 | o.l6, l7 | o.l7, l8 | o.l8, l9 | o.l9, l10 | o.l10, l11 | o.l11,
                l12 | o.l12, l13 | o.l13, l14 | o.l14, l15 | o.l15);
    }

    public Wide1024 xor(Wide1024 o) {
        return new Wide1024(l0 ^ o.l0, l1 ^ o.l1, l2 ^ o.l2, l3 ^ o.l3, l4 ^ o.l4, l5 ^ o.l5,
                l6 ^ o.l6, l7 ^ o.l7, l8 ^ o.l8, l9 ^ o.l9, l10 ^ o.l10, l11 ^ o.l11,
                l12 ^ o.l12, l13 ^ o.l13, l14 ^ o.l14, l15 ^ o.l15);
    }

    public Wide1024 not() {
        return new Wide1024(~l0, ~l1, ~l2, ~l3, ~l4, ~l5, ~l6, ~l7, ~l8, ~l9, ~l10, ~l11,
                ~l12, ~l13, ~l14, ~l15);
    }

    public Wide1024 andNot(Wide1024 o) {
        return new Wide1024(l0 & ~o.l0, l1 & ~o.l1, l2 & ~o.l2, l3 & ~o.l3, l4 & ~o.l4, l5 & ~o.l5,
                l6 & ~o.l6, l7 & ~o.l7, l8 & ~o.l8, l9 & ~o.l9, l10 & ~o.l10, l11 & ~o.l11,
                l12 & ~o.l12, l13 & ~o.l13, l14 & ~o.l14, l15 & ~o.l15);
    }

    /**
     * Logical left shift by {@code k} bits; bits shifted out are discarded.
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 1024)}
     */
    public Wide1024 shl(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shl(pack(), k, out);
        return unpack(out);
    }

    /**
     * Logical right shift by {@code k} bits (zero fill).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 1024)}
     */
    public Wide1024 ushr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.ushr(pack(), k, out);
        return unpack(out);
    }

    /**
     * Arithmetic right shift by {@code k} bits (sign-extending).
     *
     * @throws IllegalArgumentException if {@code k} is outside {@code [0, 1024)}
     */
    public Wide1024 shr(int k) {
        checkShift(k);
        long[] out = new long[LIMBS];
        Limbs.shr(pack(), k, out);
        return unpack(out);
    }

    public int compareUnsigned(Wide1024 o) {
        return Limbs.compareUnsigned(pack(), o.pack());
    }

    public int compareSigned(Wide1024 o) {
        return Limbs.compareSigned(pack(), o.pack());
    }

    /**
     * Unsigned comparison; consistent with {@link #equals}.
     *
     * @see #compareSigned(Wide1024)
     */
    @Override
    public int compareTo(Wide1024 o) {
        return compareUnsigned(o);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wide1024 o)) {
            return false;
        }
        return l0 == o.l0 && l1 == o.l1 && l2 == o.l2 && l3 == o.l3
                && l4 == o.l4 && l5 == o.l5 && l6 == o.l6 && l7 == o.l7
                && l8 == o.l8 && l9 == o.l9 && l10 == o.l10 && l11 == o.l11
                && l12 == o.l12 && l13 == o.l13 && l14 == o.l14 && l15 == o.l15;
    }

    @Override
    public int hashCode() {
        long h = 0x243F6A8885A308D3L;
        h = (h ^ l0) * 0x9E3779B97F4A7C15L;
        h = (h ^ l1) * 0x9E3779B97F4A7C15L;
        h = (h ^ l2) * 0x9E3779B97F4A7C15L;
        h = (h ^ l3) * 0x9E3779B97F4A7C15L;
        h = (h ^ l4) * 0x9E3779B97F4A7C15L;
        h = (h ^ l5) * 0x9E3779B97F4A7C15L;
        h = (h ^ l6) * 0x9E3779B97F4A7C15L;
        h = (h ^ l7) * 0x9E3779B97F4A7C15L;
        h = (h ^ l8) * 0x9E3779B97F4A7C15L;
        h = (h ^ l9) * 0x9E3779B97F4A7C15L;
        h = (h ^ l10) * 0x9E3779B97F4A7C15L;
        h = (h ^ l11) * 0x9E3779B97F4A7C15L;
        h = (h ^ l12) * 0x9E3779B97F4A7C15L;
        h = (h ^ l13) * 0x9E3779B97F4A7C15L;
        h = (h ^ l14) * 0x9E3779B97F4A7C15L;
        h = (h ^ l15) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (int) (h ^ (h >>> 32));
    }

    /** Unsigned decimal representation. */
    @Override
    public String toString() {
        return toBigInteger().toString();
    }

    /** Unsigned hexadecimal representation without prefix ({@code "0"} for zero). */
    public String toHexString() {
        return toBigInteger().toString(16);
    }

    private long[] pack() {
        return new long[] {
                l0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15
        };
    }

    private static Wide1024 unpack(long[] v) {
        return new Wide1024(
                v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7],
                v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15]);
    }

    private static void checkShift(int k) {
        if (k < 0 || k >= BITS) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
    }
}
