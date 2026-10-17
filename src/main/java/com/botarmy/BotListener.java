package com.botarmy;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

public final class BotListener implements Listener {
    private final BotArmyPlugin plugin;
    private final BotManager mgr;

    BotListener(BotArmyPlugin plugin, BotManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        ArmyBot victim = mgr.get(e.getEntity());
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;

        // บอทไม่ตีกันเอง
        if (victim != null && mgr.get(damager) != null) {
            e.setCancelled(true);
            return;
        }
        if (victim == null) return;
        if (damager instanceof Player ap) victim.onHurt(ap);

        DamageCause c = e.getCause();
        if (c == DamageCause.ENTITY_ATTACK || c == DamageCause.ENTITY_SWEEP_ATTACK || c == DamageCause.PROJECTILE) {
            if (victim.tryBlock(damager)) e.setCancelled(true);
        }
    }

    /** บอทตายแล้วดรอปของ (ตั้งค่าได้ที่ drop-items) — ถ้า Citizens ไม่สร้างดรอปให้ จะดรอปจากช่องเก็บของเองกันของหาย */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent e) {
        ArmyBot bot = mgr.get(e.getEntity());
        if (bot == null) return;
        boolean drop = plugin.getConfig().getBoolean("drop-items", true);
        if (!drop) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        } else {
            if (e instanceof PlayerDeathEvent pde) pde.setKeepInventory(false);
            if (e.getDrops().isEmpty() && e.getEntity() instanceof Player p) {
                Location at = p.getLocation();
                for (ItemStack it : bot.collectDrops(p)) {
                    if (p.getWorld() != null) p.getWorld().dropItemNaturally(at, it);
                }
            }
        }
        if (e instanceof PlayerDeathEvent pde) pde.deathMessage(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (mgr.get(e.getEntity()) != null) e.setCancelled(true);
    }

    /** Pearl ของบอท: วาร์ปบอทเองเพราะ fake connection ไม่รับ teleport จากเพิร์ลตามปกติ */
    @EventHandler(ignoreCancelled = true)
    public void onPearl(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player sp)) return;
        ArmyBot bot = mgr.get(sp);
        if (bot == null) return;
        e.setCancelled(true);
        Location dest = pearl.getLocation();
        BlockFace face = e.getHitBlockFace();
        if (face == BlockFace.DOWN) dest.add(0, -1.8, 0);
        else if (face != null && face != BlockFace.UP) dest.add(face.getDirection().multiply(0.6));
        pearl.remove();
        bot.pearlLanded(dest);
    }
}
