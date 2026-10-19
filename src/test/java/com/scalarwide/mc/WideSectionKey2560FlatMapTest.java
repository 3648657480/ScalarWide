package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WideSectionKey2560FlatMapTest {

    private static final BigInteger BASE = BigInteger.ONE.shiftLeft(1018);

    private static WideSectionKey2560 key(BigInteger dx, BigInteger dy, BigInteger dz) {
        return WideSectionKey2560.of(BASE.add(dx), dy, BASE.negate().add(dz));
    }

    private static int resizeAt(WideSectionKey2560FlatMap map) throws ReflectiveOperationException {
        Field field = WideSectionKey2560FlatMap.class.getDeclaredField("resizeAt");
        field.setAccessible(true);
        return field.getInt(map);
    }

    @Test
    void loadPercentControlsThresholdAndDefaultsTo75() throws ReflectiveOperationException {
        WideSectionKey2560FlatMap defaults = new WideSectionKey2560FlatMap();
        assertEquals(1024, defaults.capacity());
        assertEquals(768, resizeAt(defaults));
        WideSectionKey2560FlatMap sized = new WideSectionKey2560FlatMap(16);
        assertEquals(32, sized.capacity());
        assertEquals(24, resizeAt(sized));
        for (int percent : new int[] {50, 75, 90}) {
            WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap(16, percent);
            assertEquals(32, map.capacity());
            assertEquals((int) ((long) map.capacity() * percent / 100), resizeAt(map));
            assertTrue(resizeAt(map) >= 1 && resizeAt(map) < map.capacity());
        }
    }

    @Test
    void rejectsLoadPercentOutsideRange() {
        for (int percent : new int[] {Integer.MIN_VALUE, -1, 0, 49, 91, 100, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                () -> new WideSectionKey2560FlatMap(16, percent));
        }
    }

    @Test
    void growthRetainsLoadPercentAndContents() throws ReflectiveOperationException {
        for (int percent : new int[] {50, 75, 90}) {
            WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap(16, percent);
            int threshold = resizeAt(map);
            for (int i = 0; i < threshold; i++) {
                map.put(WideSectionKey2560.of(i, -i, i), i);
            }
            assertEquals(32, map.capacity());
            map.put(WideSectionKey2560.of(threshold, -threshold, threshold), threshold);
            assertEquals(64, map.capacity());
            assertEquals((int) (64L * percent / 100), resizeAt(map));
            for (int i = 0; i <= threshold; i++) {
                assertEquals(i, map.get(WideSectionKey2560.of(i, -i, i)));
            }
        }
    }

    @Test
    void cloneRetainsLoadPercentAndGrowsIndependently() throws ReflectiveOperationException {
        WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap(16, 50);
        for (int i = 0; i < 16; i++) {
            map.put(WideSectionKey2560.of(i, 0, 0), i);
        }
        WideSectionKey2560FlatMap copy = map.clone();
        assertEquals(32, copy.capacity());
        assertEquals(16, resizeAt(copy));
        copy.put(WideSectionKey2560.of(16, 0, 0), 16);
        assertEquals(64, copy.capacity());
        assertEquals(32, resizeAt(copy));
        assertEquals(32, map.capacity());
        assertEquals(16, map.size());
        assertNull(map.get(WideSectionKey2560.of(16, 0, 0)));
        for (int i = 0; i < 16; i++) {
            assertEquals(i, copy.get(WideSectionKey2560.of(i, 0, 0)));
        }
    }

    @Test
    void configurableLoadPercentMatchesHashMapUnderChurn() {
        for (int percent : new int[] {50, 75, 90}) {
            Random rnd = new Random(12345);
            WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap(16, percent);
            Map<WideSectionKey2560, Object> ref = new HashMap<>();
            for (int i = 0; i < 4000; i++) {
                int id = rnd.nextInt(128);
                WideSectionKey2560 k = key(BigInteger.valueOf(id),
                    BigInteger.valueOf(id % 17), BigInteger.valueOf(-id));
                switch (rnd.nextInt(3)) {
                    case 0 -> assertEquals(ref.put(k, i), map.put(k, i));
                    case 1 -> assertEquals(ref.remove(k), map.remove(k));
                    default -> assertEquals(ref.get(k), map.get(k));
                }
                assertEquals(ref.size(), map.size());
                assertEquals(ref.containsKey(k), map.containsKey(k));
            }
            ref.forEach((k, value) -> assertEquals(value, map.get(k)));
        }
    }

    @Test
    void differentialAgainstHashMap() {
        Random rnd = new Random(0x5EED2560L);
        WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap();
        Map<WideSectionKey2560, Object> ref = new HashMap<>();

        for (int i = 0; i < 12_000; i++) {
            BigInteger dx = BigInteger.valueOf(rnd.nextInt(256) - 128);
            BigInteger dy = BigInteger.valueOf(rnd.nextInt(1024) - 512);
            BigInteger dz = BigInteger.valueOf(rnd.nextInt(256) - 128);
            WideSectionKey2560 k = key(dx, dy, dz);
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

        for (Map.Entry<WideSectionKey2560, Object> e : ref.entrySet()) {
            assertEquals(e.getValue(), map.get(e.getKey()));
            assertTrue(map.containsKey(e.getKey()));
        }
    }

    @Test
    void everyAxisAndLimbParticipates() {
        WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap();
        WideSectionKey2560 x0 = key(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
        WideSectionKey2560 x1 = key(BigInteger.ONE, BigInteger.ZERO, BigInteger.ZERO);
        WideSectionKey2560 y1 = key(BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO);
        WideSectionKey2560 z1 = key(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ONE);
        WideSectionKey2560 all = key(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE);
        map.put(x0, "x0");
        map.put(x1, "x1");
        map.put(y1, "y1");
        map.put(z1, "z1");
        map.put(all, "all");
        assertEquals(5, map.size());
        assertEquals("x0", map.get(x0));
        assertEquals("x1", map.get(x1));
        assertEquals("y1", map.get(y1));
        assertEquals("z1", map.get(z1));
        assertEquals("all", map.get(all));
        assertNull(map.get(key(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE)));
    }

    @Test
    void cloneIsIndependentAndValuesSnapshot() {
        WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap();
        WideSectionKey2560 a = key(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
        WideSectionKey2560 b = key(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE);
        WideSectionKey2560 c = key(BigInteger.TWO, BigInteger.TWO, BigInteger.TWO);
        map.put(a, "a");
        map.put(b, "b");
        WideSectionKey2560FlatMap copy = map.clone();
        assertNotSame(map, copy);
        assertEquals(2, copy.size());
        assertEquals("a", copy.get(a));

        map.put(c, "c");
        assertEquals(3, map.size());
        assertEquals(2, copy.size());
        assertNull(copy.get(c));

        copy.remove(a);
        assertEquals("a", map.get(a));
        assertEquals(3, map.values().size());
    }

    @Test
    void nullValueRemovesAndConditionalRemove() {
        WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap();
        WideSectionKey2560 k = key(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
        map.put(k, "v");
        assertFalse(map.remove(k, "other"));
        assertTrue(map.remove(k, "v"));
        assertTrue(map.isEmpty());

        map.put(k, "w");
        assertEquals("w", map.put(k, null));
        assertFalse(map.containsKey(k));
        assertEquals(0, map.size());
    }

    @Test
    void removeClusterKeepsLaterLookupsCorrect() {
        WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap();
        WideSectionKey2560[] keys = new WideSectionKey2560[512];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = key(BigInteger.valueOf(i), BigInteger.valueOf(i % 17),
                BigInteger.valueOf(-i));
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
        map.clear();
        assertEquals(0, map.size());
        assertNull(map.get(keys[1]));
    }
}
