package com.scalarwide;

/**
 * Reusable mutable 128-bit accumulator for transient arithmetic: every
 * operation writes into this instance and allocates nothing. Not thread-safe.
 * Never use as a map key - keys must be immutable (see {@link Wide128}).
 * Opt-in misuse detection: with {@code -Dscalarwide.guard=true}, using an
 * instance from two threads throws {@link IllegalStateException} (best-effort).
 */
public final class MutableWide128 {

    private long l0;
    private long l1;

    private static final boolean GUARD = Boolean.getBoolean("scalarwide.guard");
    private transient volatile Thread guardOwner;

    private void guard() {
        if (!GUARD) {
            return;
        }
        Thread t = Thread.currentThread();
        Thread owner = guardOwner;
        if (owner == null) {
            guardOwner = t;
        } else if (owner != t) {
            throw new IllegalStateException("MutableWide128 shared across threads (red line): thread "
                    + t.getName() + " vs owner " + owner.getName());
        }
    }

    public MutableWide128 setZero() {
        guard();
        l0 = 0;
        l1 = 0;
        return this;
    }

    public MutableWide128 set(Wide128 v) {
        guard();
        l0 = v.limb(0);
        l1 = v.limb(1);
        return this;
    }

    public MutableWide128 set(long l0, long l1) {
        guard();
        this.l0 = l0;
        this.l1 = l1;
        return this;
    }

    public MutableWide128 add(Wide128 o) {
        guard();
        long o0 = o.limb(0);
        long o1 = o.limb(1);
        long r0 = l0 + o0;
        long c = Carry.carryOut(l0, r0);
        l0 = r0;
        l1 = l1 + o1 + c;
        return this;
    }

    public MutableWide128 sub(Wide128 o) {
        guard();
        long o0 = o.limb(0);
        long o1 = o.limb(1);
        long r0 = l0 - o0;
        long b = Carry.borrowOut(l0, r0);
        l0 = r0;
        l1 = l1 - o1 - b;
        return this;
    }

    public MutableWide128 mul(Wide128 o) {
        guard();
        long o0 = o.limb(0);
        long o1 = o.limb(1);
        long lo = l0 * o0;
        long mid = Math.unsignedMultiplyHigh(l0, o0) + l0 * o1 + l1 * o0;
        l0 = lo;
        l1 = mid;
        return this;
    }

    public MutableWide128 and(Wide128 o) {
        guard();
        l0 &= o.limb(0);
        l1 &= o.limb(1);
        return this;
    }

    public MutableWide128 or(Wide128 o) {
        guard();
        l0 |= o.limb(0);
        l1 |= o.limb(1);
        return this;
    }

    public MutableWide128 xor(Wide128 o) {
        guard();
        l0 ^= o.limb(0);
        l1 ^= o.limb(1);
        return this;
    }

    public MutableWide128 not() {
        guard();
        l0 = ~l0;
        l1 = ~l1;
        return this;
    }

    public MutableWide128 shl(int k) {
        guard();
        if (k < 0 || k >= 128) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
        if (k == 0) {
            return this;
        }
        long a0 = l0;
        long a1 = l1;
        if (k < 64) {
            l0 = a0 << k;
            l1 = (a1 << k) | (a0 >>> (64 - k));
        } else {
            l0 = 0;
            l1 = a0 << (k - 64);
        }
        return this;
    }

    public MutableWide128 ushr(int k) {
        guard();
        if (k < 0 || k >= 128) {
            throw new IllegalArgumentException("shift amount out of range: " + k);
        }
        if (k == 0) {
            return this;
        }
        long a0 = l0;
        long a1 = l1;
        if (k < 64) {
            l0 = (a0 >>> k) | (a1 << (64 - k));
            l1 = a1 >>> k;
        } else {
            l0 = a1 >>> (k - 64);
            l1 = 0;
        }
        return this;
    }

    public int compareUnsigned(Wide128 o) {
        int c = Long.compareUnsigned(l1, o.limb(1));
        return c != 0 ? c : Long.compareUnsigned(l0, o.limb(0));
    }

    public boolean equalsTo(Wide128 o) {
        return l0 == o.limb(0) && l1 == o.limb(1);
    }

    public long limb(int i) {
        return switch (i) {
            case 0 -> l0;
            case 1 -> l1;
            default -> throw new IndexOutOfBoundsException("limb " + i);
        };
    }

    public long longValue() {
        return l0;
    }

    public Wide128 toWide128() {
        return Wide128.of(l0, l1);
    }
}
