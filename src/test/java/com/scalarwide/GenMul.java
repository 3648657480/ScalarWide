package com.scalarwide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates fully unrolled schoolbook {@code mul} / {@code mulHigh} method
 * bodies for the wide classes (one flat method per operation, no calls to the
 * atom products). Output goes to {@code build/gen/}; splice with
 * {@code tools/splice-mul.ps1}.
 */
public final class GenMul {

    private static final int[] WIDTHS = {512, 768, 1024};

    private GenMul() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of("build", "gen");
        Files.createDirectories(dir);
        for (int w : WIDTHS) {
            int limbs = w / 64;
            Files.writeString(dir.resolve("Wide" + w + ".mul.gen.java"), generateMul(w, limbs));
            Files.writeString(dir.resolve("Wide" + w + ".mulHigh.gen.java"), generateMulHigh(w, limbs));
            System.out.println("generated Wide" + w + " (" + limbs + " limbs)");
        }
    }

    private static String generateMul(int width, int limbs) {
        List<String> products = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        build(width, limbs, limbs - 1, columns, products);
        return wrap(width, "mul", "Low " + width + " bits of the " + (2 * width)
                + "-bit product, i.e. {@code (a*b) mod 2^" + width + "}."
                + " Fully unrolled schoolbook (no atom calls).", limbs,
                range(0, limbs - 1), products, columns);
    }

    private static String generateMulHigh(int width, int limbs) {
        List<String> products = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        build(width, limbs, 2 * limbs - 1, columns, products);
        return wrap(width, "mulHigh", "High " + width + " bits of the " + (2 * width)
                + "-bit product, i.e. {@code (a*b) >>> " + width + "}."
                + " Fully unrolled schoolbook (no atom calls).", limbs,
                range(limbs, 2 * limbs - 1), products, columns);
    }

    private static int[] range(int lo, int hi) {
        int[] r = new int[hi - lo + 1];
        for (int i = 0; i < r.length; i++) {
            r[i] = lo + i;
        }
        return r;
    }

    private static void build(int width, int limbs, int lastColumn,
                              List<String> columns, List<String> products) {
        for (int t = 0; t <= lastColumn; t++) {
            StringBuilder c = new StringBuilder();
            c.append("        {\n");
            c.append("            long s;\n");
            c.append("            long k = 0;\n");
            List<String> terms = new ArrayList<>();
            for (int u = 0; u < limbs; u++) {
                int v = t - u;
                if (v >= 0 && v < limbs) {
                    terms.add("p" + u + "_" + v);
                }
            }
            for (int u = 0; u < limbs; u++) {
                int v = t - 1 - u;
                if (v >= 0 && v < limbs) {
                    terms.add("h" + u + "_" + v);
                }
            }
            boolean first = true;
            for (String term : terms) {
                if (first) {
                    c.append("            s = ").append(term).append(";\n");
                    first = false;
                } else {
                    c.append("            {\n");
                    c.append("                long r = s + ").append(term).append(";\n");
                    c.append("                k += Carry.addCarryOut(s, ").append(term).append(", r);\n");
                    c.append("                s = r;\n");
                    c.append("            }\n");
                }
            }
            c.append("            {\n");
            c.append("                long r = s + carry;\n");
            c.append("                k += Carry.addCarryOut(s, carry, r);\n");
            c.append("                c").append(t).append(" = r;\n");
            c.append("                carry = k;\n");
            c.append("            }\n");
            c.append("        }\n");
            columns.add(c.toString());
            addProductsFor(products, limbs, t);
        }
    }

    private static void addProductsFor(List<String> products, int limbs, int t) {
        for (int u = 0; u < limbs; u++) {
            int v = t - u;
            if (v >= 0 && v < limbs) {
                products.add("        long p" + u + "_" + v + " = a" + u + " * b" + v + ";\n");
            }
        }
        for (int u = 0; u < limbs; u++) {
            int v = t - 1 - u;
            if (v >= 0 && v < limbs) {
                products.add("        long h" + u + "_" + v
                        + " = Math.unsignedMultiplyHigh(a" + u + ", b" + v + ");\n");
            }
        }
    }

    private static String wrap(int width, String name, String doc, int limbs,
                               int[] returnColumns, List<String> products, List<String> columns) {
        StringBuilder b = new StringBuilder();
        b.append("    /**\n");
        b.append("     * ").append(doc).append("\n");
        b.append("     */\n");
        b.append("    public Wide").append(width).append(' ').append(name)
                .append("(Wide").append(width).append(" o) {\n");
        for (int i = 0; i < limbs; i++) {
            b.append("        long a").append(i).append(" = l").append(i).append(";\n");
        }
        for (int i = 0; i < limbs; i++) {
            b.append("        long b").append(i).append(" = o.limb(").append(i).append(");\n");
        }
        b.append("        long carry = 0;\n");
        for (int i = 0; i <= 2 * limbs - 1; i++) {
            b.append("        long c").append(i).append(";\n");
        }
        b.append("\n");
        b.append("        // products are computed once and reused by both their lo/hi columns\n");
        for (String p : products) {
            b.append(p);
        }
        b.append("\n");
        for (String c : columns) {
            b.append(c);
        }
        b.append("\n");
        b.append("        return Wide").append(width).append(".of(");
        for (int i = 0; i < returnColumns.length; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append("c").append(returnColumns[i]);
        }
        b.append(");\n");
        b.append("    }\n");
        return b.toString();
    }
}
