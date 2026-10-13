package com.scalarwide.mc;

import com.scalarwide.Wide128;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * MC-facing flat open-addressing map keyed by a 128-bit {@link Wide128} value
 * with Object values (e.g. chunk holders). Control bytes plus a
 * {@code Wide128[]} key array and an {@code Object[]} value array: keys are
 * never boxed and the (already immutable) key object carries its own
 * {@code equals}/{@code hashCode}.
 *
 * <p>Wide chunk keys do not fit the 64-bit {@link LongLongFlatMap}; this is the
 * 128-bit counterpart for {@code ChunkPos}-style pairs beyond the int domain
 * (x: high limb, z: low limb by convention). Iteration order is table order
 * (unordered); {@link #clone()} copies the table. Null values are not stored
 * ({@code put(k, null)} removes). Not thread-safe.
 */
public final class Wide128FlatMap {

    private byte[] ctrl;
    private Wide128[] keys;
    private Object[] vals;
    private int mask;
    private int size;
    private int resizeAt;

    public Wide128FlatMap() {
        this(1 << 10);
    }

    public Wide128FlatMap(int initialCapacity) {
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
        Arrays.fill(vals, null);
        size = 0;
    }

    /** @return the previous value, or {@code null}; a {@code null} value removes. */
    public Object put(Wide128 key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            return remove(key);
        }
        if (size >= resizeAt) {
            grow();
        }
        return putRaw(key, value);
    }

    public Object get(Wide128 key) {
        return getOrDefault(key, null);
    }

    public Object getOrDefault(Wide128 key, Object fallback) {
        byte[] c = ctrl;
        Wide128[] k = keys;
        Object[] v = vals;
        int i = hash(key) & mask;
        while (true) {
            if (c[i] == 0) {
                return fallback;
            }
            if (k[i].equals(key)) {
                return v[i];
            }
            i = (i + 1) & mask;
        }
    }

    public boolean containsKey(Wide128 key) {
        byte[] c = ctrl;
        Wide128[] k = keys;
        int i = hash(key) & mask;
        while (true) {
            if (c[i] == 0) {
                return false;
            }
            if (k[i].equals(key)) {
                return true;
            }
            i = (i + 1) & mask;
        }
    }

    /** @return the removed value, or {@code null} if absent. */
    public Object remove(Wide128 key) {
        int i = findSlot(key);
        if (i < 0) {
            return null;
        }
        Object old = vals[i];
        removeAt(i);
        return old;
    }

    /** Conditional remove: only if the current value is {@code value}. */
    public boolean remove(Wide128 key, Object value) {
        int i = findSlot(key);
        if (i < 0 || !Objects.equals(vals[i], value)) {
            return false;
        }
        removeAt(i);
        return true;
    }

    /** Value snapshot in table order (unordered). */
    public List<Object> values() {
        List<Object> out = new ArrayList<>(size);
        for (int i = 0; i < ctrl.length; i++) {
            if (ctrl[i] != 0) {
                out.add(vals[i]);
            }
        }
        return out;
    }

    @Override
    public Wide128FlatMap clone() {
        Wide128FlatMap m = new Wide128FlatMap(ctrl.length);
        m.ctrl = ctrl.clone();
        m.keys = keys.clone();
        m.vals = vals.clone();
        m.size = size;
        return m;
    }

    private int findSlot(Wide128 key) {
        byte[] c = ctrl;
        Wide128[] k = keys;
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

    private Object putRaw(Wide128 key, Object value) {
        byte[] c = ctrl;
        Wide128[] k = keys;
        Object[] v = vals;
        int i = hash(key) & mask;
        while (true) {
            if (c[i] == 0) {
                c[i] = 1;
                k[i] = key;
                v[i] = value;
                size++;
                return null;
            }
            if (k[i].equals(key)) {
                Object old = v[i];
                v[i] = value;
                return old;
            }
            i = (i + 1) & mask;
        }
    }

    private void removeAt(int hole) {
        byte[] c = ctrl;
        Wide128[] k = keys;
        Object[] v = vals;
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
        v[hole] = null;
        size--;
    }

    private void grow() {
        Wide128[] ok = keys;
        Object[] ov = vals;
        byte[] oc = ctrl;
        alloc(ok.length * 2);
        for (int i = 0; i < ok.length; i++) {
            if (oc[i] != 0) {
                putRaw(ok[i], ov[i]);
            }
        }
    }

    private void alloc(int cap) {
        ctrl = new byte[cap];
        keys = new Wide128[cap];
        vals = new Object[cap];
        mask = cap - 1;
        resizeAt = cap >>> 1;
        size = 0;
    }

    private static int hash(Wide128 key) {
        int h = key.hashCode();
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }
}
