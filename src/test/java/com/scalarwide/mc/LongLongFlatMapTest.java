package com.scalarwide.mc;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongLongFlatMapTest {

    @Test
    void differentialAgainstHashMap() {
        Random rnd = new Random(31337L);
        LongLongFlatMap flat = new LongLongFlatMap(64);
        Map<Long, Integer> ref = new HashMap<>();
        int next = 0;
        for (int i = 0; i < 200_000; i++) {
            int op = rnd.nextInt(10);
            long k = rnd.nextLong();
            if (op < 4) {
                int v = next++ % 1_000_000;
                flat.put(k, v);
                ref.put(k, v);
            } else if (op < 7) {
                boolean expected = ref.remove(k) != null;
                assertEquals(expected, flat.remove(k), "remove mismatch at " + i);
            } else {
                assertEquals(ref.getOrDefault(k, -1), flat.get(k), "get mismatch at " + i);
                assertEquals(ref.containsKey(k), flat.containsKey(k), "contains mismatch at " + i);
            }
        }
        assertEquals(ref.size(), flat.size());
    }

    @Test
    void packedCoordinatesAndEdges() {
        LongLongFlatMap m = new LongLongFlatMap();
        long k1 = LongLongFlatMap.pack(-123456, 987654321);
        assertEquals(-123456, LongLongFlatMap.unpackX(k1));
        assertEquals(987654321, LongLongFlatMap.unpackZ(k1));
        m.put(k1, 7);
        m.put(0L, 0);
        m.put(-1L, 1);
        m.put(Long.MIN_VALUE, 2);
        assertEquals(7, m.get(k1));
        assertEquals(0, m.get(0L));
        assertEquals(1, m.get(-1L));
        assertEquals(2, m.get(Long.MIN_VALUE));
        assertEquals(-1, m.get(42L));
        assertTrue(m.remove(0L));
        assertEquals(-1, m.get(0L));
        assertFalse(m.remove(0L));
        assertEquals(3, m.size());
        for (int i = 0; i < 10_000; i++) {
            m.put(i, i);
        }
        for (int i = 0; i < 10_000; i++) {
            assertEquals(i, m.get(i));
        }
        assertEquals(10_003, m.size());
    }

    @Test
    void clusterStressRemove() {
        LongLongFlatMap m = new LongLongFlatMap(64);
        for (int i = 0; i < 2000; i++) {
            m.put(i, i);
        }
        for (int i = 0; i < 2000; i += 3) {
            assertTrue(m.remove(i));
        }
        for (int i = 0; i < 2000; i++) {
            assertEquals(i % 3 == 0 ? -1 : i, m.get(i));
        }
        assertEquals(2000 - 667, m.size());
    }
}
