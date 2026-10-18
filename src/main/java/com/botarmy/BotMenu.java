package com.botarmy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * เมนูกล่องใหญ่ (6 แถว) เลือกชุดบอท + ตั้งค่า แล้วกดเขียว = เสก / แดง = ยกเลิก
 * กระจกล้อมรอบกดไม่ได้ — ทุกคลิกในเมนูถูกยกเลิกหมด (ดู MenuListener) จึงไม่มีของหลุดออกมา
 */
final class BotMenu implements InventoryHolder {

    enum Result { NONE, CHANGED, CONFIRM, CANCEL }

    static final int SIZE = 54;
    static final int[] KIT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    static final int[] DIFF_SLOTS = {21, 22, 23};
    static final int TOTEM_YES = 29, TOTEM_NO = 30, STACK_YES = 32, STACK_NO = 33;
    static final int[] COUNT_SLOTS = {37, 38, 39, 40, 41, 42, 43};
    static final int[] COUNTS = {1, 5, 10, 15, 20, 30, 50};
    static final int CONFIRM = 48, CANCEL = 50;

    final UUID owner;
    private final Inventory inv;

    KitType kit = KitType.CPVP;
    Difficulty diff = Difficulty.MEDIUM;
    boolean totem = true;
    boolean stack99 = false;
    int count = 5;
    /** true = เมนูนี้ใช้งานจบแล้ว (กดยืนยัน/ยกเลิก/ปิด) คลิกที่เหลือจะถูกเมินทั้งหมด กันกดซ้ำเสกซ้ำ */
    boolean consumed;

    BotMenu(UUID owner) {
        this.owner = owner;
        this.inv = Bukkit.createInventory(this, SIZE, txt("&8Bot by maroneya &7• &8เลือกชุดบอท"));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    void open(Player p) {
        render();
        p.openInventory(inv);
    }

    // ------------------------------------------------------------ click

    Result click(int slot) {
        if (consumed) return Result.NONE;
        KitType[] kits = KitType.values();
        for (int i = 0; i < KIT_SLOTS.length && i < kits.length; i++) {
            if (slot == KIT_SLOTS[i]) {
                kit = kits[i];
                render();
                return Result.CHANGED;
            }
        }
        Difficulty[] ds = Difficulty.values();
        for (int i = 0; i < DIFF_SLOTS.length && i < ds.length; i++) {
            if (slot == DIFF_SLOTS[i]) {
                diff = ds[i];
                render();
                return Result.CHANGED;
            }
        }
        for (int i = 0; i < COUNT_SLOTS.length && i < COUNTS.length; i++) {
            if (slot == COUNT_SLOTS[i]) {
                count = COUNTS[i];
                render();
                return Result.CHANGED;
            }
        }
        switch (slot) {
            case TOTEM_YES -> totem = true;
            case TOTEM_NO -> {
                totem = false;
                stack99 = false; // ไม่ถือโทเทม = ไม่มีสแตก 99 ด้วย
            }
            case STACK_YES -> {
                stack99 = true;
                totem = true;    // สแตก 99 ต้องถือโทเทม
            }
            case STACK_NO -> stack99 = false;
            case CONFIRM -> {
                return Result.CONFIRM;
            }
            case CANCEL -> {
                return Result.CANCEL;
            }
            default -> {
                return Result.NONE;
            }
        }
        render();
        return Result.CHANGED;
    }

    // ------------------------------------------------------------ render

    private static boolean isBorder(int slot) {
        int row = slot / 9, col = slot % 9;
        return row == 0 || row == 5 || col == 0 || col == 8;
    }

    void render() {
        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, isBorder(i) ? border : filler);

        KitType[] kits = KitType.values();
        for (int i = 0; i < KIT_SLOTS.length && i < kits.length; i++) {
            KitType k = kits[i];
            boolean sel = k == kit;
            List<String> lore = new ArrayList<>();
            lore.add("&8ชุดบอท (KIT)");
            for (String l : k.lore) lore.add("&7" + l);
            lore.add("");
            lore.add(sel ? "&a✔ เลือกอยู่" : "&eคลิกเพื่อเลือก");
            inv.setItem(KIT_SLOTS[i], item(k.icon, 1, (sel ? "&a&l" : "&e") + k.display, sel, lore));
        }

        Difficulty[] ds = Difficulty.values();
        for (int i = 0; i < DIFF_SLOTS.length && i < ds.length; i++) {
            Difficulty d = ds[i];
            boolean sel = d == diff;
            inv.setItem(DIFF_SLOTS[i], item(d.icon, 1, (sel ? "&a&l" : "&e") + "ระดับบอท: " + d.display, sel,
                    List.of("&8ระดับความเก่ง", "&7" + d.desc, "", sel ? "&a✔ เลือกอยู่" : "&eคลิกเพื่อเลือก")));
        }

        inv.setItem(TOTEM_YES, item(Material.TOTEM_OF_UNDYING, 1, (totem ? "&a&l" : "&e") + "ถือโทเทม", totem,
                List.of("&8บอทถือโทเทมหรือไม่", "&7โทเทมที่มือซ้าย", "", totem ? "&a✔ เลือกอยู่" : "&eคลิกเพื่อเลือก")));
        inv.setItem(TOTEM_NO, item(Material.BARRIER, 1, (!totem ? "&a&l" : "&e") + "ไม่ถือโทเทม", !totem,
                List.of("&8บอทถือโทเทมหรือไม่", "&7มือซ้ายถือโล่แทน", "", !totem ? "&a✔ เลือกอยู่" : "&eคลิกเพื่อเลือก")));

        ItemStack big = Gear.totem(99);
        decorate(big, (stack99 ? "&a&l" : "&e") + "ถือโทเทมสแตก 99", stack99,
                List.of("&8สแตกโทเทม 99 หรือไม่", "&7ตายแล้วชุบชีวิตได้ต่อเนื่อง", "&7(เลือกอันนี้ = ถือโทเทมอัตโนมัติ)", "",
                        stack99 ? "&a✔ เลือกอยู่" : "&eคลิกเพื่อเลือก"));
        inv.setItem(STACK_YES, big);
        inv.setItem(STACK_NO, item(Material.BARRIER, 1, (!stack99 ? "&a&l" : "&e") + "ไม่ถือสแตก 99", !stack99,
                List.of("&8สแตกโทเทม 99 หรือไม่", "&7จำนวนโทเทมตามระดับบอท", "", !stack99 ? "&a✔ เลือกอยู่" : "&eคลิกเพื่อเลือก")));

        for (int i = 0; i < COUNT_SLOTS.length && i < COUNTS.length; i++) {
            int c = COUNTS[i];
            boolean sel = c == count;
            inv.setItem(COUNT_SLOTS[i], item(Material.PAPER, Math.min(64, c), (sel ? "&a&l" : "&e") + "จำนวนบอท: " + c, sel,
                    List.of("&8จำนวนบอทที่จะเสก", "", sel ? "&a✔ เลือกอยู่" : "&eคลิกเพื่อเลือก")));
        }

        inv.setItem(CONFIRM, item(Material.LIME_DYE, 1, "&a&l✔ เสกบอท", false, List.of(
                "&7ชุด: &f" + kit.display,
                "&7ระดับ: &f" + diff.display,
                "&7โทเทม: &f" + (stack99 ? "สแตก 99" : totem ? "ถือ" : "ไม่ถือ"),
                "&7จำนวน: &f" + count,
                "",
                "&aคลิกเพื่อเสกบอท")));
        inv.setItem(CANCEL, item(Material.RED_DYE, 1, "&c&l✖ ยกเลิก", false, List.of("&7ปิดเมนูโดยไม่เสก")));
    }

    // ------------------------------------------------------------ helpers

    private static Component txt(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(txt(" "));
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack item(Material m, int amount, String name, boolean glow, List<String> lore) {
        ItemStack it = new ItemStack(m, amount);
        decorate(it, name, glow, lore);
        return it;
    }

    private static void decorate(ItemStack it, String name, boolean glow, List<String> lore) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(txt(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(txt(s));
        meta.lore(l);
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
    }
}
