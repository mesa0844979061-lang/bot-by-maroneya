package com.botarmy;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** บล็อกชั่วคราวที่บอทวาง (ใยแมงมุม / ลาวา / น้ำ) — จำค่าเดิมไว้แล้วคืนให้เมื่อหมดเวลา ไม่ทำให้แมพพัง */
final class Traps {

    private record Key(UUID w, int x, int y, int z) {}

    private static final class Saved {
        final Block block;
        final BlockData original;
        final Material placed;
        long expire;

        Saved(Block block, BlockData original, Material placed, long expire) {
            this.block = block;
            this.original = original;
            this.placed = placed;
            this.expire = expire;
        }
    }

    private final Map<Key, Saved> saved = new HashMap<>();

    private static Key key(Block b) {
        return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }

    /** วางบล็อกชั่วคราว (ทับได้เฉพาะอากาศ/หญ้า/บล็อกที่ทับได้ ไม่ทับของแข็งหรือของเหลว) */
    boolean set(Block b, Material m, long expireAt) {
        return set(b, m, expireAt, true);
    }

    /** physics=false ใช้กับราง (ไม่ให้เด้งหลุดเพราะอัปเดตบล็อกข้างเคียง) */
    boolean set(Block b, Material m, long expireAt, boolean physics) {
        Key k = key(b);
        Saved s = saved.get(k);
        if (s != null) {
            s.expire = Math.max(s.expire, expireAt);
            return true;
        }
        Material cur = b.getType();
        if (!(cur.isAir() || (b.isReplaceable() && !b.isLiquid()))) return false;
        saved.put(k, new Saved(b, b.getBlockData(), m, expireAt));
        b.setType(m, physics);
        return true;
    }

    boolean isTracked(Block b) {
        return saved.containsKey(key(b));
    }

    /** คืนบล็อกทันที (ใช้ตอนบอทตัดใยแมงมุม) */
    boolean release(Block b) {
        Saved s = saved.remove(key(b));
        if (s == null) return false;
        restore(s);
        return true;
    }

    void tick(long now) {
        if (saved.isEmpty()) return;
        for (Iterator<Saved> it = saved.values().iterator(); it.hasNext(); ) {
            Saved s = it.next();
            if (now >= s.expire) {
                it.remove();
                restore(s);
            }
        }
    }

    void clearAll() {
        for (Saved s : saved.values()) restore(s);
        saved.clear();
    }

    private void restore(Saved s) {
        try {
            Material cur = s.block.getType();
            boolean liquid = s.placed == Material.LAVA || s.placed == Material.WATER;
            boolean ours = cur == s.placed
                    || (liquid && (cur == Material.OBSIDIAN || cur == Material.COBBLESTONE
                    || cur == Material.LAVA || cur == Material.WATER));
            if (ours) s.block.setBlockData(s.original, true);
        } catch (Throwable ignored) {
        }
    }
}
