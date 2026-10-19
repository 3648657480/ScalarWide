package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class WideSectionKey2560OrderedSetTest {

    private static final BigInteger BASE = BigInteger.ONE.shiftLeft(1018);

    private static WideSectionKey2560 key(int x, int y, int z) {
        return WideSectionKey2560.of(
            BASE.add(BigInteger.valueOf(x)),
            BigInteger.valueOf(y),
            BASE.negate().add(BigInteger.valueOf(z)));
    }

    @Test
    void fifoOrderAndDuplicateAdd() {
        WideSectionKey2560OrderedSet set = new WideSectionKey2560OrderedSet();
        assertTrue(set.add(key(1, 1, 1)));
        assertTrue(set.add(key(2, 2, 2)));
        assertFalse(set.add(key(1, 1, 1)));
        assertTrue(set.add(key(3, 3, 3)));
        assertEquals(3, set.size());

        assertEquals(key(1, 1, 1), set.removeFirst());
        assertEquals(key(2, 2, 2), set.removeFirst());
        assertEquals(key(3, 3, 3), set.removeFirst());
        assertNull(set.removeFirst());
        assertTrue(set.isEmpty());
    }

    @Test
    void removeAnyThenRemoveFirstContinuesOrder() {
        WideSectionKey2560OrderedSet set = new WideSectionKey2560OrderedSet();
        set.add(key(1, 0, 0));
        set.add(key(2, 0, 0));
        set.add(key(3, 0, 0));
        assertTrue(set.remove(key(2, 0, 0)));
        assertFalse(set.contains(key(2, 0, 0)));
        assertEquals(key(1, 0, 0), set.removeFirst());
        assertEquals(key(3, 0, 0), set.removeFirst());

        set.add(key(9, 9, 9));
        assertEquals(1, set.size());
        set.clear();
        assertTrue(set.isEmpty());
    }
}
