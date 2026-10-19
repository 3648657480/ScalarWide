package com.scalarwide.mc;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Insertion-ordered set of 2560-bit section keys with O(1) add / remove /
 * contains and FIFO {@link #removeFirst()}. The keys are immutable values, so
 * a {@link LinkedHashSet} is the reference implementation; a primitive-array
 * specialisation is only worth it if queue churn shows up in measurements.
 * Not thread-safe.
 */
public final class WideSectionKey2560OrderedSet {

    private final Set<WideSectionKey2560> set = new LinkedHashSet<>();

    public boolean add(WideSectionKey2560 key) {
        return set.add(Objects.requireNonNull(key, "key"));
    }

    public boolean remove(WideSectionKey2560 key) {
        return set.remove(key);
    }

    public boolean contains(WideSectionKey2560 key) {
        return set.contains(key);
    }

    /** @return the first-inserted key still present, or {@code null} if empty. */
    public WideSectionKey2560 removeFirst() {
        Iterator<WideSectionKey2560> it = set.iterator();
        if (!it.hasNext()) {
            return null;
        }
        WideSectionKey2560 first = it.next();
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
