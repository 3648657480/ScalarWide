package com.scalarwide.mc;

import com.scalarwide.Wide512;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Wide512OrderedSetTest {

    private static Wide512 key(long x, long z) {
        return WideChunkKey.pack(x, z);
    }

    @Test
    void fifoOrderAndDuplicateAdd() {
        Wide512OrderedSet set = new Wide512OrderedSet();
        assertTrue(set.add(key(1, 1)));
        assertTrue(set.add(key(2, 2)));
        assertFalse(set.add(key(1, 1))); // duplicate: position unchanged
        assertTrue(set.add(key(3, 3)));
        assertEquals(3, set.size());

        assertEquals(key(1, 1), set.removeFirst());
        assertEquals(key(2, 2), set.removeFirst());
        assertEquals(key(3, 3), set.removeFirst());
        assertNull(set.removeFirst());
        assertTrue(set.isEmpty());
    }

    @Test
    void removeAnyThenRemoveFirstContinuesOrder() {
        Wide512OrderedSet set = new Wide512OrderedSet();
        set.add(key(1, 0));
        set.add(key(2, 0));
        set.add(key(3, 0));
        assertTrue(set.remove(key(2, 0)));
        assertFalse(set.contains(key(2, 0)));
        assertEquals(key(1, 0), set.removeFirst());
        assertEquals(key(3, 0), set.removeFirst());

        set.add(key(9, 9));
        assertEquals(1, set.size());
        set.clear();
        assertTrue(set.isEmpty());
    }
}
