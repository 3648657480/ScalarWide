package com.scalarwide.mc;

import java.util.Arrays;

/**
 * MC-facing flat open-addressing map for 64-bit packed keys - the hardened
 * fast path for epoch-window local coordinates packed into one long
 * ({@link #pack(int, int)}). Primitive arrays only (control bytes + key longs
 * + value ints): no objects, no boxing, zero allocation on the hot path.
 * Wide keys that do not fit 64 bits stay on {@link Wide256FlatMap}.
 *
 * <p>Not thread-safe.
 */
public final class LongLongFlatMap {

    private byte[] ctrl;
    private long[] keys;
    private int[] vals;
    private int mask;
    private int size;
    private int resizeAt;

    public LongLongFlatMap() {
        this(1 << 10);
    }

    public LongLongFlatMap(int initialCapacity) {
        int cap = Integer.highestOneBit(Math.max(16, initialCapacity - 1)) << 1;
        alloc(cap);
    }

    /** Packs two signed 32-bit local coordinates ({@code x} high, {@code z} low). */
    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackZ(long key) {
        return (int) key;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return mask + 1;
    }

    public void clear() {
        Arrays.fill(ctrl, (byte) 0);
        size = 0;
    }

    public void put(long key, int value) {
        if (size >= resizeAt) {
            grow();
        }
        putRaw(key, value);
    }

    public int get(long key) {
        return getOrDefault(key, -1);
    }

    public int getOrDefault(long key, int fallback) {
        byte[] c = ctrl;
        long[] k = keys;
        int i = hash(key) & mask;
        while (true) {
            if (c[i] == 0) {
                return fallback;
            }
            if (k[i] == key) {
                return vals[i];
            }
            i = (i + 1) & mask;
        }
    }

    public boolean containsKey(long key) {
        byte[] c = ctrl;
        long[] k = keys;
        int i = hash(key) & mask;
        while (true) {
            if (c[i] == 0) {
                return false;
            }
            if (k[i] == key) {
                return true;
            }
            i = (i + 1) & mask;
        }
    }

    /**
     * Removes the mapping for {@code key} if present (backward-shift deletion).
     *
     * @return {@code true} if the key was present
     */
    public boolean remove(long key) {
        byte[] c = ctrl;
        long[] k = keys;
        int i = hash(key) & mask;
        while (true) {
            if (c[i] == 0) {
                return false;
            }
            if (k[i] == key) {
                break;
            }
            i = (i + 1) & mask;
        }
        int hole = i;
        int j = (i + 1) & mask;
        while (c[j] != 0) {
            int ideal = hash(k[j]) & mask;
            if (((j - ideal) & mask) >= ((j - hole) & mask)) {
                k[hole] = k[j];
                vals[hole] = vals[j];
                hole = j;
            }
            j = (j + 1) & mask;
        }
        c[hole] = 0;
        size--;
        return true;
    }

    private void putRaw(long key, int value) {
        byte[] c = ctrl;
        long[] k = keys;
        int i = hash(key) & mask;
        while (true) {
            if (c[i] == 0) {
                c[i] = 1;
                k[i] = key;
                vals[i] = value;
                size++;
                return;
            }
            if (k[i] == key) {
                vals[i] = value;
                return;
            }
            i = (i + 1) & mask;
        }
    }

    private void grow() {
        byte[] oc = ctrl;
        long[] ok = keys;
        int[] ov = vals;
        alloc(ok.length * 2);
        for (int i = 0; i < ok.length; i++) {
            if (oc[i] != 0) {
                putRaw(ok[i], ov[i]);
            }
        }
    }

    private void alloc(int cap) {
        ctrl = new byte[cap];
        keys = new long[cap];
        vals = new int[cap];
        mask = cap - 1;
        resizeAt = cap >>> 1;
        size = 0;
    }

    private static int hash(long v) {
        v ^= v >>> 33;
        v *= 0xFF51AFD7ED558CCDL;
        v ^= v >>> 33;
        v *= 0xC4CEB9FE1A85EC53L;
        v ^= v >>> 33;
        return (int) v;
    }
}
