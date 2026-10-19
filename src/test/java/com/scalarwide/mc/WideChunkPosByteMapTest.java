package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WideChunkPosByteMapTest {

    private static final BigInteger BASE = BigInteger.ONE.shiftLeft(1018);
    private static final byte FALLBACK = (byte) -1;

    private static WideChunkPos key(BigInteger dx, BigInteger dz) {
        return WideChunkPos.of(BASE.add(dx), BASE.negate().add(dz));
    }

    @Test
    void differentialAgainstHashMap() {
        Random rnd = new Random(0xB17EC4A0L);
        WideChunkPosByteMap map = new WideChunkPosByteMap();
        Map<WideChunkPos, Byte> ref = new HashMap<>();
        for (int i = 0; i < 12_000; i++) {
            WideChunkPos k = key(BigInteger.valueOf(rnd.nextInt(256) - 128),
                BigInteger.valueOf(rnd.nextInt(256) - 128));
            int op = rnd.nextInt(100);
            if (op < 60) {
                byte v = (byte) rnd.nextInt();
                assertEquals(ref.getOrDefault(k, FALLBACK), map.put(k, v, FALLBACK));
                ref.put(k, v);
            } else if (op < 85) {
                assertEquals(ref.getOrDefault(k, FALLBACK), map.remove(k, FALLBACK));
                ref.remove(k);
            } else {
                assertEquals(ref.getOrDefault(k, FALLBACK), map.getOrDefault(k, FALLBACK));
                assertEquals(ref.containsKey(k), map.containsKey(k));
            }
            assertEquals(ref.size(), map.size());
        }
    }

    @Test
    void growthAndKeysSnapshot() {
        WideChunkPosByteMap map = new WideChunkPosByteMap(16);
        for (int i = 0; i < 4096; i++) {
            map.put(key(BigInteger.valueOf(i), BigInteger.valueOf(-i)),
                (byte) (i & 0x7F), FALLBACK);
        }
        assertEquals(4096, map.size());
        assertEquals(4096, map.keys().size());
        assertEquals((byte) 0, map.getOrDefault(key(BigInteger.ZERO, BigInteger.ZERO), FALLBACK));
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    void clusterRemovalKeepsLookupsCorrect() {
        WideChunkPosByteMap map = new WideChunkPosByteMap(16);
        WideChunkPos[] keys = new WideChunkPos[512];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = key(BigInteger.valueOf(i), BigInteger.valueOf(-i));
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
            assertFalse(present && map.getOrDefault(keys[i], FALLBACK) != (byte) (i & 0x7F));
        }
    }
}
