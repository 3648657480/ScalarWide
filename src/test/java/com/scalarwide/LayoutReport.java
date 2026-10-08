package com.scalarwide;

import org.openjdk.jol.info.ClassLayout;

public final class LayoutReport {

    private LayoutReport() {
    }

    public static void main(String[] args) {
        System.out.println(ClassLayout.parseClass(Wide128.class).toPrintable());
        System.out.println(ClassLayout.parseClass(Wide256.class).toPrintable());
        System.out.println(ClassLayout.parseClass(Wide384.class).toPrintable());
        System.out.println(ClassLayout.parseClass(Wide512.class).toPrintable());
        System.out.println(ClassLayout.parseClass(Wide768.class).toPrintable());
        System.out.println(ClassLayout.parseClass(Wide1024.class).toPrintable());
    }
}
