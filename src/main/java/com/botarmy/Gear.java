package com.botarmy;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class Gear {
    private Gear() {}

    static final class Kit {
        ItemStack helmet, chest, legs, boots, sword, mace, axe, shield, elytra;
    }

    static Kit build(Level lv, boolean unb) {
        Level.Spec s = lv.spec;
        String t = s.armorTier;
        int p = s.prot;
        Kit k = new Kit();

        k.helmet = mk(Material.valueOf(t + "_HELMET"), unb);
        en(k.helmet, Enchantment.PROTECTION, p);
        en(k.helmet, Enchantment.UNBREAKING, 3);
        if (s.full) {
            en(k.helmet, Enchantment.MENDING, 1);
            en(k.helmet, Enchantment.RESPIRATION, 3);
            en(k.helmet, Enchantment.AQUA_AFFINITY, 1);
        }

        k.chest = mk(Material.valueOf(t + "_CHESTPLATE"), unb);
        en(k.chest, Enchantment.PROTECTION, p);
        en(k.chest, Enchantment.UNBREAKING, 3);
        if (s.full) {
            en(k.chest, Enchantment.MENDING, 1);
        }

        k.legs = mk(Material.valueOf(t + "_LEGGINGS"), unb);
        en(k.legs, Enchantment.PROTECTION, p);
        en(k.legs, Enchantment.UNBREAKING, 3);
        if (s.full) {
            en(k.legs, Enchantment.MENDING, 1);
            en(k.legs, Enchantment.SWIFT_SNEAK, 3);
        }

        k.boots = mk(Material.valueOf(t + "_BOOTS"), unb);
        en(k.boots, Enchantment.PROTECTION, p);
        en(k.boots, Enchantment.UNBREAKING, 3);
        if (s.mace) en(k.boots, Enchantment.FEATHER_FALLING, 4);
        if (s.full) {
            en(k.boots, Enchantment.MENDING, 1);
            en(k.boots, Enchantment.DEPTH_STRIDER, 3);
            en(k.boots, Enchantment.SOUL_SPEED, 3);
        }

        k.sword = mk(s.swordMat, unb);
        en(k.sword, Enchantment.SHARPNESS, s.sharp);
        en(k.sword, Enchantment.UNBREAKING, s.full ? 3 : 2);
        if (s.full) {
            en(k.sword, Enchantment.FIRE_ASPECT, 2);
            en(k.sword, Enchantment.LOOTING, 3);
            en(k.sword, Enchantment.SWEEPING_EDGE, 3);
            en(k.sword, Enchantment.MENDING, 1);
        }

        if (s.mace) {
            k.mace = mk(Material.MACE, unb);
            en(k.mace, Enchantment.DENSITY, s.full ? 5 : 3);
            en(k.mace, Enchantment.BREACH, s.full ? 4 : 2);
            en(k.mace, Enchantment.WIND_BURST, s.full ? 3 : 1);
            en(k.mace, Enchantment.UNBREAKING, 3);
            if (s.full) {
                en(k.mace, Enchantment.FIRE_ASPECT, 2);
                en(k.mace, Enchantment.MENDING, 1);
            }
        }
        if (s.axe) {
            k.axe = mk(Material.NETHERITE_AXE, unb);
            en(k.axe, Enchantment.SHARPNESS, s.full ? 5 : 4);
            en(k.axe, Enchantment.EFFICIENCY, 5);
            en(k.axe, Enchantment.UNBREAKING, 3);
        }
        k.shield = mk(Material.SHIELD, unb);
        en(k.shield, Enchantment.UNBREAKING, 3);
        if (s.full) en(k.shield, Enchantment.MENDING, 1);

        if (s.elytra) {
            k.elytra = mk(Material.ELYTRA, unb);
            en(k.elytra, Enchantment.UNBREAKING, 3);
            en(k.elytra, Enchantment.MENDING, 1);
        }
        return k;
    }

    /** โทเทมสแตกใหญ่ (ตั้ง max_stack_size = 99) */
    static ItemStack totem(int amount) {
        ItemStack t = new ItemStack(Material.TOTEM_OF_UNDYING, 1);
        ItemMeta m = t.getItemMeta();
        m.setMaxStackSize(99);
        t.setItemMeta(m);
        t.setAmount(Math.max(1, Math.min(99, amount)));
        return t;
    }

    static ItemStack gapple(Level lv, int amount) {
        return new ItemStack(lv.spec.enchantedApples ? Material.ENCHANTED_GOLDEN_APPLE : Material.GOLDEN_APPLE,
                Math.max(1, Math.min(64, amount)));
    }

    private static ItemStack mk(Material m, boolean unb) {
        ItemStack i = new ItemStack(m);
        if (unb) {
            ItemMeta meta = i.getItemMeta();
            meta.setUnbreakable(true);
            i.setItemMeta(meta);
        }
        return i;
    }

    private static void en(ItemStack i, Enchantment e, int lvl) {
        if (lvl > 0) i.addUnsafeEnchantment(e, lvl);
    }
}
