package com.scalarwide.mc;

import com.scalarwide.Wide512;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * MC-facing insertion-ordered set of {@link Wide512} nodes with O(1) add /
 * remove / contains and FIFO {@link #removeFirst()} - the wide counterpart of
 * the {@code LongLinkedOpenHashSet} buckets in {@code LeveledPriorityQueue}
 * (ticket/lighting graph queues).
 *
 * <p>Backed by a {@link LinkedHashSet} (the keys are already-allocated
 * immutable values; no extra key boxing beyond the set nodes). Not
 * thread-safe. A primitive-array specialisation is a future optimisation
 * candidate if queue churn shows up in measurements.</p>
 */
public final class Wide512OrderedSet {

    private final Set<Wide512> set = new LinkedHashSet<>();

    public boolean add(Wide512 key) {
        return set.add(Objects.requireNonNull(key, "key"));
    }

    public boolean remove(Wide512 key) {
        return set.remove(key);
    }

    public boolean contains(Wide512 key) {
        return set.contains(key);
    }

    /** @return the first-inserted node still present, or {@code null} if empty. */
    public Wide512 removeFirst() {
        Iterator<Wide512> it = set.iterator();
        if (!it.hasNext()) {
            return null;
        }
        Wide512 first = it.next();
        it.remove();
        return first;
    }

    public boolean isEmpty() {
        return set.isEmpty();
    }

    public int size() {
        return set.size();
    }

    public void clear() {
        set.clear();
    }
}
