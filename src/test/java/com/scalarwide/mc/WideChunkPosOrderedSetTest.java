package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class WideChunkPosOrderedSetTest {

    private static final BigInteger BASE = BigInteger.ONE.shiftLeft(1018);

    private static WideChunkPos key(int x, int z) {
        return WideChunkPos.of(BASE.add(BigInteger.valueOf(x)),
            BASE.negate().add(BigInteger.valueOf(z)));
    }

    @Test
    void fifoOrderAndDuplicateAdd() {
        WideChunkPosOrderedSet set = new WideChunkPosOrderedSet();
        assertTrue(set.add(key(1, 1)));
        assertTrue(set.add(key(2, 2)));
        assertFalse(set.add(key(1, 1)));
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
        WideChunkPosOrderedSet set = new WideChunkPosOrderedSet();
        set.add(key(1, 0));
        set.add(key(2, 0));
        set.add(key(3, 0));
        assertTrue(set.remove(key(2, 0)));
        assertFalse(set.contains(key(2, 0)));
        assertEquals(key(1, 0), set.removeFirst());
        assertEquals(key(3, 0), set.removeFirst());

        set.add(key(9, 9));
        set.clear();
        assertTrue(set.isEmpty());
    }
}
