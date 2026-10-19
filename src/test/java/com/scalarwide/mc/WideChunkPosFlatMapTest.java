package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WideChunkPosFlatMapTest {

    private static final BigInteger BASE = BigInteger.ONE.shiftLeft(1018);

    private static WideChunkPos key(BigInteger dx, BigInteger dz) {
        return WideChunkPos.of(BASE.add(dx), BASE.negate().add(dz));
    }

    @Test
    void differentialAgainstHashMap() {
        Random rnd = new Random(0xC4A0F1A7L);
        WideChunkPosFlatMap map = new WideChunkPosFlatMap();
        Map<WideChunkPos, Object> ref = new HashMap<>();
        for (int i = 0; i < 12_000; i++) {
            WideChunkPos k = key(BigInteger.valueOf(rnd.nextInt(256) - 128),
                BigInteger.valueOf(rnd.nextInt(256) - 128));
            int op = rnd.nextInt(100);
            if (op < 60) {
                int v = rnd.nextInt();
                assertEquals(ref.put(k, v), map.put(k, v), "put @" + i);
            } else if (op < 85) {
                assertEquals(ref.remove(k), map.remove(k), "remove @" + i);
            } else {
                assertEquals(ref.get(k), map.get(k), "get @" + i);
                assertEquals(ref.containsKey(k), map.containsKey(k), "contains @" + i);
            }
            assertEquals(ref.size(), map.size(), "size @" + i);
        }
        for (Map.Entry<WideChunkPos, Object> e : ref.entrySet()) {
            assertEquals(e.getValue(), map.get(e.getKey()));
            assertTrue(map.containsKey(e.getKey()));
        }
    }

    @Test
    void cloneIsIndependentAndSnapshotsAreComplete() {
        WideChunkPosFlatMap map = new WideChunkPosFlatMap(16);
        WideChunkPos a = key(BigInteger.ZERO, BigInteger.ZERO);
        WideChunkPos b = key(BigInteger.ONE, BigInteger.ONE);
        map.put(a, "a");
        map.put(b, "b");
        WideChunkPosFlatMap copy = map.clone();
        assertNotSame(map, copy);
        assertEquals(2, copy.size());
        assertEquals("a", copy.get(a));
        assertEquals(2, map.keys().size());
        assertEquals(2, map.values().size());

        copy.remove(a);
        assertEquals("a", map.get(a));
        assertNull(copy.get(a));
        map.clear();
        assertEquals(0, map.size());
        assertEquals(1, copy.size());
    }

    @Test
    void nullValueRemovesAndConditionalRemove() {
        WideChunkPosFlatMap map = new WideChunkPosFlatMap();
        WideChunkPos k = key(BigInteger.ZERO, BigInteger.ZERO);
        map.put(k, "v");
        assertFalse(map.remove(k, "other"));
        assertTrue(map.remove(k, "v"));
        assertTrue(map.isEmpty());

        map.put(k, "w");
        assertEquals("w", map.put(k, null));
        assertFalse(map.containsKey(k));
    }

    @Test
    void clusterRemovalKeepsLaterLookupsCorrect() {
        WideChunkPosFlatMap map = new WideChunkPosFlatMap(16);
        WideChunkPos[] keys = new WideChunkPos[512];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = key(BigInteger.valueOf(i), BigInteger.valueOf(-i));
            map.put(keys[i], "v" + i);
        }
        for (int i = 0; i < keys.length; i += 2) {
            assertEquals("v" + i, map.remove(keys[i]));
        }
        assertEquals(256, map.size());
        for (int i = 0; i < keys.length; i++) {
            if ((i & 1) == 0) {
                assertNull(map.get(keys[i]));
            } else {
                assertEquals("v" + i, map.get(keys[i]));
            }
        }
    }
}
