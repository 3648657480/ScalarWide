package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WideSectionKey2560ByteMapTest {

    private static final BigInteger BASE = BigInteger.ONE.shiftLeft(1018);
    private static final byte FALLBACK = (byte) -1;

    private static WideSectionKey2560 key(BigInteger dx, BigInteger dy, BigInteger dz) {
        return WideSectionKey2560.of(BASE.add(dx), dy, BASE.negate().add(dz));
    }

    @Test
    void differentialAgainstHashMap() {
        Random rnd = new Random(0xB17E2560L);
        WideSectionKey2560ByteMap map = new WideSectionKey2560ByteMap();
        Map<WideSectionKey2560, Byte> ref = new HashMap<>();

        for (int i = 0; i < 20_000; i++) {
            BigInteger dx = BigInteger.valueOf(rnd.nextInt(256) - 128);
            BigInteger dy = BigInteger.valueOf(rnd.nextInt(1024) - 512);
            BigInteger dz = BigInteger.valueOf(rnd.nextInt(256) - 128);
            WideSectionKey2560 k = key(dx, dy, dz);
            int op = rnd.nextInt(100);
            if (op < 60) {
                byte v = (byte) rnd.nextInt();
                assertEquals(ref.getOrDefault(k, FALLBACK),
                    map.put(k, v, FALLBACK), "put @" + i);
                ref.put(k, v);
            } else if (op < 85) {
                assertEquals(ref.getOrDefault(k, FALLBACK),
                    map.remove(k, FALLBACK), "remove @" + i);
                ref.remove(k);
            } else {
                assertEquals(ref.getOrDefault(k, FALLBACK),
                    map.getOrDefault(k, FALLBACK), "get @" + i);
                assertEquals(ref.containsKey(k), map.containsKey(k), "contains @" + i);
            }
            assertEquals(ref.size(), map.size(), "size @" + i);
        }

        for (Map.Entry<WideSectionKey2560, Byte> e : ref.entrySet()) {
            assertTrue(map.containsKey(e.getKey()));
            assertEquals(e.getValue(), Byte.valueOf(map.getOrDefault(e.getKey(), FALLBACK)));
        }
    }

    @Test
    void growthAndKeysSnapshot() {
        WideSectionKey2560ByteMap map = new WideSectionKey2560ByteMap(16);
        for (int i = 0; i < 4096; i++) {
            map.put(key(BigInteger.valueOf(i), BigInteger.valueOf(i % 17),
                BigInteger.valueOf(-i)), (byte) (i & 0x7F), FALLBACK);
        }
        assertEquals(4096, map.size());
        assertEquals(4096, map.keys().size());
        for (int i = 0; i < 4096; i += 512) {
            WideSectionKey2560 k = key(BigInteger.valueOf(i), BigInteger.valueOf(i % 17),
                BigInteger.valueOf(-i));
            assertEquals((byte) (i & 0x7F), map.getOrDefault(k, FALLBACK));
        }
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertTrue(map.keys().isEmpty());
    }

    @Test
    void clusterRemovalKeepsLookupsCorrect() {
        WideSectionKey2560ByteMap map = new WideSectionKey2560ByteMap();
        WideSectionKey2560[] keys = new WideSectionKey2560[512];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = key(BigInteger.valueOf(i), BigInteger.valueOf(i % 17),
                BigInteger.valueOf(-i));
            map.put(keys[i], (byte) (i & 0x7F), FALLBACK);
        }
        for (int i = 0; i < keys.length; i += 2) {
            assertEquals((byte) (i & 0x7F), map.remove(keys[i], FALLBACK));
            assertEquals(FALLBACK, map.remove(keys[i], FALLBACK));
        }
        assertEquals(256, map.size());
        for (int i = 0; i < keys.length; i++) {
            boolean present = (i & 1) == 1;
            assertEquals(present, map.containsKey(keys[i]));
            if (!present) {
                assertEquals(FALLBACK, map.getOrDefault(keys[i], FALLBACK));
            }
        }
    }
}
