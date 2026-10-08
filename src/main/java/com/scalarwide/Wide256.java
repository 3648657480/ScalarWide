package com.scalarwide;

import java.math.BigInteger;

public final class Wide256 implements Comparable<Wide256> {

    public static final int BITS = 256;
    public static final int LIMBS = 4;
    public static final Wide256 ZERO = new Wide256(0, 0, 0, 0);
    public static final Wide256 ONE = new Wide256(1, 0, 0, 0);
    public static final Wide256 MAX_VALUE = new Wide256(-1L, -1L, -1L, -1L);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private Wide256(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    public static Wide256 of(long v) {
        return new Wide256(v, 0, 0, 0);
    }

    public static Wide256 ofSigned(long v) {
        long fill = v >> 63;
        return new Wide256(v, fill, fill, fill);
    }

    public static Wide256 of(long l0, long l1, long l2, long l3) {
        return new Wide256(l0, l1, l2, l3);
    }

    public static Wide256 fromBigInteger(BigInteger v) {
        if (v.signum() < 0) {
            throw new IllegalArgumentException("negative value: " + v);
        }
        if (v.bitLength() > BITS) {
            throw new IllegalArgumentException("value does not fit in " + BITS + " bits");
        }
        return new Wide256(
                v.longValue(),
                v.shiftRight(64).longValue(),
                v.shiftRight(128).longValue(),
                v.shiftRight(192).longValue());
    }

    public BigInteger toBigInteger() {
        return Limbs.toBigInteger(pack());
    }

    public long limb(int i) {
        return switch (i) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> throw new IndexOutOfBoundsException("limb " + i);
        };
    }

    public long longValue() {
        return l0;
    }

    public boolean isZero() {
        return (l0 | l1 | l2 | l3) == 0;
    }

    public int bitLength() {
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

    public Wide256 add(Wide256 o) {
        long r0 = l0 + o.l0;
        long c = Limbs.addCarry(l0, o.l0, r0);
        long s1 = l1 + o.l1;
        long r1 = s1 + c;
        c = Limbs.addCarry(l1, o.l1, s1) + Limbs.addCarry(s1, c, r1);
        long s2 = l2 + o.l2;
        long r2 = s2 + c;
        c = Limbs.addCarry(l2, o.l2, s2) + Limbs.addCarry(s2, c, r2);
        long r3 = l3 + o.l3 + c;
        return new Wide256(r0, r1, r2, r3);
    }

    public Wide256 sub(Wide256 o) {
        long r0 = l0 - o.l0;
        long b = Limbs.subBorrow(l0, o.l0, r0);
        long d1 = l1 - o.l1;
        long r1 = d1 - b;
        b = Limbs.subBorrow(l1, o.l1, d1) + Limbs.subBorrow(d1, b, r1);
        long d2 = l2 - o.l2;
        long r2 = d2 - b;
        b = Limbs.subBorrow(l2, o.l2, d2) + Limbs.subBorrow(d2, b, r2);
        long r3 = l3 - o.l3 - b;
        return new Wide256(r0, r1, r2, r3);
    }

    public Wide256 neg() {
        return ZERO.sub(this);
    }

    public Wide256 mul(Wide256 o) {
        long lo00 = l0 * o.l0;
        long hi00 = Math.unsignedMultiplyHigh(l0, o.l0);
        long lo01 = l0 * o.l1;
        long lo10 = l1 * o.l0;

        long t1 = hi00 + lo01;
        long k1 = Limbs.addCarry(hi00, lo01, t1);
        long c1 = t1 + lo10;
        k1 += Limbs.addCarry(t1, lo10, c1);

        long hi01 = Math.unsignedMultiplyHigh(l0, o.l1);
        long hi10 = Math.unsignedMultiplyHigh(l1, o.l0);
        long lo02 = l0 * o.l2;
        long lo11 = l1 * o.l1;
        long lo20 = l2 * o.l0;

        long t2 = hi01 + hi10;
        long k2 = Limbs.addCarry(hi01, hi10, t2);
        long t2b = t2 + lo02;
        k2 += Limbs.addCarry(t2, lo02, t2b);
        long t2c = t2b + lo11;
        k2 += Limbs.addCarry(t2b, lo11, t2c);
        long t2d = t2c + lo20;
        k2 += Limbs.addCarry(t2c, lo20, t2d);
        long c2 = t2d + k1;
        k2 += Limbs.addCarry(t2d, k1, c2);

        long hi02 = Math.unsignedMultiplyHigh(l0, o.l2);
        long hi11 = Math.unsignedMultiplyHigh(l1, o.l1);
        long hi20 = Math.unsignedMultiplyHigh(l2, o.l0);
        long lo03 = l0 * o.l3;
        long lo12 = l1 * o.l2;
        long lo21 = l2 * o.l1;
        long lo30 = l3 * o.l0;

        long t3 = hi02 + hi11;
        long k3 = Limbs.addCarry(hi02, hi11, t3);
        long t3b = t3 + hi20;
        k3 += Limbs.addCarry(t3, hi20, t3b);
        long t3c = t3b + lo03;
        k3 += Limbs.addCarry(t3b, lo03, t3c);
        long t3d = t3c + lo12;
        k3 += Limbs.addCarry(t3c, lo12, t3d);
        long t3e = t3d + lo21;
        k3 += Limbs.addCarry(t3d, lo21, t3e);
        long t3f = t3e + lo30;
        k3 += Limbs.addCarry(t3e, lo30, t3f);
        long c3 = t3f + k2;

        return new Wide256(lo00, c1, c2, c3);
    }

    public Wide256 mulHigh(Wide256 o) {
        long hi00 = Math.unsignedMultiplyHigh(l0, o.l0);
        long lo01 = l0 * o.l1;
        long lo10 = l1 * o.l0;
        long t1 = hi00 + lo01;
        long k1 = Limbs.addCarry(hi00, lo01, t1);
        long t1b = t1 + lo10;
        k1 += Limbs.addCarry(t1, lo10, t1b);

        long hi01 = Math.unsignedMultiplyHigh(l0, o.l1);
        long hi10 = Math.unsignedMultiplyHigh(l1, o.l0);
        long lo02 = l0 * o.l2;
        long lo11 = l1 * o.l1;
        long lo20 = l2 * o.l0;
        long t2 = hi01 + hi10;
        long k2 = Limbs.addCarry(hi01, hi10, t2);
        long t2b = t2 + lo02;
        k2 += Limbs.addCarry(t2, lo02, t2b);
        long t2c = t2b + lo11;
        k2 += Limbs.addCarry(t2b, lo11, t2c);
        long t2d = t2c + lo20;
        k2 += Limbs.addCarry(t2c, lo20, t2d);
        long t2e = t2d + k1;
        k2 += Limbs.addCarry(t2d, k1, t2e);

        long hi02 = Math.unsignedMultiplyHigh(l0, o.l2);
        long hi11 = Math.unsignedMultiplyHigh(l1, o.l1);
        long hi20 = Math.unsignedMultiplyHigh(l2, o.l0);
        long lo03 = l0 * o.l3;
        long lo12 = l1 * o.l2;
        long lo21 = l2 * o.l1;
        long lo30 = l3 * o.l0;
        long t3 = hi02 + hi11;
        long k3 = Limbs.addCarry(hi02, hi11, t3);
        long t3b = t3 + hi20;
        k3 += Limbs.addCarry(t3, hi20, t3b);
        long t3c = t3b + lo03;
        k3 += Limbs.addCarry(t3b, lo03, t3c);
        long t3d = t3c + lo12;
        k3 += Limbs.addCarry(t3c, lo12, t3d);
        long t3e = t3d + lo21;
        k3 += Limbs.addCarry(t3d, lo21, t3e);
        long t3f = t3e + lo30;
        k3 += Limbs.addCarry(t3e, lo30, t3f);
        long t3g = t3f + k2;
        k3 += Limbs.addCarry(t3f, k2, t3g);

        long hi03 = Math.unsignedMultiplyHigh(l0, o.l3);
        long hi12 = Math.unsignedMultiplyHigh(l1, o.l2);
        long hi21 = Math.unsignedMultiplyHigh(l2, o.l1);
        long hi30 = Math.unsignedMultiplyHigh(l3, o.l0);
        long lo13 = l1 * o.l3;
        long lo22 = l2 * o.l2;
        long lo31 = l3 * o.l1;
        long t4 = hi03 + hi12;
        long k4 = Limbs.addCarry(hi03, hi12, t4);
        long t4b = t4 + hi21;
        k4 += Limbs.addCarry(t4, hi21, t4b);
        long t4c = t4b + hi30;
        k4 += Limbs.addCarry(t4b, hi30, t4c);
        long t4d = t4c + lo13;
        k4 += Limbs.addCarry(t4c, lo13, t4d);
        long t4e = t4d + lo22;
        k4 += Limbs.addCarry(t4d, lo22, t4e);
        long t4f = t4e + lo31;
        k4 += Limbs.addCarry(t4e, lo31, t4f);
        long c4 = t4f + k3;
        k4 += Limbs.addCarry(t4f, k3, c4);

        long hi13 = Math.unsignedMultiplyHigh(l1, o.l3);
        long hi22 = Math.unsignedMultiplyHigh(l2, o.l2);
        long hi31 = Math.unsignedMultiplyHigh(l3, o.l1);
        long lo23 = l2 * o.l3;
        long lo32 = l3 * o.l2;
        long t5 = hi13 + hi22;
        long k5 = Limbs.addCarry(hi13, hi22, t5);
        long t5b = t5 + hi31;
        k5 += Limbs.addCarry(t5, hi31, t5b);
        long t5c = t5b + lo23;
        k5 += Limbs.addCarry(t5b, lo23, t5c);
        long t5d = t5c + lo32;
        k5 += Limbs.addCarry(t5c, lo32, t5d);
        long c5 = t5d + k4;
        k5 += Limbs.addCarry(t5d, k4, c5);

        long hi23 = Math.unsignedMultiplyHigh(l2, o.l3);
        long hi32 = Math.unsignedMultiplyHigh(l3, o.l2);
        long lo33 = l3 * o.l3;
        long t6 = hi23 + hi32;
        long k6 = Limbs.addCarry(hi23, hi32, t6);
        long t6b = t6 + lo33;
        k6 += Limbs.addCarry(t6, lo33, t6b);
        long c6 = t6b + k5;
        k6 += Limbs.addCarry(t6b, k5, c6);

        long hi33 = Math.unsignedMultiplyHigh(l3, o.l3);
        long c7 = hi33 + k6;

        return new Wide256(c4, c5, c6, c7);
    }

    public Wide256 and(Wide256 o) {
        return new Wide256(l0 & o.l0, l1 & o.l1, l2 & o.l2, l3 & o.l3);
    }

    public Wide256 or(Wide256 o) {
        return new Wide256(l0 | o.l0, l1 | o.l1, l2 | o.l2, l3 | o.l3);
    }

    public Wide256 xor(Wide256 o) {
        return new Wide256(l0 ^ o.l0, l1 ^ o.l1, l2 ^ o.l2, l3 ^ o.l3);
    }

    public Wide256 not() {
        return new Wide256(~l0, ~l1, ~l2, ~l3);
    }

    public Wide256 andNot(Wide256 o) {
        return new Wide256(l0 & ~o.l0, l1 & ~o.l1, l2 & ~o.l2, l3 & ~o.l3);
    }

    public Wide256 shl(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        int ls = k >>> 6;
        int bs = k & 63;
        if (bs == 0) {
            return switch (ls) {
                case 1 -> new Wide256(0, l0, l1, l2);
                case 2 -> new Wide256(0, 0, l0, l1);
                default -> new Wide256(0, 0, 0, l0);
            };
        }
        long inv = 64 - bs;
        return switch (ls) {
            case 0 -> new Wide256(
                    l0 << bs,
                    (l1 << bs) | (l0 >>> inv),
                    (l2 << bs) | (l1 >>> inv),
                    (l3 << bs) | (l2 >>> inv));
            case 1 -> new Wide256(
                    0,
                    l0 << bs,
                    (l1 << bs) | (l0 >>> inv),
                    (l2 << bs) | (l1 >>> inv));
            case 2 -> new Wide256(
                    0,
                    0,
                    l0 << bs,
                    (l1 << bs) | (l0 >>> inv));
            default -> new Wide256(0, 0, 0, l0 << bs);
        };
    }

    public Wide256 ushr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        int ls = k >>> 6;
        int bs = k & 63;
        if (bs == 0) {
            return switch (ls) {
                case 1 -> new Wide256(l1, l2, l3, 0);
                case 2 -> new Wide256(l2, l3, 0, 0);
                default -> new Wide256(l3, 0, 0, 0);
            };
        }
        long inv = 64 - bs;
        return switch (ls) {
            case 0 -> new Wide256(
                    (l0 >>> bs) | (l1 << inv),
                    (l1 >>> bs) | (l2 << inv),
                    (l2 >>> bs) | (l3 << inv),
                    l3 >>> bs);
            case 1 -> new Wide256(
                    (l1 >>> bs) | (l2 << inv),
                    (l2 >>> bs) | (l3 << inv),
                    l3 >>> bs,
                    0);
            case 2 -> new Wide256(
                    (l2 >>> bs) | (l3 << inv),
                    l3 >>> bs,
                    0,
                    0);
            default -> new Wide256(l3 >>> bs, 0, 0, 0);
        };
    }

    public Wide256 shr(int k) {
        checkShift(k);
        if (k == 0) {
            return this;
        }
        int ls = k >>> 6;
        int bs = k & 63;
        long fill = l3 >> 63;
        if (bs == 0) {
            return switch (ls) {
                case 1 -> new Wide256(l1, l2, l3, fill);
                case 2 -> new Wide256(l2, l3, fill, fill);
                default -> new Wide256(l3, fill, fill, fill);
            };
        }
        long inv = 64 - bs;
        return switch (ls) {
            case 0 -> new Wide256(
                    (l0 >>> bs) | (l1 << inv),
                    (l1 >>> bs) | (l2 << inv),
                    (l2 >>> bs) | (l3 << inv),
                    (l3 >>> bs) | (fill << inv));
            case 1 -> new Wide256(
                    (l1 >>> bs) | (l2 << inv),
                    (l2 >>> bs) | (l3 << inv),
                    (l3 >>> bs) | (fill << inv),
                    fill);
            case 2 -> new Wide256(
                    (l2 >>> bs) | (l3 << inv),
                    (l3 >>> bs) | (fill << inv),
                    fill,
                    fill);
            default -> new Wide256((l3 >>> bs) | (fill << inv), fill, fill, fill);
        };
    }

    public int compareUnsigned(Wide256 o) {
        int c = Long.compareUnsigned(l3, o.l3);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l2, o.l2);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l1, o.l1);
        return c != 0 ? c : Long.compareUnsigned(l0, o.l0);
    }

    public int compareSigned(Wide256 o) {
        int c = Long.compare(l3, o.l3);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l2, o.l2);
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l1, o.l1);
        return c != 0 ? c : Long.compareUnsigned(l0, o.l0);
    }

    @Override
    public int compareTo(Wide256 o) {
        return compareUnsigned(o);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wide256 o)) {
            return false;
        }
        return l0 == o.l0 && l1 == o.l1 && l2 == o.l2 && l3 == o.l3;
    }

    @Override
    public int hashCode() {
        long h = 0x243F6A8885A308D3L;
        h = (h ^ l0) * 0x9E3779B97F4A7C15L;
        h = (h ^ l1) * 0x9E3779B97F4A7C15L;
        h = (h ^ l2) * 0x9E3779B97F4A7C15L;
        h = (h ^ l3) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (int) (h ^ (h >>> 32));
    }

    @Override
    public String toString() {
        return toBigInteger().toString();
    }

    public String toHexString() {
        return toBigInteger().toString(16);
    }

    private long[] pack() {
        return new long[] { l0, l1, l2, l3 };
    }

    private static void checkShift(int k) {
        if (k < 0 || k >= BITS) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
    }
}
