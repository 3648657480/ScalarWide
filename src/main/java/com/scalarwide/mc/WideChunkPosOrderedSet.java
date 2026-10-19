package com.scalarwide.mc;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Insertion-ordered set of 1024+1024-bit chunk positions with FIFO
 * {@link #removeFirst()}. Reference implementation over immutable keys.
 */
public final class WideChunkPosOrderedSet {

    private final Set<WideChunkPos> set = new LinkedHashSet<>();

    public boolean add(WideChunkPos key) {
        return set.add(Objects.requireNonNull(key, "key"));
    }

    public boolean remove(WideChunkPos key) {
        return set.remove(key);
    }

    public boolean contains(WideChunkPos key) {
        return set.contains(key);
    }

    /** @return the first-inserted key still present, or {@code null} if empty. */
    public WideChunkPos removeFirst() {
        Iterator<WideChunkPos> it = set.iterator();
        if (!it.hasNext()) {
            return null;
        }
        WideChunkPos first = it.next();
        it.remove();
        return first;
    }

    public boolean isEmpty() {
        return set.isEmpty();
    }

    public int size() {
        return set.size();
    }

    public Iterator<WideChunkPos> iterator() {
        return set.iterator();
    }

    public void clear() {
        set.clear();
    }
}
