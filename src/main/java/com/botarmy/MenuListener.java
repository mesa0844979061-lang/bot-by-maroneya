package com.botarmy;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** ควบคุมเมนู: ยกเลิกทุกการคลิก/ลาก/ย้ายของในเมนู (กันของหลุด กันบั๊ก) และจัดการปุ่มเขียว/แดง */
public final class MenuListener implements Listener {
    private final BotArmyPlugin plugin;
    private final BotManager mgr;

    MenuListener(BotArmyPlugin plugin, BotManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    static BotMenu menuOf(Inventory top) {
        return top != null && top.getHolder() instanceof BotMenu m ? m : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        BotMenu m = menuOf(e.getView().getTopInventory());
        if (m == null) return;
        e.setCancelled(true); // ทุกคลิก (ทั้งในเมนูและกระเป๋าตัวเอง, shift-click, ปุ่มเลข, ดรอป, สลับมือ) ถูกยกเลิก
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.getUniqueId().equals(m.owner)) {
            m.consumed = true;
            Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            return;
        }
        if (m.consumed) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        ClickType ct = e.getClick();
        if (ct != ClickType.LEFT && ct != ClickType.RIGHT) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= BotMenu.SIZE) return;

        BotMenu.Result r = m.click(slot);
        switch (r) {
            case CHANGED -> p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            case CANCEL -> {
                m.consumed = true;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.closeInventory();
                    plugin.msg(p, "&cยกเลิกแล้ว");
                });
            }
            case CONFIRM -> {
                m.consumed = true; // กันกดรัวแล้วเสกซ้ำ
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.closeInventory();
                    spawn(p, m);
                });
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (menuOf(e.getView().getTopInventory()) != null) e.setCancelled(true);
    }

    /** โหมดครีเอทีฟคลิกของ (InventoryCreativeEvent เป็นอีเวนต์แยก) */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreative(InventoryCreativeEvent e) {
        if (menuOf(e.getView().getTopInventory()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        BotMenu m = menuOf(e.getView().getTopInventory());
        if (m != null) m.consumed = true; // ปิดเมนูเฉยๆ = ยกเลิก ไม่เสก
    }

    private void spawn(Player p, BotMenu m) {
        if (!p.isOnline() || p.isDead()) return;
        if (!p.hasPermission("botarmy.admin")) {
            plugin.msg(p, "&cคุณไม่มีสิทธิ์ใช้คำสั่งนี้");
            return;
        }
        int max = plugin.getConfig().getInt("max-total-bots", 100);
        int room = max - mgr.size();
        if (room <= 0) {
            plugin.msg(p, "&cบอทเต็มแล้ว (" + max + ") ใช้ /debot ก่อน");
            return;
        }
        int n = Math.max(1, Math.min(m.count, room));
        int made = mgr.spawn(p, m.kit, m.diff, m.totem, m.stack99, n);
        plugin.msg(p, "&aเสกบอทชุด &f" + m.kit.display + " &aระดับ &f" + m.diff.display
                + " &aจำนวน &f" + made + " &aตัว"
                + (made < m.count ? " &7(ถูกจำกัดตามจำนวนสูงสุด/พื้นที่)" : ""));
    }
}
