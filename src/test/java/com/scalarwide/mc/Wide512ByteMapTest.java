package com.scalarwide.mc;

import com.scalarwide.Wide512;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Wide512ByteMapTest {

    private static Wide512 key(long x, long z) {
        return WideChunkKey.pack(x, z);
    }

    @Test
    void differentialAgainstHashMap() {
        Random rnd = new Random(12345);
        Wide512ByteMap map = new Wide512ByteMap();
        Map<Wide512, Byte> ref = new HashMap<>();
        long base = 1L << 40;

        for (int i = 0; i < 20_000; i++) {
            long x = base + rnd.nextInt(128) - 64;
            long z = -base + rnd.nextInt(128) - 64;
            Wide512 k = key(x, z);
            int op = rnd.nextInt(100);
            if (op < 60) {
                byte v = (byte) rnd.nextInt();
                assertEquals(ref.getOrDefault(k, (byte) -1), map.put(k, v, (byte) -1), "put @ " + i);
                ref.put(k, v);
            } else if (op < 85) {
                assertEquals(ref.getOrDefault(k, (byte) -1), map.remove(k, (byte) -1), "remove @ " + i);
                ref.remove(k);
            } else {
                assertEquals(ref.getOrDefault(k, (byte) -1), map.getOrDefault(k, (byte) -1), "get @ " + i);
                assertEquals(ref.containsKey(k), map.containsKey(k), "contains @ " + i);
            }
            assertEquals(ref.size(), map.size(), "size @ " + i);
        }

        for (Map.Entry<Wide512, Byte> e : ref.entrySet()) {
            assertEquals(e.getValue(), map.getOrDefault(e.getKey(), (byte) -1));
        }
    }

    @Test
    void growthAndKeysSnapshot() {
        Wide512ByteMap map = new Wide512ByteMap(16);
        for (long i = 0; i < 4096; i++) {
            map.put(key(i, -i), (byte) (i & 0x7F), (byte) -1);
        }
        assertEquals(4096, map.size());
        assertEquals(4096, map.keys().size());
        for (long i = 0; i < 4096; i += 512) {
            assertEquals((byte) (i & 0x7F), map.getOrDefault(key(i, -i), (byte) -1));
        }
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void clusterRemovalKeepsLookupsCorrect() {
        Wide512ByteMap map = new Wide512ByteMap();
        for (long i = 0; i < 512; i++) {
            map.put(key(i, i), (byte) (i & 0x7F), (byte) -1);
        }
        for (long i = 0; i < 512; i += 2) {
            assertEquals((byte) (i & 0x7F), map.remove(key(i, i), (byte) -1));
        }
        assertEquals(256, map.size());
        for (long i = 0; i < 512; i++) {
            boolean present = i % 2 == 1;
            assertEquals(present, map.containsKey(key(i, i)));
            assertFalse(present && map.getOrDefault(key(i, i), (byte) -1) != (byte) (i & 0x7F));
        }
    }
}
