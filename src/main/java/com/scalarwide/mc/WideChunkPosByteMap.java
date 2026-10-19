package com.scalarwide.mc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Open-addressing map from 1024+1024-bit chunk positions to primitive
 * {@code byte} values. Same tag layout as {@link WideChunkPosFlatMap}.
 */
public final class WideChunkPosByteMap {

    private static final int TAG = 0x80000000;
    private static final int HASH_MASK = 0x7FFFFFFF;

    private int[] tags;
    private WideChunkPos[] keys;
    private byte[] vals;
    private int mask;
    private int size;
    private int resizeAt;

    public WideChunkPosByteMap() {
        this(1 << 10);
    }

    public WideChunkPosByteMap(int initialCapacity) {
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
        size = 0;
    }

    /** @return the previous value, or {@code fallback} if absent. */
    public byte put(WideChunkPos key, byte value, byte fallback) {
        Objects.requireNonNull(key, "key");
        if (size >= resizeAt) {
            grow();
        }
        return putRaw(key, value, fallback);
    }

    public byte getOrDefault(WideChunkPos key, byte fallback) {
        int i = findSlot(key);
        return i < 0 ? fallback : vals[i];
    }

    public boolean containsKey(WideChunkPos key) {
        return findSlot(key) >= 0;
    }

    /** @return the removed value, or {@code fallback} if absent. */
    public byte remove(WideChunkPos key, byte fallback) {
        int i = findSlot(key);
        if (i < 0) {
            return fallback;
        }
        byte old = vals[i];
        removeAt(i);
        return old;
    }

    public List<WideChunkPos> keys() {
        List<WideChunkPos> out = new ArrayList<>(size);
        for (int i = 0; i < tags.length; i++) {
            if (tags[i] != 0) {
                out.add(keys[i]);
            }
        }
        return out;
    }

    private int findSlot(WideChunkPos key) {
        int h = hash(key);
        int tag = h | TAG;
        int[] t = tags;
        WideChunkPos[] k = keys;
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

    private byte putRaw(WideChunkPos key, byte value, byte fallback) {
        int h = hash(key);
        int tag = h | TAG;
        int[] t = tags;
        WideChunkPos[] k = keys;
        byte[] v = vals;
        int i = h & mask;
        while (true) {
            int current = t[i];
            if (current == 0) {
                t[i] = tag;
                k[i] = key;
                v[i] = value;
                size++;
                return fallback;
            }
            if (current == tag && k[i].equals(key)) {
                byte old = v[i];
                v[i] = value;
                return old;
            }
            i = (i + 1) & mask;
        }
    }

    private void removeAt(int hole) {
        int[] t = tags;
        WideChunkPos[] k = keys;
        byte[] v = vals;
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
        size--;
    }

    private void grow() {
        int[] ot = tags;
        WideChunkPos[] ok = keys;
        byte[] ov = vals;
        alloc(ok.length * 2);
        for (int i = 0; i < ok.length; i++) {
            if (ot[i] != 0) {
                putRaw(ok[i], ov[i], (byte) 0);
            }
        }
    }

    private void alloc(int cap) {
        tags = new int[cap];
        keys = new WideChunkPos[cap];
        vals = new byte[cap];
        mask = cap - 1;
        resizeAt = cap - (cap >>> 2);
        size = 0;
    }

    private static int hash(WideChunkPos key) {
        int h = key.hashCode();
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & HASH_MASK;
    }
}
