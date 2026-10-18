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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        if (victim == null) return;
        // รถระเบิด/เอนทิตีของบอท ไม่ทำร้ายบอทด้วยกัน
        if (mgr.isBotEntity(e.getDamager())) {
            e.setCancelled(true);
            return;
        }
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;

        // บอทไม่ตีกันเอง (ดาเมจจากคริสตัลที่ตัวเองระเบิดใส่ตัวเอง ปล่อยผ่านเพื่อให้บอทต้องคำนวณความปลอดภัย)
        ArmyBot atk = mgr.get(damager);
        if (atk != null) {
            if (atk != victim) e.setCancelled(true);
            return;
        }
        if (damager instanceof Player ap) victim.onHurt(ap);

        DamageCause c = e.getCause();
        if (c == DamageCause.ENTITY_ATTACK || c == DamageCause.ENTITY_SWEEP_ATTACK || c == DamageCause.PROJECTILE) {
            if (victim.tryBlock(damager)) e.setCancelled(true);
        }
    }

    /** ระเบิดจากรถระเบิดของบอท: ไม่ทำลายบล็อก (ปิดได้ที่ cart-block-damage: true) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!mgr.isBotEntity(e.getEntity())) return;
        if (!plugin.getConfig().getBoolean("cart-block-damage", false)) e.blockList().clear();
    }

    /** ผู้เล่นแตกโทเทม → บอท CPVP รีบวางคริสตัลซ้ำ */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        if (e.getEntity() instanceof Player p && mgr.get(p) == null) mgr.notePop(p);
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
            e.getDrops().removeIf(it -> it != null && ArmyBot.isUtility(it.getType()));
            if (e.getDrops().isEmpty() && e.getEntity() instanceof Player p) {
                Location at = p.getLocation();
                for (ItemStack it : bot.collectDrops(p)) {
                    if (p.getWorld() != null) p.getWorld().dropItemNaturally(at, it);
                }
            }
        }
        if (e instanceof PlayerDeathEvent pde) pde.deathMessage(null);
    }

    private boolean isBot(Player p) {
        return mgr.get(p) != null || p.hasMetadata("NPC");
    }

    /** ปิดข้อความ join/leave ของระบบกับบอท (ปลั๊กอินประกาศเองแค่ครั้งเดียว ไม่ให้ซ้อนกับข้อความตาย) */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotQuit(PlayerQuitEvent e) {
        if (isBot(e.getPlayer())) e.quitMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotJoin(PlayerJoinEvent e) {
        if (isBot(e.getPlayer())) e.joinMessage(null);
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
