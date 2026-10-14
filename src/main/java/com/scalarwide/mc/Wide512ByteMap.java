package com.scalarwide.mc;

import com.scalarwide.Wide512;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * MC-facing flat open-addressing map from 128/512-bit {@link Wide512} keys to
 * primitive {@code byte} values (e.g. the ticket/lighting graph levels in
 * {@code DynamicGraphMinFixedPoint}). Control bytes plus a {@code Wide512[]}
 * key array and a {@code byte[]} value array: keys are never boxed.
 *
 * <p>Default value: {@link #getOrDefault} takes an explicit fallback; the
 * graph's {@code defaultReturnValue((byte) -1)} convention is preserved by the
 * caller. {@link #keys()} returns a snapshot for the graph's
 * {@code keySet().forEach} sweep. Iteration order is table order (unordered).
 * Not thread-safe.
 */
public final class Wide512ByteMap {

    private byte[] ctrl;
    private Wide512[] keys;
    private byte[] vals;
    private int mask;
    private int size;
    private int resizeAt;

    public Wide512ByteMap() {
        this(1 << 10);
    }

    public Wide512ByteMap(int initialCapacity) {
        int cap = Integer.highestOneBit(Math.max(16, initialCapacity - 1)) << 1;
        alloc(cap);
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return mask + 1;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        Arrays.fill(ctrl, (byte) 0);
        Arrays.fill(keys, null);
        size = 0;
    }

    /** @return the previous value, or {@code fallback} if absent. */
    public byte put(Wide512 key, byte value, byte fallback) {
        Objects.requireNonNull(key, "key");
        if (size >= resizeAt) {
            grow();
        }
        return putRaw(key, value, fallback);
    }

    public byte getOrDefault(Wide512 key, byte fallback) {
        int i = findSlot(key);
        return i < 0 ? fallback : vals[i];
    }

    public boolean containsKey(Wide512 key) {
        return findSlot(key) >= 0;
    }

    /** @return the removed value, or {@code fallback} if absent. */
    public byte remove(Wide512 key, byte fallback) {
        int i = findSlot(key);
        if (i < 0) {
            return fallback;
        }
        byte old = vals[i];
        removeAt(i);
        return old;
    }

    /** Key snapshot in table order (unordered). */
    public List<Wide512> keys() {
        List<Wide512> out = new ArrayList<>(size);
        for (int i = 0; i < ctrl.length; i++) {
            if (ctrl[i] != 0) {
                out.add(keys[i]);
            }
        }
        return out;
    }

    private int findSlot(Wide512 key) {
        byte[] c = ctrl;
        Wide512[] k = keys;
        int i = hash(key) & mask;
        while (true) {
            if (c[i] == 0) {
                return -1;
            }
            if (k[i].equals(key)) {
                return i;
            }
            i = (i + 1) & mask;
        }
    }

    private byte putRaw(Wide512 key, byte value, byte fallback) {
        byte[] c = ctrl;
        Wide512[] k = keys;
        byte[] v = vals;
        int i = hash(key) & mask;
        while (true) {
            if (c[i] == 0) {
                c[i] = 1;
                k[i] = key;
                v[i] = value;
                size++;
                return fallback;
            }
            if (k[i].equals(key)) {
                byte old = v[i];
                v[i] = value;
                return old;
            }
            i = (i + 1) & mask;
        }
    }

    private void removeAt(int hole) {
        byte[] c = ctrl;
        Wide512[] k = keys;
        byte[] v = vals;
        int j = (hole + 1) & mask;
        while (c[j] != 0) {
            int ideal = hash(k[j]) & mask;
            if (((j - ideal) & mask) >= ((j - hole) & mask)) {
                k[hole] = k[j];
                v[hole] = v[j];
                hole = j;
            }
            j = (j + 1) & mask;
        }
        c[hole] = 0;
        k[hole] = null;
        size--;
    }

    private void grow() {
        Wide512[] ok = keys;
        byte[] ov = vals;
        byte[] oc = ctrl;
        alloc(ok.length * 2);
        for (int i = 0; i < ok.length; i++) {
            if (oc[i] != 0) {
                putRaw(ok[i], ov[i], (byte) 0);
            }
        }
    }

    private void alloc(int cap) {
        ctrl = new byte[cap];
        keys = new Wide512[cap];
        vals = new byte[cap];
        mask = cap - 1;
        resizeAt = cap >>> 1;
        size = 0;
    }

    private static int hash(Wide512 key) {
        int h = key.hashCode();
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }
}
