package com.scalarwide;

/**
 * Reusable mutable 256-bit accumulator for transient arithmetic: every
 * operation writes into this instance and allocates nothing. Not thread-safe.
 * Never use as a map key - keys must be immutable (see {@link Wide256}).
 */
public final class MutableWide256 {

    private long l0;
    private long l1;
    private long l2;
    private long l3;

    public MutableWide256 setZero() {
        l0 = 0;
        l1 = 0;
        l2 = 0;
        l3 = 0;
        return this;
    }

    public MutableWide256 set(Wide256 v) {
        l0 = v.limb(0);
        l1 = v.limb(1);
        l2 = v.limb(2);
        l3 = v.limb(3);
        return this;
    }

    public MutableWide256 set(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        return this;
    }

    public MutableWide256 add(Wide256 o) {
        long o0 = o.limb(0);
        long o1 = o.limb(1);
        long o2 = o.limb(2);
        long o3 = o.limb(3);
        long r0 = l0 + o0;
        long c = Carry.carryOut(l0, r0);
        long s1 = l1 + o1;
        long r1 = s1 + c;
        c = Carry.carryOut(l1, s1) + Carry.carryOut(s1, r1);
        long s2 = l2 + o2;
        long r2 = s2 + c;
        c = Carry.carryOut(l2, s2) + Carry.carryOut(s2, r2);
        long r3 = l3 + o3 + c;
        l0 = r0;
        l1 = r1;
        l2 = r2;
        l3 = r3;
        return this;
    }

    public MutableWide256 sub(Wide256 o) {
        long o0 = o.limb(0);
        long o1 = o.limb(1);
        long o2 = o.limb(2);
        long o3 = o.limb(3);
        long r0 = l0 - o0;
        long b = Carry.borrowOut(l0, r0);
        long d1 = l1 - o1;
        long r1 = d1 - b;
        b = Carry.borrowOut(l1, d1) + Carry.borrowOut(d1, r1);
        long d2 = l2 - o2;
        long r2 = d2 - b;
        b = Carry.borrowOut(l2, d2) + Carry.borrowOut(d2, r2);
        long r3 = l3 - o3 - b;
        l0 = r0;
        l1 = r1;
        l2 = r2;
        l3 = r3;
        return this;
    }

    public MutableWide256 mul(Wide256 o) {
        long o0 = o.limb(0);
        long o1 = o.limb(1);
        long o2 = o.limb(2);
        long o3 = o.limb(3);
        long c0 = l0 * o0;
        long hi00 = Math.unsignedMultiplyHigh(l0, o0);
        long lo01 = l0 * o1;
        long lo10 = l1 * o0;
        long t1 = hi00 + lo01;
        long k1 = Carry.carryOut(hi00, t1);
        long c1 = t1 + lo10;
        k1 += Carry.carryOut(t1, c1);
        long hi01 = Math.unsignedMultiplyHigh(l0, o1);
        long hi10 = Math.unsignedMultiplyHigh(l1, o0);
        long lo02 = l0 * o2;
        long lo11 = l1 * o1;
        long lo20 = l2 * o0;
        long t2 = hi01 + hi10;
        long k2 = Carry.carryOut(hi01, t2);
        long t2b = t2 + lo02;
        k2 += Carry.carryOut(t2, t2b);
        long t2c = t2b + lo11;
        k2 += Carry.carryOut(t2b, t2c);
        long t2d = t2c + lo20;
        k2 += Carry.carryOut(t2c, t2d);
        long c2 = t2d + k1;
        k2 += Carry.carryOut(t2d, c2);
        long hi02 = Math.unsignedMultiplyHigh(l0, o2);
        long hi11 = Math.unsignedMultiplyHigh(l1, o1);
        long hi20 = Math.unsignedMultiplyHigh(l2, o0);
        long lo03 = l0 * o3;
        long lo12 = l1 * o2;
        long lo21 = l2 * o1;
        long lo30 = l3 * o0;
        long t3 = hi02 + hi11;
        long k3 = Carry.carryOut(hi02, t3);
        long t3b = t3 + hi20;
        k3 += Carry.carryOut(t3, t3b);
        long t3c = t3b + lo03;
        k3 += Carry.carryOut(t3b, t3c);
        long t3d = t3c + lo12;
        k3 += Carry.carryOut(t3c, t3d);
        long t3e = t3d + lo21;
        k3 += Carry.carryOut(t3d, t3e);
        long t3f = t3e + lo30;
        k3 += Carry.carryOut(t3e, t3f);
        long c3 = t3f + k2;
        l0 = c0;
        l1 = c1;
        l2 = c2;
        l3 = c3;
        return this;
    }

    public MutableWide256 and(Wide256 o) {
        l0 &= o.limb(0);
        l1 &= o.limb(1);
        l2 &= o.limb(2);
        l3 &= o.limb(3);
        return this;
    }

    public MutableWide256 or(Wide256 o) {
        l0 |= o.limb(0);
        l1 |= o.limb(1);
        l2 |= o.limb(2);
        l3 |= o.limb(3);
        return this;
    }

    public MutableWide256 xor(Wide256 o) {
        l0 ^= o.limb(0);
        l1 ^= o.limb(1);
        l2 ^= o.limb(2);
        l3 ^= o.limb(3);
        return this;
    }

    public MutableWide256 not() {
        l0 = ~l0;
        l1 = ~l1;
        l2 = ~l2;
        l3 = ~l3;
        return this;
    }

    public MutableWide256 shl(int k) {
        if (k < 0 || k >= 256) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
        if (k == 0) {
            return this;
        }
        long a0 = l0;
        long a1 = l1;
        long a2 = l2;
        long a3 = l3;
        int ls = k >>> 6;
        int bs = k & 63;
        if (bs == 0) {
            switch (ls) {
                case 1 -> {
                    l0 = 0;
                    l1 = a0;
                    l2 = a1;
                    l3 = a2;
                }
                case 2 -> {
                    l0 = 0;
                    l1 = 0;
                    l2 = a0;
                    l3 = a1;
                }
                default -> {
                    l0 = 0;
                    l1 = 0;
                    l2 = 0;
                    l3 = a0;
                }
            }
        } else {
            long inv = 64 - bs;
            switch (ls) {
                case 0 -> {
                    l0 = a0 << bs;
                    l1 = (a1 << bs) | (a0 >>> inv);
                    l2 = (a2 << bs) | (a1 >>> inv);
                    l3 = (a3 << bs) | (a2 >>> inv);
                }
                case 1 -> {
                    l0 = 0;
                    l1 = a0 << bs;
                    l2 = (a1 << bs) | (a0 >>> inv);
                    l3 = (a2 << bs) | (a1 >>> inv);
                }
                case 2 -> {
                    l0 = 0;
                    l1 = 0;
                    l2 = a0 << bs;
                    l3 = (a1 << bs) | (a0 >>> inv);
                }
                default -> {
                    l0 = 0;
                    l1 = 0;
                    l2 = 0;
                    l3 = a0 << bs;
                }
            }
        }
        return this;
    }

    public MutableWide256 ushr(int k) {
        if (k < 0 || k >= 256) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
        if (k == 0) {
            return this;
        }
        long a0 = l0;
        long a1 = l1;
        long a2 = l2;
        long a3 = l3;
        int ls = k >>> 6;
        int bs = k & 63;
        if (bs == 0) {
            switch (ls) {
                case 1 -> {
                    l0 = a1;
                    l1 = a2;
                    l2 = a3;
                    l3 = 0;
                }
                case 2 -> {
                    l0 = a2;
                    l1 = a3;
                    l2 = 0;
                    l3 = 0;
                }
                default -> {
                    l0 = a3;
                    l1 = 0;
                    l2 = 0;
                    l3 = 0;
                }
            }
        } else {
            long inv = 64 - bs;
            switch (ls) {
                case 0 -> {
                    l0 = (a0 >>> bs) | (a1 << inv);
                    l1 = (a1 >>> bs) | (a2 << inv);
                    l2 = (a2 >>> bs) | (a3 << inv);
                    l3 = a3 >>> bs;
                }
                case 1 -> {
                    l0 = (a1 >>> bs) | (a2 << inv);
                    l1 = (a2 >>> bs) | (a3 << inv);
                    l2 = a3 >>> bs;
                    l3 = 0;
                }
                case 2 -> {
                    l0 = (a2 >>> bs) | (a3 << inv);
                    l1 = a3 >>> bs;
                    l2 = 0;
                    l3 = 0;
                }
                default -> {
                    l0 = a3 >>> bs;
                    l1 = 0;
                    l2 = 0;
                    l3 = 0;
                }
            }
        }
        return this;
    }

    public int compareUnsigned(Wide256 o) {
        int c = Long.compareUnsigned(l3, o.limb(3));
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l2, o.limb(2));
        if (c != 0) {
            return c;
        }
        c = Long.compareUnsigned(l1, o.limb(1));
        return c != 0 ? c : Long.compareUnsigned(l0, o.limb(0));
    }

    public boolean equalsTo(Wide256 o) {
        return l0 == o.limb(0) && l1 == o.limb(1) && l2 == o.limb(2) && l3 == o.limb(3);
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

    public Wide256 toWide256() {
        return Wide256.of(l0, l1, l2, l3);
    }
}
