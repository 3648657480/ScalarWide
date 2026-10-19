package com.scalarwide.mc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;

class WideSectionKey2560LayoutTest {

    private static WideSectionKey2560 key() {
        return WideSectionKey2560.of(BigInteger.ONE.shiftLeft(1020), BigInteger.ZERO,
            BigInteger.ONE.shiftLeft(1020).negate());
    }

    @Test
    void keyUsesOneFlatLimbArray() {
        String footprint = GraphLayout.parseInstance(key()).toFootprint();
        System.out.println("[JOL] WideSectionKey2560\n" + footprint);
        org.junit.jupiter.api.Assertions.assertTrue(footprint.contains("[J"), footprint);
    }

    @Test
    void mapMetadataIsOneTagArray() {
        WideSectionKey2560FlatMap map = new WideSectionKey2560FlatMap(16);
        map.put(key(), "value");
        String footprint = GraphLayout.parseInstance(map).toFootprint();
        System.out.println("[JOL] WideSectionKey2560FlatMap(1 key)\n" + footprint);
        org.junit.jupiter.api.Assertions.assertTrue(footprint.contains("[I"), footprint);
        org.junit.jupiter.api.Assertions.assertTrue(footprint.contains("WideSectionKey2560"), footprint);
    }
}
