package com.scalarwide.mc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Open-addressing flat map for 2560-bit section keys. The hot metadata is a
 * single {@code tags[]} array: 0 means empty, otherwise the low 31 bits are a
 * hash tag and the high bit is set. The 320-byte key object is touched only
 * after a tag match; equality remains the full 40-limb comparison.
 */
public final class WideSectionKey2560FlatMap {

    private static final int TAG = 0x80000000;
    private static final int HASH_MASK = 0x7FFFFFFF;

    private final int loadPercent;
    private int[] tags;
    private WideSectionKey2560[] keys;
    private Object[] vals;
    private int mask;
    private int size;
    private int resizeAt;

    public WideSectionKey2560FlatMap() {
        this(1 << 10);
    }

    public WideSectionKey2560FlatMap(int initialCapacity) {
        this(initialCapacity, 75);
    }

    /**
     * Constructs a map with a resize threshold of {@code loadPercent}%.
     *
     * @throws IllegalArgumentException if {@code loadPercent} is outside 50..90
     */
    WideSectionKey2560FlatMap(int initialCapacity, int loadPercent) {
        if (loadPercent < 50 || loadPercent > 90) {
            throw new IllegalArgumentException("loadPercent must be in 50..90");
        }
        this.loadPercent = loadPercent;
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
        Arrays.fill(tags, 0);
        Arrays.fill(keys, null);
        Arrays.fill(vals, null);
        size = 0;
    }

    public Object put(WideSectionKey2560 key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            return remove(key);
        }
        if (size >= resizeAt) {
            grow();
        }
        return putRaw(key, value);
    }

    public Object get(WideSectionKey2560 key) {
        return getOrDefault(key, null);
    }

    public Object getOrDefault(WideSectionKey2560 key, Object fallback) {
        int h = hash(key);
        int tag = h | TAG;
        int[] t = tags;
        WideSectionKey2560[] k = keys;
        Object[] v = vals;
        int i = h & mask;
        while (true) {
            int current = t[i];
            if (current == 0) {
                return fallback;
            }
            if (current == tag && k[i].equals(key)) {
                return v[i];
            }
            i = (i + 1) & mask;
        }
    }

    public boolean containsKey(WideSectionKey2560 key) {
        int h = hash(key);
        int tag = h | TAG;
        int[] t = tags;
        WideSectionKey2560[] k = keys;
        int i = h & mask;
        while (true) {
            int current = t[i];
            if (current == 0) {
                return false;
            }
            if (current == tag && k[i].equals(key)) {
                return true;
            }
            i = (i + 1) & mask;
        }
    }

    public Object remove(WideSectionKey2560 key) {
        int i = findSlot(key);
        if (i < 0) {
            return null;
        }
        Object old = vals[i];
        removeAt(i);
        return old;
    }

    public boolean remove(WideSectionKey2560 key, Object value) {
        int i = findSlot(key);
        if (i < 0 || !Objects.equals(vals[i], value)) {
            return false;
        }
        removeAt(i);
        return true;
    }

    public List<Object> values() {
        List<Object> out = new ArrayList<>(size);
        for (int i = 0; i < tags.length; i++) {
            if (tags[i] != 0) {
                out.add(vals[i]);
            }
        }
        return out;
    }

    @Override
    public WideSectionKey2560FlatMap clone() {
        WideSectionKey2560FlatMap m = new WideSectionKey2560FlatMap(tags.length, loadPercent);
        m.tags = tags.clone();
        m.keys = keys.clone();
        m.vals = vals.clone();
        m.size = size;
        return m;
    }

    private int findSlot(WideSectionKey2560 key) {
        int h = hash(key);
        int tag = h | TAG;
        int[] t = tags;
        WideSectionKey2560[] k = keys;
        int i = h & mask;
        while (true) {
            int current = t[i];
            if (current == 0) {
                return -1;
            }
            if (current == tag && k[i].equals(key)) {
                return i;
            }
            i = (i + 1) & mask;
        }
    }

    private Object putRaw(WideSectionKey2560 key, Object value) {
        int h = hash(key);
        int tag = h | TAG;
        int[] t = tags;
        WideSectionKey2560[] k = keys;
        Object[] v = vals;
        int i = h & mask;
        while (true) {
            int current = t[i];
            if (current == 0) {
                t[i] = tag;
                k[i] = key;
                v[i] = value;
                size++;
                return null;
            }
            if (current == tag && k[i].equals(key)) {
                Object old = v[i];
                v[i] = value;
                return old;
            }
            i = (i + 1) & mask;
        }
    }

    private void removeAt(int hole) {
        int[] t = tags;
        WideSectionKey2560[] k = keys;
        Object[] v = vals;
        int j = (hole + 1) & mask;
        while (t[j] != 0) {
            int ideal = (t[j] & HASH_MASK) & mask;
            if (((j - ideal) & mask) >= ((j - hole) & mask)) {
                t[hole] = t[j];
                k[hole] = k[j];
                v[hole] = v[j];
                hole = j;
            }
            j = (j + 1) & mask;
        }
        t[hole] = 0;
        k[hole] = null;
        v[hole] = null;
        size--;
    }

    private void grow() {
        int[] ot = tags;
        WideSectionKey2560[] ok = keys;
        Object[] ov = vals;
        alloc(ok.length * 2);
        for (int i = 0; i < ok.length; i++) {
            if (ot[i] != 0) {
                putRaw(ok[i], ov[i]);
            }
        }
    }

    private void alloc(int cap) {
        tags = new int[cap];
        keys = new WideSectionKey2560[cap];
        vals = new Object[cap];
        mask = cap - 1;
        resizeAt = (int) ((long) cap * loadPercent / 100);
        size = 0;
    }

    private static int hash(WideSectionKey2560 key) {
        int h = key.hashCode();
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & HASH_MASK;
    }
}
