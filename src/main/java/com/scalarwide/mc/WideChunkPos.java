package com.scalarwide.mc;

import java.math.BigInteger;

/**
 * Dedicated 2D far-domain chunk position: X and Z are 1024-bit signed chunk
 * coordinates. Y is intentionally absent; section Y belongs to section keys,
 * not to {@code ChunkPos}.
 */
public final class WideChunkPos {

    public static final int BYTES = 2 * (WideChunkCoord1024.BITS / 8);

    private final WideChunkCoord1024 x;
    private final WideChunkCoord1024 z;
    private final int hash;

    private WideChunkPos(WideChunkCoord1024 x, WideChunkCoord1024 z) {
        this.x = x;
        this.z = z;
        this.hash = 31 * x.hashCode() + z.hashCode();
    }

    public static WideChunkPos of(long x, long z) {
        return new WideChunkPos(WideChunkCoord1024.of(x), WideChunkCoord1024.of(z));
    }

    /** Compatibility bridge for old {@code WideChunkKey.pack(int,int)} callers. */
    public static WideChunkPos pack(int x, int z) {
        return of(x, z);
    }

    /** Compatibility bridge for old {@code WideChunkKey.pack(long,long)} callers. */
    public static WideChunkPos pack(long x, long z) {
        return of(x, z);
    }

    /** Low 64 bits of X for legacy chunk-map code. */
    public static long x(WideChunkPos key) {
        return key.xLong();
    }

    /** Low 64 bits of Z for legacy chunk-map code. */
    public static long z(WideChunkPos key) {
        return key.zLong();
    }

    public static WideChunkPos neighbor(WideChunkPos key, int dx, int dz) {
        return key.neighbor(dx, dz);
    }

    public static WideChunkPos neighbor(WideChunkPos key, long dx, long dz) {
        return key.neighbor(dx, dz);
    }

    public static WideChunkPos of(BigInteger x, BigInteger z) {
        return new WideChunkPos(WideChunkCoord1024.of(x), WideChunkCoord1024.of(z));
    }

    public static WideChunkPos fromByteArray(byte[] bytes) {
        if (bytes.length != BYTES) {
            throw new IllegalArgumentException("expected " + BYTES + " bytes");
        }
        byte[] xb = new byte[WideChunkCoord1024.BITS / 8];
        byte[] zb = new byte[WideChunkCoord1024.BITS / 8];
        System.arraycopy(bytes, 0, xb, 0, xb.length);
        System.arraycopy(bytes, xb.length, zb, 0, zb.length);
        return new WideChunkPos(WideChunkCoord1024.fromByteArray(xb),
            WideChunkCoord1024.fromByteArray(zb));
    }

    public WideChunkCoord1024 x() {
        return x;
    }

    public WideChunkCoord1024 z() {
        return z;
    }

    /** Low 64 bits of X; compatibility bridge only. */
    public long xLong() {
        return x.limb(0);
    }

    /** Low 64 bits of Z; compatibility bridge only. */
    public long zLong() {
        return z.limb(0);
    }

    public WideChunkPos neighbor(long dx, long dz) {
        return new WideChunkPos(x.neighbor(dx), z.neighbor(dz));
    }

    public WideRegionPos region() {
        return WideRegionPos.of(this);
    }

    public int localX() {
        return (int) (x.limb(0) & 31L);
    }

    public int localZ() {
        return (int) (z.limb(0) & 31L);
    }

    public byte[] toByteArray() {
        byte[] out = new byte[BYTES];
        System.arraycopy(x.toByteArray(), 0, out, 0, WideChunkCoord1024.BITS / 8);
        System.arraycopy(z.toByteArray(), 0, out, WideChunkCoord1024.BITS / 8,
            WideChunkCoord1024.BITS / 8);
        return out;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WideChunkPos other
            && x.equals(other.x) && z.equals(other.z);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + z + ")";
    }
}
