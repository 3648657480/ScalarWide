package com.scalarwide.mc;

import com.scalarwide.Wide256;

/**
 * MC-facing flat open-addressing map specialized for {@link Wide256} keys.
 *
 * <p>ASIC-style layout: one interleaved slot per entry ({@code l0 l1 l2 l3
 * value}), power-of-two capacity, linear probing, no node objects, no boxing,
 * zero allocation on the hot path. Values must be non-negative ({@code -1} is
 * the empty marker). Not thread-safe. No removal in this cut.
 */
public final class Wide256FlatMap {

    private static final int EMPTY = -1;

    private long[] slots;
    private int mask;
    private int size;
    private int resizeAt;

    public Wide256FlatMap() {
        this(1 << 10);
    }

    public Wide256FlatMap(int initialCapacity) {
        int cap = Integer.highestOneBit(Math.max(16, initialCapacity - 1)) << 1;
        alloc(cap);
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return mask + 1;
    }

    public void clear() {
        java.util.Arrays.fill(slots, 0L);
        for (int i = 4; i < slots.length; i += 5) {
            slots[i] = EMPTY;
        }
        size = 0;
    }

    public void put(Wide256 key, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0: " + value);
        }
        if (size >= resizeAt) {
            grow();
        }
        putRaw(key.limb(0), key.limb(1), key.limb(2), key.limb(3), value);
    }

    public int get(Wide256 key) {
        return getOrDefault(key, -1);
    }

    public int getOrDefault(Wide256 key, int fallback) {
        long k0 = key.limb(0);
        long k1 = key.limb(1);
        long k2 = key.limb(2);
        long k3 = key.limb(3);
        long[] s = slots;
        int i = hash(k0, k1, k2, k3) & mask;
        while (true) {
            int o = i * 5;
            if (s[o + 4] == EMPTY) {
                return fallback;
            }
            if (s[o] == k0 && s[o + 1] == k1 && s[o + 2] == k2 && s[o + 3] == k3) {
                return (int) s[o + 4];
            }
            i = (i + 1) & mask;
        }
    }

    public boolean containsKey(Wide256 key) {
        return getOrDefault(key, EMPTY) != EMPTY;
    }

    /**
     * Removes the mapping for {@code key} if present, using backward-shift
     * deletion (no tombstones; probe clusters stay compact).
     *
     * @return {@code true} if the key was present
     */
    public boolean remove(Wide256 key) {
        long k0 = key.limb(0);
        long k1 = key.limb(1);
        long k2 = key.limb(2);
        long k3 = key.limb(3);
        long[] s = slots;
        int i = hash(k0, k1, k2, k3) & mask;
        while (true) {
            int o = i * 5;
            if (s[o + 4] == EMPTY) {
                return false;
            }
            if (s[o] == k0 && s[o + 1] == k1 && s[o + 2] == k2 && s[o + 3] == k3) {
                break;
            }
            i = (i + 1) & mask;
        }
        int hole = i;
        int j = (i + 1) & mask;
        while (true) {
            int jo = j * 5;
            if (s[jo + 4] == EMPTY) {
                break;
            }
            int ideal = hash(s[jo], s[jo + 1], s[jo + 2], s[jo + 3]) & mask;
            if (((j - ideal) & mask) >= ((j - hole) & mask)) {
                int ho = hole * 5;
                s[ho] = s[jo];
                s[ho + 1] = s[jo + 1];
                s[ho + 2] = s[jo + 2];
                s[ho + 3] = s[jo + 3];
                s[ho + 4] = s[jo + 4];
                hole = j;
            }
            j = (j + 1) & mask;
        }
        int ho = hole * 5;
        s[ho] = 0;
        s[ho + 1] = 0;
        s[ho + 2] = 0;
        s[ho + 3] = 0;
        s[ho + 4] = EMPTY;
        size--;
        return true;
    }

    private void putRaw(long k0, long k1, long k2, long k3, int value) {
        long[] s = slots;
        int i = hash(k0, k1, k2, k3) & mask;
        while (true) {
            int o = i * 5;
            if (s[o + 4] == EMPTY) {
                s[o] = k0;
                s[o + 1] = k1;
                s[o + 2] = k2;
                s[o + 3] = k3;
                s[o + 4] = value;
                size++;
                return;
            }
            if (s[o] == k0 && s[o + 1] == k1 && s[o + 2] == k2 && s[o + 3] == k3) {
                s[o + 4] = value;
                return;
            }
            i = (i + 1) & mask;
        }
    }

    private void grow() {
        long[] old = slots;
        alloc(old.length / 5 * 2);
        for (int o = 0; o < old.length; o += 5) {
            if (old[o + 4] != EMPTY) {
                putRaw(old[o], old[o + 1], old[o + 2], old[o + 3], (int) old[o + 4]);
            }
        }
    }

    private void alloc(int cap) {
        slots = new long[cap * 5];
        for (int i = 4; i < slots.length; i += 5) {
            slots[i] = EMPTY;
        }
        mask = cap - 1;
        resizeAt = cap >>> 1;
        size = 0;
    }

    private static int hash(long l0, long l1, long l2, long l3) {
        long h = 0x243F6A8885A308D3L;
        h = (h ^ l0) * 0x9E3779B97F4A7C15L;
        h = (h ^ l1) * 0x9E3779B97F4A7C15L;
        h = (h ^ l2) * 0x9E3779B97F4A7C15L;
        h = (h ^ l3) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (int) (h ^ (h >>> 32));
    }
}
