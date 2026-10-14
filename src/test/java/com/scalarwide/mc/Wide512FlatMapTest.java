package com.scalarwide.mc;

import com.scalarwide.Wide512;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Wide512FlatMapTest {

    /** W4 chunk-key layout: x high 256 / z low 256, sign-extended. */
    private static Wide512 key(long x, long z) {
        long s = z >> 63;
        long t = x >> 63;
        return Wide512.of(z, s, s, s, x, t, t, t);
    }

    @Test
    void differentialAgainstHashMap() {
        Random rnd = new Random(12345);
        Wide512FlatMap map = new Wide512FlatMap();
        Map<Wide512, Object> ref = new HashMap<>();
        long base = 1L << 60;

        for (int i = 0; i < 20_000; i++) {
            long x = base + rnd.nextInt(128) - 64;
            long z = -base + rnd.nextInt(128) - 64;
            Wide512 k = key(x, z);
            int op = rnd.nextInt(100);
            if (op < 60) {
                int v = rnd.nextInt();
                assertEquals(ref.put(k, v), map.put(k, v), "put prev @ " + i);
            } else if (op < 85) {
                assertEquals(ref.remove(k), map.remove(k), "remove prev @ " + i);
            } else {
                assertEquals(ref.get(k), map.get(k), "get @ " + i);
                assertEquals(ref.containsKey(k), map.containsKey(k), "contains @ " + i);
            }
            assertEquals(ref.size(), map.size(), "size @ " + i);
        }

        for (Map.Entry<Wide512, Object> e : ref.entrySet()) {
            assertEquals(e.getValue(), map.get(e.getKey()));
            assertTrue(map.containsKey(e.getKey()));
        }
    }

    @Test
    void everyLimbParticipates() {
        Wide512FlatMap map = new Wide512FlatMap();
        map.put(Wide512.of(1, 0, 0, 0, 0, 0, 0, 0), "l0");
        map.put(Wide512.of(0, 0, 0, 1, 0, 0, 0, 0), "l3");
        map.put(Wide512.of(0, 0, 0, 0, 1, 0, 0, 0), "l4");
        map.put(Wide512.of(0, 0, 0, 0, 0, 0, 0, 1), "l7");
        map.put(key(1L << 49, 0), "far-x");
        map.put(key(0, 1L << 49), "far-z");
        assertEquals(6, map.size());
        assertEquals("l0", map.get(Wide512.of(1, 0, 0, 0, 0, 0, 0, 0)));
        assertEquals("l3", map.get(Wide512.of(0, 0, 0, 1, 0, 0, 0, 0)));
        assertEquals("l4", map.get(Wide512.of(0, 0, 0, 0, 1, 0, 0, 0)));
        assertEquals("l7", map.get(Wide512.of(0, 0, 0, 0, 0, 0, 0, 1)));
        assertEquals("far-x", map.get(key(1L << 49, 0)));
        assertEquals("far-z", map.get(key(0, 1L << 49)));
        assertNull(map.get(key(1L << 49, 1L << 49)));
    }

    @Test
    void cloneIsIndependentAndValuesSnapshot() {
        Wide512FlatMap map = new Wide512FlatMap();
        map.put(key(1, 2), "one");
        map.put(key(3, 4), "two");
        Wide512FlatMap copy = map.clone();
        assertNotSame(map, copy);
        assertEquals(map.size(), copy.size());
        assertEquals("one", copy.get(key(1, 2)));

        map.put(key(5, 6), "three");
        assertEquals(3, map.size());
        assertEquals(2, copy.size());
        assertNull(copy.get(key(5, 6)));

        copy.remove(key(1, 2));
        assertEquals("one", map.get(key(1, 2)));
        assertEquals(3, map.values().size());
    }

    @Test
    void nullValueRemovesAndConditionalRemove() {
        Wide512FlatMap map = new Wide512FlatMap();
        map.put(key(7, 8), "v");
        assertFalse(map.remove(key(7, 8), "other"));
        assertTrue(map.remove(key(7, 8), "v"));
        assertTrue(map.isEmpty());

        map.put(key(9, 10), "w");
        assertEquals("w", map.put(key(9, 10), null));
        assertFalse(map.containsKey(key(9, 10)));
        assertEquals(0, map.size());
    }

    @Test
    void removeClusterKeepsLaterLookupsCorrect() {
        Wide512FlatMap map = new Wide512FlatMap();
        for (long i = 0; i < 512; i++) {
            map.put(key(i, i), "v" + i);
        }
        for (long i = 0; i < 512; i += 2) {
            assertEquals("v" + i, map.remove(key(i, i)));
        }
        assertEquals(256, map.size());
        for (long i = 0; i < 512; i++) {
            if (i % 2 == 0) {
                assertNull(map.get(key(i, i)));
            } else {
                assertEquals("v" + i, map.get(key(i, i)));
            }
        }
        map.clear();
        assertEquals(0, map.size());
        assertNull(map.get(key(1, 1)));
    }
}
