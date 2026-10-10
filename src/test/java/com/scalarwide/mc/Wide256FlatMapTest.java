package com.scalarwide.mc;

import com.scalarwide.Wide256;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Wide256FlatMapTest {

    @Test
    void differentialAgainstHashMap() {
        Random rnd = new Random(20261010L);
        Wide256FlatMap flat = new Wide256FlatMap(64);
        Map<Wide256, Integer> ref = new HashMap<>();
        int next = 0;
        for (int i = 0; i < 100_000; i++) {
            int op = rnd.nextInt(10);
            Wide256 k = sample(rnd);
            if (op < 8) {
                int v = next++ % 1_000_000;
                flat.put(k, v);
                ref.put(k, v);
            } else {
                int exp = ref.getOrDefault(k, -1);
                assertEquals(exp, flat.get(k), "get mismatch at " + i);
                assertEquals(ref.containsKey(k), flat.containsKey(k), "contains mismatch at " + i);
            }
        }
        assertEquals(ref.size(), flat.size());
        for (Map.Entry<Wide256, Integer> e : ref.entrySet()) {
            assertEquals((int) e.getValue(), flat.get(e.getKey()));
        }
    }

    @Test
    void zeroKeyAndEdges() {
        Wide256FlatMap m = new Wide256FlatMap();
        m.put(Wide256.ZERO, 0);
        m.put(Wide256.ONE, 1);
        m.put(Wide256.MAX_VALUE, 2);
        assertEquals(0, m.get(Wide256.ZERO));
        assertEquals(1, m.get(Wide256.ONE));
        assertEquals(2, m.get(Wide256.MAX_VALUE));
        assertEquals(-1, m.get(Wide256.of(42)));
        m.put(Wide256.ZERO, 9);
        assertEquals(9, m.get(Wide256.ZERO));
        assertEquals(3, m.size());
        assertThrows(IllegalArgumentException.class, () -> m.put(Wide256.of(1), -1));
        m.clear();
        assertEquals(0, m.size());
        assertEquals(-1, m.get(Wide256.ZERO));
    }

    @Test
    void denseKeysFastGrowth() {
        Wide256FlatMap m = new Wide256FlatMap(16);
        for (int i = 1; i <= 5000; i++) {
            m.put(Wide256.of(i), i);
        }
        for (int i = 1; i <= 5000; i++) {
            assertEquals(i, m.get(Wide256.of(i)));
        }
        assertEquals(5000, m.size());
    }

    @Test
    void removeDifferential() {
        Random rnd = new Random(991122L);
        Wide256FlatMap flat = new Wide256FlatMap(64);
        Map<Wide256, Integer> ref = new HashMap<>();
        int next = 0;
        for (int i = 0; i < 200_000; i++) {
            int op = rnd.nextInt(10);
            Wide256 k = sample(rnd);
            if (op < 4) {
                int v = next++ % 1_000_000;
                flat.put(k, v);
                ref.put(k, v);
            } else if (op < 7) {
                boolean expected = ref.remove(k) != null;
                assertEquals(expected, flat.remove(k), "remove mismatch at " + i);
            } else {
                assertEquals(ref.getOrDefault(k, -1), flat.get(k), "get mismatch at " + i);
            }
        }
        assertEquals(ref.size(), flat.size());
        for (Map.Entry<Wide256, Integer> e : ref.entrySet()) {
            assertEquals((int) e.getValue(), flat.get(e.getKey()));
        }
    }

    @Test
    void removeClusterAndEdges() {
        Wide256FlatMap m = new Wide256FlatMap(64);
        for (int i = 0; i < 500; i++) {
            m.put(Wide256.of(i), i);
        }
        for (int i = 0; i < 500; i += 2) {
            assertTrue(m.remove(Wide256.of(i)));
        }
        for (int i = 0; i < 500; i++) {
            assertEquals(i % 2 == 0 ? -1 : i, m.get(Wide256.of(i)));
        }
        assertEquals(250, m.size());
        assertFalse(m.remove(Wide256.of(2)));
        m.put(Wide256.ZERO, 0);
        assertTrue(m.remove(Wide256.ZERO));
        assertEquals(-1, m.get(Wide256.ZERO));
        assertEquals(250, m.size());
    }

    private static Wide256 sample(Random rnd) {
        return Wide256.of(rnd.nextLong(), rnd.nextLong() >>> (rnd.nextInt(4) * 16),
                rnd.nextLong(), rnd.nextLong() >>> (rnd.nextInt(4) * 16));
    }
}
