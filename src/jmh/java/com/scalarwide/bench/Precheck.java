package com.scalarwide.bench;

import com.scalarwide.Wide1024;
import com.scalarwide.Wide256;
import com.scalarwide.Wide512;

import com.inf.long2.ints.Int1024;
import com.inf.long2.ints.Int256;
import com.inf.long2.ints.Int512;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Correctness pre-check for the ScalarWide vs Long2 competition.
 *
 * <p>Per scenario and per implementation: 256 inputs from a fixed seed (identical values and
 * order for both sides), compared against a BigInteger reference. Writes
 * {@code correctness.tsv} and {@code cases.tsv}.
 *
 * <p>This describes these samples only; it is not a proof of correctness over all inputs.
 */
public final class Precheck {

    private static final long SEED = 12345L;
    private static final int N = 256;

    private static final StringBuilder CASES = new StringBuilder();
    private static final StringBuilder CORR = new StringBuilder();

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : ".");
        Files.createDirectories(out);

        CORR.append("width\tscenario\timpl\terrors\tmismatches\tsemanticDiffs\texceptions\tfirstCase\n");
        CASES.append("width\tscenario\tinputs\tnote\n");

        for (int width : new int[] {256, 512, 1024}) {
            runWidth(width);
        }

        Files.writeString(out.resolve("correctness.tsv"), CORR.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("cases.tsv"), CASES.toString(), StandardCharsets.UTF_8);
        System.out.println("wrote " + out.toAbsolutePath());
    }

    // ---------------------------------------------------------------- inputs

    /** Values used for binary operations: same array, same order, for both implementations. */
    private static BigInteger[] pool(int width, long salt) {
        Random rnd = new Random(SEED + salt);
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        BigInteger[] v = new BigInteger[N];
        for (int i = 0; i < N; i++) { final int idx = i;
            if (i == 0) {
                v[i] = BigInteger.ZERO;
            } else if (i == 1) {
                v[i] = BigInteger.ONE;
            } else if (i == 2) {
                v[i] = mask;
            } else if (i == 3) {
                v[i] = BigInteger.ONE.shiftLeft(width - 1);
            } else {
                v[i] = new BigInteger(width, rnd).and(mask);
            }
        }
        return v;
    }

    private static void runWidth(int width) {
        BigInteger[] a = pool(width, 1);
        BigInteger[] b = pool(width, 2);
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);

        checkBinary(width, "add", a, b, (x, y) -> x.add(y).and(mask));
        checkBinary(width, "subtract", a, b, (x, y) -> x.subtract(y).and(mask));
        checkBinary(width, "multiply", a, b, (x, y) -> x.multiply(y).and(mask));
        checkBinary(width, "and", a, b, BigInteger::and);
        checkBinary(width, "or", a, b, BigInteger::or);
        checkBinary(width, "xor", a, b, BigInteger::xor);
        checkUnary(width, "not", a, x -> x.xor(mask));
        checkUnary(width, "negate", a, x -> x.negate().and(mask));
        checkShiftLeft(width, "shiftLeft", a, 7);
        checkShiftRight(width, "shiftRight", a, 7);
        checkCompare(width, "compareTo", a, b);
        checkEquals(width, "equals", a, b);
        checkHash(width, "hashCode", a);
        checkBytes(width, "toBytes", a);
        checkFromBytes(width, "fromBytes", a);
        checkString(width, "toString", a);
        checkHex(width, "toHexString", a);
        checkToLong(width, "toLong", a);
        checkBitLength(width, "bitLength", a);
        checkOfLong(width, "ofLong", a);

        CASES.append(width).append("\tall\t").append(N).append("\tseed=").append(SEED).append('\n');
    }

    // ---------------------------------------------------------------- checks

    private interface Bin {
        BigInteger apply(BigInteger x, BigInteger y);
    }

    private interface Un {
        BigInteger apply(BigInteger x);
    }

    private static void checkBinary(int width, String name, BigInteger[] a, BigInteger[] b, Bin ref) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) { final int idx = i;
            BigInteger want = ref.apply(a[idx], b[idx]);
            String ws = safe(() -> wideBinary(width, name, a[idx], b[idx]));
            String ls = safe(() -> longBinary(width, name, a[idx], b[idx]));
            score(0, want.toString(), ws, e, x, first, width, name, i);
            score(1, want.toString(), ls, e, x, first, width, name, i);
        }
        emit(width, name, e, x, first);
    }

    private static void checkUnary(int width, String name, BigInteger[] a, Un ref) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) { final int idx = i;
            BigInteger want = ref.apply(a[idx]);
            String ws = safe(() -> wideUnary(width, name, a[idx]));
            String ls = safe(() -> longUnary(width, name, a[idx]));
            score(0, want.toString(), ws, e, x, first, width, name, i);
            score(1, want.toString(), ls, e, x, first, width, name, i);
        }
        emit(width, name, e, x, first);
    }

    private static void checkShiftLeft(int width, String name, BigInteger[] a, int k) {
        checkShift(width, name, a, k, true);
    }

    private static void checkShiftRight(int width, String name, BigInteger[] a, int k) {
        checkShift(width, name, a, k, false);
    }

    /**
     * ScalarWide exposes {@code shl(k)} / {@code shr(k)}; Long2 exposes a single
     * {@code shift(k)} where positive is left (verified on the 0.1.8 jar). Reference is the
     * wrapping left shift, or the arithmetic (sign-propagating) right shift.
     */
    private static void checkShift(int width, String name, BigInteger[] a, int k, boolean left) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        for (int i = 0; i < N; i++) {
            final int idx = i;
            String want = (left
                    ? a[idx].shiftLeft(k).and(mask)
                    : signed(a[idx], width).shiftRight(k).and(mask)).toString();
            String ws = safe(() -> wideShift(width, a[idx], left ? k : -k));
            String ls = safe(() -> longShift(width, a[idx], left ? k : -k));
            score(0, want, ws, e, x, first, width, name, idx);
            score(1, want, ls, e, x, first, width, name, idx);
        }
        emit(width, name, e, x, first);
    }

    private static void checkCompare(int width, String name, BigInteger[] a, BigInteger[] b) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) { final int idx = i;
            BigInteger sa = signed(a[idx], width);
            BigInteger sb = signed(b[idx], width);
            String want = Integer.toString(Integer.signum(sa.compareTo(sb)));
            String ws = safe(() -> Integer.toString(Integer.signum(
                    wideSignum(width, a[idx], b[idx]))));
            String ls = safe(() -> Integer.toString(Integer.signum(
                    longSignum(width, a[idx], b[idx]))));
            score(0, want, ws, e, x, first, width, name, i);
            score(1, want, ls, e, x, first, width, name, i);
        }
        emit(width, name, e, x, first);
    }

    private static void checkEquals(int width, String name, BigInteger[] a, BigInteger[] b) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) { final int idx = i;
            String want = Boolean.toString(a[idx].equals(b[idx]));
            String ws = safe(() -> Boolean.toString(wideEquals(width, a[idx], b[idx])));
            String ls = safe(() -> Boolean.toString(longEquals(width, a[idx], b[idx])));
            score(0, want, ws, e, x, first, width, name, i);
            score(1, want, ls, e, x, first, width, name, i);
        }
        emit(width, name, e, x, first);
    }

    /** Hash quality is not judged; only that equal values hash equally across freshly built objects. */
    private static void checkHash(int width, String name, BigInteger[] a) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) { final int idx = i;
            String want = "self-consistent";
            String ws = safe(() -> {
                int h1 = wideHash(width, a[idx]);
                int h2 = wideHash(width, a[idx]);
                return h1 == h2 ? want : "unstable:" + h1 + "/" + h2;
            });
            String ls = safe(() -> {
                int h1 = longHash(width, a[idx]);
                int h2 = longHash(width, a[idx]);
                return h1 == h2 ? want : "unstable:" + h1 + "/" + h2;
            });
            score(0, want, ws, e, x, first, width, name, i);
            score(1, want, ls, e, x, first, width, name, i);
        }
        emit(width, name, e, x, first);
    }

    private static void checkBytes(int width, String name, BigInteger[] a) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) { final int idx = i;
            String want = hex(a[idx], width);
            String ws = safe(() -> hexOfBytes(wideToBytes(width, a[idx]), width));
            String ls = safe(() -> hexOfBytes(longToBytes(width, a[idx]), width));
            score(0, want, ws, e, x, first, width, name, i);
            score(1, want, ls, e, x, first, width, name, i);
        }
        emit(width, name, e, x, first);
    }

    private static void checkFromBytes(int width, String name, BigInteger[] a) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) { final int idx = i;
            byte[] raw = toFixedBe(a[idx], width);
            String want = a[idx].toString();
            String ws = safe(() -> wideFromBytes(width, raw).toString());
            String ls = safe(() -> longFromBytes(width, raw).toString());
            score(0, want, ws, e, x, first, width, name, i);
            score(1, want, ls, e, x, first, width, name, i);
        }
        emit(width, name, e, x, first);
    }

    private static void checkString(int width, String name, BigInteger[] a) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) {
            final int idx = i;
            String wantWide = a[idx].toString();
            String wantLong = signed(a[idx], width).toString();
            String ws = safe(() -> wideToString(width, a[idx]));
            String ls = safe(() -> longToString(width, a[idx]));
            score(0, wantWide, ws, e, x, first, width, name, idx);
            score(1, wantLong, ls, e, x, first, width, name, idx);
        }
        emit(width, name, e, x, first);
    }

    private static void checkHex(int width, String name, BigInteger[] a) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) {
            final int idx = i;
            String wantWide = a[idx].toString(16);
            String wantLong = "0x" + hex(signed(a[idx], width).and(BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE)), width);
            String ws = safe(() -> wideToHex(width, a[idx]));
            String ls = safe(() -> longToHex(width, a[idx]));
            score(0, wantWide, ws, e, x, first, width, name, idx);
            score(1, wantLong, ls, e, x, first, width, name, idx);
        }
        emit(width, name, e, x, first);
    }

    private static void checkToLong(int width, String name, BigInteger[] a) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) { final int idx = i;
            String want = Long.toString(a[idx].longValue());
            String ws = safe(() -> Long.toString(wideToLong(width, a[idx])));
            String ls = safe(() -> Long.toString(longToLong(width, a[idx])));
            score(0, want, ws, e, x, first, width, name, i);
            score(1, want, ls, e, x, first, width, name, i);
        }
        emit(width, name, e, x, first);
    }

    private static void checkBitLength(int width, String name, BigInteger[] a) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        for (int i = 0; i < N; i++) {
            final int idx = i;
            String wantWide = Integer.toString(a[idx].bitLength());
            String wantLong = Integer.toString(signed(a[idx], width).bitLength());
            String ws = safe(() -> Integer.toString(wideBitLength(width, a[idx])));
            String ls = safe(() -> Integer.toString(longBitLength(width, a[idx])));
            score(0, wantWide, ws, e, x, first, width, name, idx);
            score(1, wantLong, ls, e, x, first, width, name, idx);
        }
        emit(width, name, e, x, first);
    }

    private static void checkOfLong(int width, String name, BigInteger[] a) {
        long[] e = new long[2];
        long[] x = new long[2];
        String[] first = new String[2];
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        for (int i = 0; i < N; i++) {
            final int idx = i;
            long v = a[idx].longValue();
            // of(long) zero-extends the 64-bit pattern, so mask at 64 bits (not at the width).
            String wantWide = BigInteger.valueOf(v).and(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)).toString();
            String wantLong = BigInteger.valueOf(v).toString();
            String ws = safe(() -> wideOfLong(width, v));
            String ls = safe(() -> longOfLong(width, v));
            score(0, wantWide, ws, e, x, first, width, name, idx);
            score(1, wantLong, ls, e, x, first, width, name, idx);
        }
        emit(width, name, e, x, first);
    }

    // ---------------------------------------------------------------- scoring

    private static void score(int impl, String want, String got, long[] errors, long[] exceptions,
                              String[] first, int width, String name, int i) {
        if (got.startsWith("EX:")) {
            exceptions[impl]++;
            if (first[impl] == null) {
                first[impl] = "i=" + i + " " + got;
            }
            return;
        }
        if (!got.equals(want)) {
            errors[impl]++;
            if (first[impl] == null) {
                first[impl] = "i=" + i + " expected=" + abbreviate(want) + " actual=" + abbreviate(got);
            }
        }
    }

    private static void emit(int width, String name, long[] errors, long[] exceptions, String[] first) {
        for (int impl = 0; impl < 2; impl++) {
            CORR.append(width).append('\t').append(name).append('\t')
                    .append(impl == 0 ? "scalarwide" : "long2").append('\t')
                    .append(errors[impl]).append('\t').append(0).append('\t').append(0).append('\t')
                    .append(exceptions[impl]).append('\t')
                    .append(first[impl] == null ? "" : first[impl].replace('\t', ' '))
                    .append('\n');
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 48 ? s : s.substring(0, 24) + "..." + s.substring(s.length() - 20);
    }

    private interface Sup {
        String get() throws Exception;
    }

    private static String safe(Sup s) {
        try {
            return s.get();
        } catch (Throwable t) {
            return "EX:" + t.getClass().getSimpleName() + ":" + t.getMessage();
        }
    }

    // ---------------------------------------------------------------- helpers

    static BigInteger signed(BigInteger v, int width) {
        return v.testBit(width - 1) ? v.subtract(BigInteger.ONE.shiftLeft(width)) : v;
    }

    static String hex(BigInteger v, int width) {
        String s = v.toString(16);
        int digits = width / 4;
        StringBuilder sb = new StringBuilder(digits);
        for (int i = s.length(); i < digits; i++) {
            sb.append('0');
        }
        return sb.append(s).toString();
    }

    static byte[] toFixedBe(BigInteger v, int width) {
        String h = hex(v, width);
        byte[] out = new byte[width / 8];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    static String hexOfBytes(byte[] b, int width) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        }
        String s = sb.toString();
        int digits = width / 4;
        return s.length() >= digits ? s.substring(s.length() - digits) : s;
    }

    // ---------------------------------------------------------------- dispatch

    static String wideBinary(int width, String op, BigInteger x, BigInteger y) {
        return switch (width) {
            case 256 -> {
                Wide256 a = Wide256.fromBigInteger(x);
                Wide256 b = Wide256.fromBigInteger(y);
                Wide256 r = switch (op) {
                    case "add" -> a.add(b);
                    case "subtract" -> a.sub(b);
                    case "multiply" -> a.mul(b);
                    case "and" -> a.and(b);
                    case "or" -> a.or(b);
                    default -> a.xor(b);
                };
                yield r.toBigInteger().toString();
            }
            case 512 -> {
                Wide512 a = Wide512.fromBigInteger(x);
                Wide512 b = Wide512.fromBigInteger(y);
                Wide512 r = switch (op) {
                    case "add" -> a.add(b);
                    case "subtract" -> a.sub(b);
                    case "multiply" -> a.mul(b);
                    case "and" -> a.and(b);
                    case "or" -> a.or(b);
                    default -> a.xor(b);
                };
                yield r.toBigInteger().toString();
            }
            default -> {
                Wide1024 a = Wide1024.fromBigInteger(x);
                Wide1024 b = Wide1024.fromBigInteger(y);
                Wide1024 r = switch (op) {
                    case "add" -> a.add(b);
                    case "subtract" -> a.sub(b);
                    case "multiply" -> a.mul(b);
                    case "and" -> a.and(b);
                    case "or" -> a.or(b);
                    default -> a.xor(b);
                };
                yield r.toBigInteger().toString();
            }
        };
    }

    static String longBinary(int width, String op, BigInteger x, BigInteger y) {
        return switch (width) {
            case 256 -> {
                Int256 a = Int256.of(toLimbs(x, 4));
                Int256 b = Int256.of(toLimbs(y, 4));
                Int256 r = switch (op) {
                    case "add" -> a.add(b);
                    case "subtract" -> a.subtract(b);
                    case "multiply" -> a.multiply(b);
                    case "and" -> a.and(b);
                    case "or" -> a.or(b);
                    default -> a.xor(b);
                };
                yield unsigned(r.toBytes()).toString();
            }
            case 512 -> {
                Int512 a = Int512.of(toLimbs(x, 8));
                Int512 b = Int512.of(toLimbs(y, 8));
                Int512 r = switch (op) {
                    case "add" -> a.add(b);
                    case "subtract" -> a.subtract(b);
                    case "multiply" -> a.multiply(b);
                    case "and" -> a.and(b);
                    case "or" -> a.or(b);
                    default -> a.xor(b);
                };
                yield unsigned(r.toBytes()).toString();
            }
            default -> {
                Int1024 a = Int1024.of(toLimbs(x, 16));
                Int1024 b = Int1024.of(toLimbs(y, 16));
                Int1024 r = switch (op) {
                    case "add" -> a.add(b);
                    case "subtract" -> a.subtract(b);
                    case "multiply" -> a.multiply(b);
                    case "and" -> a.and(b);
                    case "or" -> a.or(b);
                    default -> a.xor(b);
                };
                yield unsigned(r.toBytes()).toString();
            }
        };
    }

    /** Long2 stores the most significant limb first; {@code toBytes} is big-endian two's complement. */
    static long[] toLimbs(BigInteger v, int limbs) {
        long[] out = new long[limbs];
        for (int i = 0; i < limbs; i++) {
            out[limbs - 1 - i] = v.shiftRight(64 * i).longValue();
        }
        return out;
    }

    static BigInteger unsigned(byte[] be) {
        return new BigInteger(1, be);
    }

    static String wideUnary(int width, String op, BigInteger x) {
        return switch (width) {
            case 256 -> {
                Wide256 a = Wide256.fromBigInteger(x);
                yield (op.equals("not") ? a.not() : a.neg()).toBigInteger().toString();
            }
            case 512 -> {
                Wide512 a = Wide512.fromBigInteger(x);
                yield (op.equals("not") ? a.not() : a.neg()).toBigInteger().toString();
            }
            default -> {
                Wide1024 a = Wide1024.fromBigInteger(x);
                yield (op.equals("not") ? a.not() : a.neg()).toBigInteger().toString();
            }
        };
    }

    static String longUnary(int width, String op, BigInteger x) {
        return switch (width) {
            case 256 -> {
                Int256 a = Int256.of(toLimbs(x, 4));
                yield unsigned((op.equals("not") ? a.not() : a.negate()).toBytes()).toString();
            }
            case 512 -> {
                Int512 a = Int512.of(toLimbs(x, 8));
                yield unsigned((op.equals("not") ? a.not() : a.negate()).toBytes()).toString();
            }
            default -> {
                Int1024 a = Int1024.of(toLimbs(x, 16));
                yield unsigned((op.equals("not") ? a.not() : a.negate()).toBytes()).toString();
            }
        };
    }
    // ---------------------------------------------------------------- per-op dispatch (cont.)

    static String wideShift(int width, BigInteger x, int k) {
        return switch (width) {
            case 256 -> (k >= 0 ? Wide256.fromBigInteger(x).shl(k) : Wide256.fromBigInteger(x).shr(-k))
                    .toBigInteger().toString();
            case 512 -> (k >= 0 ? Wide512.fromBigInteger(x).shl(k) : Wide512.fromBigInteger(x).shr(-k))
                    .toBigInteger().toString();
            default -> (k >= 0 ? Wide1024.fromBigInteger(x).shl(k) : Wide1024.fromBigInteger(x).shr(-k))
                    .toBigInteger().toString();
        };
    }

    static String longShift(int width, BigInteger x, int k) {
        return switch (width) {
            case 256 -> unsigned(Int256.of(toLimbs(x, 4)).shift(k).toBytes()).toString();
            case 512 -> unsigned(Int512.of(toLimbs(x, 8)).shift(k).toBytes()).toString();
            default -> unsigned(Int1024.of(toLimbs(x, 16)).shift(k).toBytes()).toString();
        };
    }

    static int wideSignum(int width, BigInteger x, BigInteger y) {
        return switch (width) {
            case 256 -> Integer.signum(Wide256.fromBigInteger(x).compareSigned(Wide256.fromBigInteger(y)));
            case 512 -> Integer.signum(Wide512.fromBigInteger(x).compareSigned(Wide512.fromBigInteger(y)));
            default -> Integer.signum(Wide1024.fromBigInteger(x).compareSigned(Wide1024.fromBigInteger(y)));
        };
    }

    static int longSignum(int width, BigInteger x, BigInteger y) {
        return switch (width) {
            case 256 -> Integer.signum(Int256.of(toLimbs(x, 4)).compareTo(Int256.of(toLimbs(y, 4))));
            case 512 -> Integer.signum(Int512.of(toLimbs(x, 8)).compareTo(Int512.of(toLimbs(y, 8))));
            default -> Integer.signum(Int1024.of(toLimbs(x, 16)).compareTo(Int1024.of(toLimbs(y, 16))));
        };
    }

    static boolean wideEquals(int width, BigInteger x, BigInteger y) {
        return switch (width) {
            case 256 -> Wide256.fromBigInteger(x).equals(Wide256.fromBigInteger(y));
            case 512 -> Wide512.fromBigInteger(x).equals(Wide512.fromBigInteger(y));
            default -> Wide1024.fromBigInteger(x).equals(Wide1024.fromBigInteger(y));
        };
    }

    static boolean longEquals(int width, BigInteger x, BigInteger y) {
        return switch (width) {
            case 256 -> Int256.of(toLimbs(x, 4)).equals(Int256.of(toLimbs(y, 4)));
            case 512 -> Int512.of(toLimbs(x, 8)).equals(Int512.of(toLimbs(y, 8)));
            default -> Int1024.of(toLimbs(x, 16)).equals(Int1024.of(toLimbs(y, 16)));
        };
    }

    static int wideHash(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Wide256.fromBigInteger(x).hashCode();
            case 512 -> Wide512.fromBigInteger(x).hashCode();
            default -> Wide1024.fromBigInteger(x).hashCode();
        };
    }

    static int longHash(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Int256.of(toLimbs(x, 4)).hashCode();
            case 512 -> Int512.of(toLimbs(x, 8)).hashCode();
            default -> Int1024.of(toLimbs(x, 16)).hashCode();
        };
    }

    static byte[] wideToBytes(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Wide256.fromBigInteger(x).toByteArrayFixed();
            case 512 -> Wide512.fromBigInteger(x).toByteArrayFixed();
            default -> Wide1024.fromBigInteger(x).toByteArrayFixed();
        };
    }

    static byte[] longToBytes(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Int256.of(toLimbs(x, 4)).toBytes();
            case 512 -> Int512.of(toLimbs(x, 8)).toBytes();
            default -> Int1024.of(toLimbs(x, 16)).toBytes();
        };
    }

    static BigInteger wideFromBytes(int width, byte[] raw) {
        return switch (width) {
            case 256 -> Wide256.fromByteArray(raw, 0, raw.length).toBigInteger();
            case 512 -> Wide512.fromByteArray(raw, 0, raw.length).toBigInteger();
            default -> Wide1024.fromByteArray(raw, 0, raw.length).toBigInteger();
        };
    }

    static BigInteger longFromBytes(int width, byte[] raw) {
        return switch (width) {
            case 256 -> unsigned(Int256.of(raw).toBytes());
            case 512 -> unsigned(Int512.of(raw).toBytes());
            default -> unsigned(Int1024.of(raw).toBytes());
        };
    }

    static String wideToString(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Wide256.fromBigInteger(x).toString();
            case 512 -> Wide512.fromBigInteger(x).toString();
            default -> Wide1024.fromBigInteger(x).toString();
        };
    }

    static String longToString(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Int256.of(toLimbs(x, 4)).toString();
            case 512 -> Int512.of(toLimbs(x, 8)).toString();
            default -> Int1024.of(toLimbs(x, 16)).toString();
        };
    }

    static String wideToHex(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Wide256.fromBigInteger(x).toHexString();
            case 512 -> Wide512.fromBigInteger(x).toHexString();
            default -> Wide1024.fromBigInteger(x).toHexString();
        };
    }

    static String longToHex(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Int256.of(toLimbs(x, 4)).toHexString();
            case 512 -> Int512.of(toLimbs(x, 8)).toHexString();
            default -> Int1024.of(toLimbs(x, 16)).toHexString();
        };
    }

    static long wideToLong(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Wide256.fromBigInteger(x).longValue();
            case 512 -> Wide512.fromBigInteger(x).longValue();
            default -> Wide1024.fromBigInteger(x).longValue();
        };
    }

    static long longToLong(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Int256.of(toLimbs(x, 4)).toLong();
            case 512 -> Int512.of(toLimbs(x, 8)).toLong();
            default -> Int1024.of(toLimbs(x, 16)).toLong();
        };
    }

    static int wideBitLength(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Wide256.fromBigInteger(x).bitLength();
            case 512 -> Wide512.fromBigInteger(x).bitLength();
            default -> Wide1024.fromBigInteger(x).bitLength();
        };
    }

    static int longBitLength(int width, BigInteger x) {
        return switch (width) {
            case 256 -> Int256.of(toLimbs(x, 4)).bitLength();
            case 512 -> Int512.of(toLimbs(x, 8)).bitLength();
            default -> Int1024.of(toLimbs(x, 16)).bitLength();
        };
    }

    static String wideOfLong(int width, long v) {
        return switch (width) {
            case 256 -> Wide256.of(v).toString();
            case 512 -> Wide512.of(v).toString();
            default -> Wide1024.of(v).toString();
        };
    }

    static String longOfLong(int width, long v) {
        return switch (width) {
            case 256 -> Int256.of(v).toString();
            case 512 -> Int512.of(v).toString();
            default -> Int1024.of(v).toString();
        };
    }
}