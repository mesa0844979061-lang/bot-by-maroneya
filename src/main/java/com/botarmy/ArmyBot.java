package com.botarmy;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.UUID;

/** บอท 1 ตัว: ถือ NPC ผู้เล่นของ Citizens + สมองต่อสู้ */
public final class ArmyBot {

    static final int S_SWORD = 0, S_MACE = 1, S_AXE = 2, S_GAPPLE = 3, S_PEARL = 4, S_WIND = 5, S_SHIELD = 6, S_TOTEM = 7;

    enum State { PATROL, ATTACK, RETREAT, FROZEN }

    private enum Combo { NONE, RISING, GLIDE, DIVE }

    private enum Weapon { SWORD, AXE }

    private static final Method START_USING = find("startUsingItem", EquipmentSlot.class);
    private static final Method CLEAR_ACTIVE = find("clearActiveItem");

    private static Method find(String n, Class<?>... a) {
        try {
            return LivingEntity.class.getMethod(n, a);
        } catch (Throwable t) {
            return null;
        }
    }

    final BotManager mgr;
    final NPC npc;
    final String name;
    final Level level;
    final Level.Spec spec;
    final boolean totemBot;
    private final Random rnd = new Random();

    State state = State.PATROL;
    boolean frozen, hadSpawned, equipped;
    long respawnAt;
    int slot;

    private Combo combo = Combo.NONE;
    private boolean shieldUp, eating, jumpPending;
    private long shieldDownUntil, shieldDisabledUntil, shieldHoldUntil;
    private long nextGappleAt, nextPearlAt, nextComboAt, eatEndsAt, lastEatSound, comboStart;
    private long lastAttack, lastHurt, retreatSince, nextStrafe, jumpAt, lastEnemyTick;
    private int gapples, pearls, strafeDir = 1;
    private double peakY, enemyVy, lastEnemyY;
    private Player lastEnemy, lastAttacker;
    private Location navLast;
    private Entity navEntity;
    private Gear.Kit kit;
    private ItemStack savedOffhand, savedChest;

    ArmyBot(BotManager mgr, NPC npc, String name, Level level) {
        this.mgr = mgr;
        this.npc = npc;
        this.name = name;
        this.level = level;
        this.spec = level.spec;
        this.gapples = spec.gapples;
        this.pearls = spec.pearls;
        this.totemBot = spec.totemCount > 0 && rnd.nextDouble() < spec.totemChance;
    }

    // ---------------------------------------------------------------- basics

    Player player() {
        Entity e = npc.getEntity();
        return e instanceof Player p ? p : null;
    }

    boolean isSpawned() {
        return npc.isSpawned() && player() != null;
    }

    UUID uuid() {
        Entity e = npc.getEntity();
        return e == null ? null : e.getUniqueId();
    }

    void destroy() {
        try {
            npc.destroy();
        } catch (Throwable ignored) {
        }
    }

    boolean respawn(Location loc) {
        if (!npc.spawn(loc)) return false;
        equipped = false;
        combo = Combo.NONE;
        eating = false;
        shieldUp = false;
        state = State.PATROL;
        navLast = null;
        navEntity = null;
        gapples = spec.gapples;
        pearls = spec.pearls;
        respawnAt = 0;
        hadSpawned = true;
        return true;
    }

    @SuppressWarnings("deprecation")
    private boolean onGround(Player p) {
        return p.isOnGround() || p.getLocation().clone().subtract(0, 0.2, 0).getBlock().getType().isSolid();
    }

    @SuppressWarnings("deprecation")
    private double maxHealth(Player p) {
        return Math.max(1, p.getMaxHealth());
    }

    private boolean canShield() {
        return spec.shield > 0 && (!totemBot || level == Level.MAX);
    }

    // ---------------------------------------------------------------- equipment

    void equip(Player p) {
        boolean unb = mgr.plugin().getConfig().getBoolean("unbreakable-gear", true);
        kit = Gear.build(level, unb);
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(kit.helmet);
        inv.setChestplate(kit.chest);
        inv.setLeggings(kit.legs);
        inv.setBoots(kit.boots);
        inv.setItem(S_SWORD, kit.sword);
        if (spec.mace) inv.setItem(S_MACE, kit.mace);
        if (spec.axe) inv.setItem(S_AXE, kit.axe);
        if (gapples > 0) inv.setItem(S_GAPPLE, Gear.gapple(level, gapples));
        if (pearls > 0) inv.setItem(S_PEARL, new ItemStack(Material.ENDER_PEARL, Math.min(16, pearls)));
        if (spec.wind) inv.setItem(S_WIND, new ItemStack(Material.WIND_CHARGE, 64));
        if (totemBot && level == Level.MAX) inv.setItem(S_SHIELD, kit.shield);
        if (spec.totemCount > 0) inv.setItem(S_TOTEM, Gear.totem(spec.totemCount));
        inv.setItemInOffHand(totemBot ? Gear.totem(spec.totemCount) : kit.shield);
        inv.setHeldItemSlot(S_SWORD);
        shieldUp = false;
        savedOffhand = null;
        savedChest = null;
    }

    private void selectSlot(Player p, int s) {
        if (eating) return;
        if (p.getInventory().getHeldItemSlot() != s) p.getInventory().setHeldItemSlot(s);
    }

    private void updateConsumables(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.setItem(S_GAPPLE, gapples > 0 ? Gear.gapple(level, gapples) : null);
        inv.setItem(S_PEARL, pearls > 0 ? new ItemStack(Material.ENDER_PEARL, Math.min(16, pearls)) : null);
    }

    // ---------------------------------------------------------------- freeze

    void setFrozen(boolean f) {
        frozen = f;
        Player p = player();
        if (p == null) return;
        if (f) {
            cancelNav();
            endCombo(p);
            lowerShield(p);
            eating = false;
            state = State.FROZEN;
            PlayerInventory inv = p.getInventory();
            inv.setItem(S_TOTEM, Gear.totem(99));
            inv.setItemInOffHand(Gear.totem(99));
            inv.setHeldItemSlot(S_TOTEM);
        } else {
            state = State.PATROL;
            equipped = false; // จะสวมเกียร์ใหม่ในทิกถัดไป
        }
    }

    // ---------------------------------------------------------------- shield

    private void useItem(Player p, EquipmentSlot s) {
        if (START_USING == null) return;
        try {
            START_USING.invoke(p, s);
        } catch (Throwable ignored) {
        }
    }

    private static void clearActive(LivingEntity e) {
        if (CLEAR_ACTIVE == null) return;
        try {
            CLEAR_ACTIVE.invoke(e);
        } catch (Throwable ignored) {
        }
    }

    private void raiseShield(Player p, long now) {
        if (shieldUp || now < shieldDisabledUntil || eating || !canShield() || combo != Combo.NONE) return;
        if (totemBot) {
            savedOffhand = p.getInventory().getItemInOffHand().clone();
            p.getInventory().setItemInOffHand(kit.shield);
        }
        shieldUp = true;
        useItem(p, EquipmentSlot.OFF_HAND);
    }

    private void lowerShield(Player p) {
        if (!shieldUp) return;
        shieldUp = false;
        clearActive(p);
        if (totemBot && savedOffhand != null) {
            p.getInventory().setItemInOffHand(savedOffhand);
            savedOffhand = null;
        }
    }

    /** เรียกจาก listener: true = โล่กันดาเมจนี้ได้ */
    boolean tryBlock(Entity attacker) {
        Player p = player();
        if (p == null || !shieldUp || eating || combo != Combo.NONE) return false;
        Vector facing = p.getLocation().getDirection().setY(0);
        Vector to = attacker.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (to.lengthSquared() < 0.01 || facing.lengthSquared() < 0.01 || facing.dot(to) <= 0) return false;
        World w = p.getWorld();
        if (attacker instanceof Player ap && ap.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) {
            shieldDisabledUntil = mgr.now() + 100;
            lowerShield(p);
            w.playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
        } else {
            w.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
        }
        return true;
    }

    void onHurt(Player attacker) {
        lastHurt = mgr.now();
        lastAttacker = attacker;
    }

    Player lastAttacker() {
        return mgr.now() - lastHurt < 100 ? lastAttacker : null;
    }

    private void shieldLogic(Player p, Player enemy, double dist, long now) {
        if (!canShield()) return;
        boolean want = false;
        boolean maceDive = false;
        if (enemy.getInventory().getItemInMainHand().getType() == Material.MACE && dist < 11
                && (enemy.getFallDistance() > 1.5f || enemyVy < -0.25
                || enemy.getLocation().getY() - p.getLocation().getY() > 2.5)) {
            maceDive = true;
        }
        if (maceDive) {
            want = true;
        } else if (dist < spec.reach + 1.8) {
            want = spec.shield >= 2 ? enemy.getCooledAttackStrength(0f) >= 0.8f : rnd.nextDouble() < 0.5;
        }
        if (now < shieldDownUntil) want = false;

        if (want && !shieldUp) {
            double react = spec.shield == 1 ? 0.7 : spec.shield == 2 ? 0.9 : 1.0;
            if (!maceDive || rnd.nextDouble() < react) {
                raiseShield(p, now);
                shieldHoldUntil = now + 8;
            }
        } else if (!want && shieldUp && now >= shieldHoldUntil) {
            lowerShield(p);
        }
    }

    // ---------------------------------------------------------------- navigation

    private void cancelNav() {
        try {
            npc.getNavigator().cancelNavigation();
        } catch (Throwable ignored) {
        }
        navLast = null;
        navEntity = null;
    }

    private void navTo(Location to) {
        if (to == null) return;
        Player p = player();
        if (p == null) return;
        if (to.getWorld() != p.getWorld()) {
            npc.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
            return;
        }
        if (navLast == null || navLast.getWorld() != to.getWorld() || navLast.distanceSquared(to) > 2.25
                || !npc.getNavigator().isNavigating()) {
            npc.getNavigator().setTarget(to);
            navLast = to.clone();
            navEntity = null;
        }
    }

    private void navToEntity(Entity e) {
        if (navEntity != e || !npc.getNavigator().isNavigating()) {
            npc.getNavigator().setTarget(e, false);
            navEntity = e;
            navLast = null;
        }
    }

    // ---------------------------------------------------------------- main tick

    void tick(long now) {
        Player p = player();
        if (p == null || p.isDead()) return;
        if (!equipped) {
            equip(p);
            equipped = true;
            if (frozen) setFrozen(true);
            return;
        }
        if (frozen) return;

        Player enemy = mgr.pickTarget(this, p);
        trackEnemy(enemy, now);

        if (eating) tickEat(p, now);
        if (combo != Combo.NONE) {
            tickCombo(p, enemy, now);
            return;
        }

        double frac = p.getHealth() / maxHealth(p);
        if (state != State.RETREAT && spec.canGapple() && gapples > 0 && now >= nextGappleAt
                && !eating && frac <= spec.retreatHp) {
            state = State.RETREAT;
            retreatSince = now;
        }
        if (state == State.RETREAT) {
            retreat(p, enemy, now);
            return;
        }

        if (enemy == null) {
            state = State.PATROL;
            lowerShield(p);
            selectSlot(p, S_SWORD);
            patrolMove(p);
            return;
        }
        state = State.ATTACK;
        fight(p, enemy, now);
    }

    private void trackEnemy(Player e, long now) {
        if (e == null) {
            lastEnemy = null;
            return;
        }
        double y = e.getLocation().getY();
        if (lastEnemy == e && now > lastEnemyTick) {
            enemyVy = (y - lastEnemyY) / (double) (now - lastEnemyTick);
        } else {
            enemyVy = 0;
        }
        lastEnemy = e;
        lastEnemyY = y;
        lastEnemyTick = now;
    }

    // ---------------------------------------------------------------- patrol

    private void patrolMove(Player p) {
        if (mgr.leader() == this) {
            navTo(mgr.patrolPoint(p.getLocation()));
        } else {
            Location fp = mgr.formationPoint(this);
            if (fp != null && fp.getWorld() == p.getWorld() && fp.distanceSquared(p.getLocation()) > 50 * 50) {
                npc.teleport(fp, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } else {
                navTo(fp);
            }
        }
    }

    // ---------------------------------------------------------------- fight

    private void fight(Player p, Player enemy, long now) {
        Location bl = p.getLocation();
        Location el = enemy.getLocation();
        if (bl.getWorld() != el.getWorld()) return;
        double dist = bl.distance(el);
        boolean ground = onGround(p);

        // ---- ท่าไม้ตาย (high / max)
        if (ground && now >= nextComboAt && !eating) {
            if (spec.elytra && kit.elytra != null && dist >= 26 && dist <= 80) {
                startGlide(p, enemy, now);
                return;
            }
            if (spec.mace && spec.wind && dist >= 4 && dist <= 16 && rnd.nextDouble() < 0.6) {
                startRise(p, enemy, now);
                return;
            }
        }

        // ---- ปาเพิร์ลเข้าประชิด
        if (spec.canPearl() && pearls > 0 && now >= nextPearlAt && ground && dist >= 18 && dist <= 45 && !eating) {
            if (throwPearl(p, el.clone(), now)) return;
        }

        // ---- เดิน / หัน
        npc.faceLocation(enemy.getEyeLocation());
        navToEntity(enemy);
        if (spec.shield >= 2 && dist < 5 && now >= nextStrafe) {
            nextStrafe = now + 10 + rnd.nextInt(14);
            strafeDir = rnd.nextBoolean() ? 1 : -1;
        }
        if (spec.shield >= 2 && dist < 4.5 && dist > 1.2 && ground) {
            Vector to = el.toVector().subtract(bl.toVector()).setY(0);
            if (to.lengthSquared() > 0.01) {
                to.normalize();
                Vector side = new Vector(-to.getZ(), 0, to.getX()).multiply(0.07 * strafeDir);
                p.setVelocity(p.getVelocity().add(side));
            }
        }

        // ---- โล่
        shieldLogic(p, enemy, dist, now);

        // ---- เลือกอาวุธ + โจมตี
        Weapon w = (spec.axe && enemy.isBlocking()) ? Weapon.AXE : Weapon.SWORD;
        selectSlot(p, w == Weapon.AXE ? S_AXE : S_SWORD);
        int cd = (w == Weapon.AXE ? 20 : 12) + (spec.shield == 1 ? 4 : 0);
        boolean ready = now - lastAttack >= cd;
        if (dist <= spec.reach && ready) {
            if (spec.jumpCrit) {
                if (jumpPending && now - jumpAt > 30) jumpPending = false;
                if (jumpPending) {
                    boolean fall = !ground && p.getVelocity().getY() < -0.03;
                    if (fall || now - jumpAt > 10) {
                        doHit(p, enemy, w, fall, now);
                        jumpPending = false;
                    }
                } else if (ground && rnd.nextDouble() < 0.6) {
                    Vector v = p.getVelocity();
                    v.setY(0.42);
                    p.setVelocity(v);
                    jumpPending = true;
                    jumpAt = now;
                } else {
                    doHit(p, enemy, w, false, now);
                }
            } else {
                doHit(p, enemy, w, false, now);
            }
        }
    }

    private static double baseDamage(Material m) {
        switch (m) {
            case NETHERITE_SWORD: return 8;
            case DIAMOND_SWORD: return 7;
            case IRON_SWORD: return 6;
            case NETHERITE_AXE: return 10;
            default: return 5;
        }
    }

    private void doHit(Player p, Player t, Weapon w, boolean crit, long now) {
        if (shieldUp) lowerShield(p);
        shieldDownUntil = now + 8;
        lastAttack = now;
        p.swingMainHand();
        if (rnd.nextDouble() < spec.miss) {
            lastAttack = now - 3;
            return;
        }
        double dmg;
        if (w == Weapon.AXE) {
            dmg = baseDamage(Material.NETHERITE_AXE) + 0.5 * ((spec.full ? 5 : 4) + 1);
            if (t.isBlocking()) {
                t.setCooldown(Material.SHIELD, 100);
                clearActive(t);
                t.getWorld().playSound(t.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
            }
        } else {
            dmg = baseDamage(spec.swordMat) + 0.5 * (spec.sharp + 1);
        }
        if (crit) dmg *= 1.5;
        t.damage(dmg, p);
        if (spec.full) t.setFireTicks(Math.max(t.getFireTicks(), 80));
        t.getWorld().playSound(t.getLocation(), crit ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1f);
    }

    // ---------------------------------------------------------------- combos (wind charge / mace / elytra)

    private void steer(Player p, Location target, double hs, Double vy) {
        Vector v = p.getVelocity();
        Vector d = target.toVector().subtract(p.getLocation().toVector()).setY(0);
        double len = d.length();
        if (len > 0.01) d.multiply(Math.min(hs, len * 0.5) / len);
        else d.zero();
        p.setVelocity(new Vector(d.getX(), vy == null ? v.getY() : vy, d.getZ()));
    }

    private void windFx(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation();
        w.playSound(l, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
        w.spawnParticle(Particle.CLOUD, l, 20, 0.3, 0.1, 0.3, 0.08);
    }

    private void startRise(Player p, Player e, long now) {
        combo = Combo.RISING;
        comboStart = now;
        peakY = p.getLocation().getY();
        lowerShield(p);
        cancelNav();
        npc.faceLocation(e.getEyeLocation());
        Vector d = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        double len = d.length();
        double hs = Math.min(0.5, len * 0.05);
        if (len > 0.01) d.multiply(hs / len);
        p.setVelocity(new Vector(d.getX(), spec.boost, d.getZ()));
        selectSlot(p, S_WIND);
        windFx(p);
        nextComboAt = now + (spec.elytra ? 60 : 100);
    }

    private void startGlide(Player p, Player e, long now) {
        combo = Combo.GLIDE;
        comboStart = now;
        peakY = p.getLocation().getY();
        lowerShield(p);
        cancelNav();
        savedChest = p.getInventory().getChestplate();
        p.getInventory().setChestplate(kit.elytra);
        p.setVelocity(new Vector(0, spec.boost * 1.1, 0));
        windFx(p);
        selectSlot(p, S_WIND);
        nextComboAt = now + 80;
    }

    private void tickCombo(Player p, Player e, long now) {
        Location bl = p.getLocation();
        long age = now - comboStart;
        peakY = Math.max(peakY, bl.getY());
        if (e == null || e.getWorld() != bl.getWorld() || age > 140) {
            endCombo(p);
            return;
        }
        Location eloc = e.getLocation();
        npc.faceLocation(e.getEyeLocation());
        boolean ground = onGround(p);
        Vector v = p.getVelocity();

        switch (combo) {
            case RISING -> {
                if (age >= 4) selectSlot(p, S_MACE);
                steer(p, eloc, 0.35, null);
                if (age >= 4 && (v.getY() <= 0.0 || age > 30)) combo = Combo.DIVE;
            }
            case GLIDE -> {
                if (age >= 6 && !p.isGliding()) p.setGliding(true);
                double hd = Math.hypot(eloc.getX() - bl.getX(), eloc.getZ() - bl.getZ());
                if (hd < 10 || age > 100) {
                    p.setGliding(false);
                    if (savedChest != null) {
                        p.getInventory().setChestplate(savedChest);
                        savedChest = null;
                    }
                    combo = Combo.DIVE;
                    comboStart = now - 4;
                    selectSlot(p, S_MACE);
                } else if (age >= 6) {
                    Vector aim = eloc.toVector().add(new Vector(0, 9, 0)).subtract(bl.toVector());
                    if (aim.lengthSquared() > 0.01) p.setVelocity(aim.normalize().multiply(1.3));
                }
                if (ground && age > 12) endCombo(p);
            }
            case DIVE -> {
                steer(p, eloc, 0.75, v.getY() - 0.05);
                double fall = Math.max(0, peakY - bl.getY());
                p.setFallDistance((float) fall);
                Location chest = eloc.clone().add(0, 0.9, 0);
                if (fall > 1.5 && bl.clone().add(0, 0.9, 0).distanceSquared(chest) <= 3.3 * 3.3) {
                    smash(p, e, fall, now);
                    endCombo(p);
                    return;
                }
                if (ground && age > 8) endCombo(p);
            }
            default -> {
            }
        }
    }

    private void endCombo(Player p) {
        if (combo == Combo.NONE) return;
        combo = Combo.NONE;
        if (p.isGliding()) p.setGliding(false);
        if (savedChest != null) {
            p.getInventory().setChestplate(savedChest);
            savedChest = null;
        }
        selectSlot(p, S_SWORD);
        jumpPending = false;
        navLast = null;
        navEntity = null;
    }

    private void smash(Player p, Player t, double fall, long now) {
        int dens = spec.full ? 5 : 3;
        double bonus = Math.min(fall, 3) * 4 + Math.max(0, Math.min(fall, 8) - 3) * 2 + Math.max(0, fall - 8);
        double dmg = 6 + bonus + 0.5 * dens * fall;
        dmg = Math.min(dmg, mgr.plugin().getConfig().getDouble("mace-max-damage", 60.0));
        p.swingMainHand();
        t.damage(dmg, p);
        if (spec.full) t.setFireTicks(Math.max(t.getFireTicks(), 80));
        p.setFallDistance(0);
        Vector v = p.getVelocity();
        v.setY(0.01);
        p.setVelocity(v);
        World w = t.getWorld();
        w.playSound(t.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.2f, 1f);
        w.spawnParticle(Particle.CLOUD, t.getLocation(), 25, 0.5, 0.1, 0.5, 0.1);
        lastAttack = now;
        shieldDownUntil = now;
        shieldHoldUntil = now + 6;
    }

    // ---------------------------------------------------------------- ender pearl

    private boolean throwPearl(Player p, Location dest, long now) {
        Vector v = Ballistics.solve(p.getEyeLocation(), dest, 1.5);
        if (v == null) return false;
        selectSlot(p, S_PEARL);
        p.launchProjectile(EnderPearl.class, v);
        pearls--;
        updateConsumables(p);
        nextPearlAt = now + 100;
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1f);
        selectSlot(p, S_SWORD);
        return true;
    }

    /** เรียกจาก listener ตอนเพิร์ลของบอทตกถึงพื้น */
    void pearlLanded(Location dest) {
        Player p = player();
        if (p == null) return;
        dest.setYaw(p.getLocation().getYaw());
        dest.setPitch(p.getLocation().getPitch());
        npc.teleport(dest, PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        navLast = null;
        navEntity = null;
        double dmg = mgr.plugin().getConfig().getDouble("pearl-fall-damage", 5.0);
        if (dmg > 0) p.damage(dmg);
        dest.getWorld().playSound(dest, Sound.ENTITY_PLAYER_TELEPORT, 1f, 1f);
    }

    // ---------------------------------------------------------------- retreat + golden apple

    private void retreat(Player p, Player enemy, long now) {
        lowerShield(p);
        Location bl = p.getLocation();
        double dist = 99;
        Location el = null;
        if (enemy != null && enemy.getWorld() == bl.getWorld()) {
            el = enemy.getLocation();
            dist = bl.distance(el);
        }
        if (eating) {
            if (el != null && dist < 8) navAway(p, el);
            return;
        }
        boolean far = dist >= 9;
        boolean timeout = now - retreatSince > 100;

        if (el != null && spec.canPearl() && pearls > 0 && now >= nextPearlAt && dist < 7
                && p.getHealth() > 7 && onGround(p)) {
            Vector away = bl.toVector().subtract(el.toVector()).setY(0);
            if (away.lengthSquared() > 0.01) {
                away.normalize().multiply(22);
                Location dest = bl.clone().add(away);
                dest = mgr.safeGround(dest);
                if (throwPearl(p, dest, now)) return;
            }
        }
        if (far || timeout || el == null) {
            startEat(p, now);
            return;
        }
        navAway(p, el);
    }

    private void navAway(Player p, Location from) {
        Location bl = p.getLocation();
        Vector away = bl.toVector().subtract(from.toVector()).setY(0);
        if (away.lengthSquared() < 0.01) away = new Vector(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5);
        away.normalize().multiply(12);
        Location home = mgr.home();
        if (home != null && home.getWorld() == bl.getWorld()) {
            Vector toHome = home.toVector().subtract(bl.toVector()).setY(0);
            if (toHome.lengthSquared() > 1) away.add(toHome.normalize().multiply(3));
        }
        navTo(mgr.safeGround(bl.clone().add(away)));
    }

    private void startEat(Player p, long now) {
        eating = true;
        eatEndsAt = now + 32;
        lastEatSound = now;
        p.getInventory().setHeldItemSlot(S_GAPPLE);
    }

    private void tickEat(Player p, long now) {
        if (now - lastEatSound >= 4) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.6f, 0.9f + rnd.nextFloat() * 0.3f);
            lastEatSound = now;
        }
        if (now < eatEndsAt) return;
        eating = false;
        gapples--;
        updateConsumables(p);
        if (spec.enchantedApples) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 3));
        } else {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 1f);
        p.getInventory().setHeldItemSlot(S_SWORD);
        nextGappleAt = now + 60;
        if (state == State.RETREAT) state = State.ATTACK;
    }
}
