package com.botarmy;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

final class Gear {
    private Gear() {}

    static final class Kit {
        ItemStack helmet, chest, legs, boots, sword, mace, axe, shield, elytra, bow, crossbow;
    }

    static Kit build(Spec s, boolean unb) {
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

        if (!s.axeOnly && !s.maceMain) {
            k.sword = mk(s.swordMat, unb);
            en(k.sword, Enchantment.SHARPNESS, s.sharp);
            en(k.sword, Enchantment.UNBREAKING, s.full ? 3 : 2);
            if (s.full) {
                if (s.fireAspect) en(k.sword, Enchantment.FIRE_ASPECT, 2);
                en(k.sword, Enchantment.LOOTING, 3);
                en(k.sword, Enchantment.SWEEPING_EDGE, 3);
                en(k.sword, Enchantment.MENDING, 1);
            }
        }

        if (s.mace) {
            k.mace = mk(Material.MACE, unb);
            en(k.mace, Enchantment.DENSITY, s.density);
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
            en(k.axe, Enchantment.SHARPNESS, s.axeSharp);
            en(k.axe, Enchantment.EFFICIENCY, 5);
            en(k.axe, Enchantment.UNBREAKING, 3);
            if (s.axeOnly) en(k.axe, Enchantment.MENDING, 1);
        }
        if (s.bow) {
            k.bow = mk(Material.BOW, unb);
            en(k.bow, Enchantment.POWER, 5);
            en(k.bow, Enchantment.PUNCH, 2);
            en(k.bow, Enchantment.FLAME, 1);
            en(k.bow, Enchantment.INFINITY, 1);
            en(k.bow, Enchantment.UNBREAKING, 3);
            k.crossbow = mk(Material.CROSSBOW, unb);
            en(k.crossbow, Enchantment.QUICK_CHARGE, 3);
            en(k.crossbow, Enchantment.PIERCING, 4);
            en(k.crossbow, Enchantment.UNBREAKING, 3);
            en(k.crossbow, Enchantment.MENDING, 1);
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

    static ItemStack gapple(Spec s, int amount) {
        return new ItemStack(s.enchantedApples ? Material.ENCHANTED_GOLDEN_APPLE : Material.GOLDEN_APPLE,
                Math.max(1, Math.min(64, amount)));
    }

    /** ของประกอบ kit (ช่องกระเป๋าหลัก ไม่กระทบแถบด่วน) */
    static void extras(KitType kit, PlayerInventory inv) {
        if (kit == KitType.CUTPVP) {
            inv.setItem(9, new ItemStack(Material.ARROW, 64));
        }
    }

    static ItemStack flint(boolean unb) {
        return mk(Material.FLINT_AND_STEEL, unb);
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
