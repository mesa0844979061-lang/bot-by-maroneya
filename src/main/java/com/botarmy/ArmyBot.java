package com.botarmy;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** บอท 1 ตัว: ถือ NPC ผู้เล่นของ Citizens + สมองต่อสู้ */
public final class ArmyBot {

    static final int S_SWORD = 0, S_MACE = 1, S_AXE = 2, S_GAPPLE = 3, S_PEARL = 4, S_WIND = 5, S_SHIELD = 6, S_TOTEM = 7,
            S_WEB = 8, S_LAVA = 1, S_WATER = 2, // ใช้เฉพาะชุด UHC
            S_CRYSTAL = 1, S_OBI = 2,           // ใช้เฉพาะชุด CPVP
            S_CART = 1, S_BOW = 5, S_FLINT = 7, S_RAIL = 8; // ใช้เฉพาะชุด CUTPVP

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
    final KitType kitType;
    final Spec spec;
    final boolean totemBot;
    /** ประกาศ left the game ไปแล้ว (กันซ้ำตอนตาย) */
    boolean leftAnnounced;
    private int equipEntityId = -1;
    private long nextGearCheck;
    private final Random rnd = new Random();

    State state = State.PATROL;
    boolean frozen, hadSpawned, equipped;
    long respawnAt;
    int slot;

    private Combo combo = Combo.NONE;
    /** คอมโบนี้เริ่มจากปีก → ทุบเสร็จสลับกลับเป็นปีกบินขึ้นทุบวนซ้ำ */
    private boolean glideMode;
    /** ขวานเพิ่งทุบโล่แตก → ต่อค้อน Wind Burst ภายในเวลานี้ / เพิร์ลวาร์ปถึงตัวศัตรู → ลอยตัวทุบ */
    private long postAxeUntil, pearlSmashUntil, pearlThrownAt;
    private boolean pearlForSmash;
    /** จำนวนครั้งที่ลองบินใหม่เมื่อลงพื้นแล้วยังทุบไม่โดน (ศัตรูอยู่ในหลุม/หลบ) */
    private int relaunch;
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

    /** true = อยู่ทีมรุมของ /bot kill */
    boolean killer;
    private Player target;
    private int chain, stuckCount;
    private long nextWebAt, nextLavaAt, nextExtAt, webSince, pendingWaterAt, lastStuckCheck;
    private Location pendingWaterLoc, lastPos;

    // ---- โดดตี / ติดตามศัตรู
    private boolean critPlan = true;
    private double enemyVx, enemyVz, lastEnemyX, lastEnemyZ;

    // ---- CPVP คริสตัล
    private int crystals;
    private long nextCrystalAt, crystalBusyUntil;

    // ---- CUTPVP รถระเบิด / ธนู
    private int carts;
    private long nextCartAt, cartIgniteAt, cartFleeUntil, nextShotAt;
    private final List<ExplosiveMinecart> pendingCarts = new ArrayList<>();
    private Location cartFleeFrom;
    private final boolean useCrossbow = new Random().nextBoolean();

    ArmyBot(BotManager mgr, NPC npc, String name, KitType kitType, Spec spec) {
        this.mgr = mgr;
        this.npc = npc;
        this.name = name;
        this.kitType = kitType;
        this.spec = spec;
        this.gapples = spec.gapples;
        this.pearls = spec.pearls;
        this.crystals = spec.crystal ? 64 : 0;
        this.carts = spec.cart ? 16 : 0;
        this.totemBot = spec.totem;
    }

    // ---------------------------------------------------------------- basics

    /** รวมของทั้งหมดที่บอทถืออยู่ (เกราะ/อาวุธ/ของในมือ) แล้วล้างช่องเก็บของ — ใช้ตอนตายเพื่อดรอป */
    java.util.List<ItemStack> collectDrops(Player p) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        PlayerInventory inv = p.getInventory();
        if (savedChest != null) inv.setChestplate(savedChest); // กำลังใส่ปีกอยู่ → คืนเกราะก่อน
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir() && !isUtility(it.getType())) out.add(it.clone());
        }
        inv.clear();
        return out;
    }

    /** ของสิ้นเปลืองประจำชุด (คริสตัล/ออบซิเดียน/รถระเบิด/ราง/ไฟแช็ก/ลูกธนู) — ไม่ดรอปตอนบอทตาย กันของระเบิดหลุดออกไป */
    static boolean isUtility(Material m) {
        return m == Material.END_CRYSTAL || m == Material.OBSIDIAN || m == Material.TNT_MINECART
                || m == Material.RAIL || m == Material.FLINT_AND_STEEL || m == Material.ARROW;
    }

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
        chain = 0;
        pendingWaterLoc = null;
        crystals = spec.crystal ? 64 : 0;
        carts = spec.cart ? 16 : 0;
        pendingCarts.clear();
        cartFleeUntil = 0;
        crystalBusyUntil = 0;
        jumpPending = false;
        glideMode = false;
        savedChest = null;
        savedOffhand = null;
        postAxeUntil = 0;
        pearlSmashUntil = 0;
        pearlForSmash = false;
        relaunch = 0;
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
        return spec.shield > 0 && (!totemBot || spec.shield >= 3);
    }

    // ---------------------------------------------------------------- equipment

    void equip(Player p) {
        boolean unb = mgr.plugin().getConfig().getBoolean("unbreakable-gear", true);
        kit = Gear.build(spec, unb);
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(kit.helmet.clone());
        inv.setChestplate(kit.chest.clone());
        inv.setLeggings(kit.legs.clone());
        inv.setBoots(kit.boots.clone());
        inv.setItem(S_SWORD, mainItem());
        if (spec.mace && !spec.maceMain) inv.setItem(S_MACE, kit.mace);
        if (spec.axe && !spec.axeOnly) inv.setItem(S_AXE, kit.axe);
        if (gapples > 0) inv.setItem(S_GAPPLE, Gear.gapple(spec, gapples));
        if (pearls > 0) inv.setItem(S_PEARL, new ItemStack(Material.ENDER_PEARL, Math.min(16, pearls)));
        if (spec.wind) inv.setItem(S_WIND, new ItemStack(Material.WIND_CHARGE, 64));
        if (spec.webCd > 0) inv.setItem(S_WEB, new ItemStack(Material.COBWEB, 64));
        if (spec.lavaCd > 0) {
            inv.setItem(S_LAVA, new ItemStack(Material.LAVA_BUCKET));
            inv.setItem(S_WATER, new ItemStack(Material.WATER_BUCKET));
        }
        if (spec.crystal) {
            if (crystals > 0) inv.setItem(S_CRYSTAL, new ItemStack(Material.END_CRYSTAL, Math.min(64, crystals)));
            inv.setItem(S_OBI, new ItemStack(Material.OBSIDIAN, 64));
        }
        if (spec.cart) {
            if (carts > 0) inv.setItem(S_CART, new ItemStack(Material.TNT_MINECART, Math.min(64, carts)));
            inv.setItem(S_RAIL, new ItemStack(Material.RAIL, 64));
            inv.setItem(S_FLINT, Gear.flint(unb));
            inv.setItem(S_BOW, useCrossbow ? kit.crossbow : kit.bow);
        }
        if (totemBot && spec.shield >= 3) inv.setItem(S_SHIELD, kit.shield);
        inv.setItemInOffHand(totemBot ? Gear.totem(spec.totemAmount()) : kit.shield);
        Gear.extras(kitType, inv);
        inv.setHeldItemSlot(S_SWORD);
        pendingCarts.clear();
        shieldUp = false;
        savedOffhand = null;
        savedChest = null;
    }

    /** อาวุธหลักช่อง 0: ขวาน (Axe PvP) / ค้อน (Mace PvP) / ดาบ */
    private ItemStack mainItem() {
        return spec.axeOnly ? kit.axe : spec.maceMain ? kit.mace : kit.sword;
    }

    /** ช่องค้อนสำหรับท่าลอยตัวทุบ */
    private int maceSlot() {
        return spec.maceMain ? S_SWORD : S_MACE;
    }

    private static boolean isEmpty(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    /** กันเกราะหาย: ถ้าเกราะ/ดาบหลุดจากตัว (เช่น NPC ถูกสร้างใหม่ หรือถูกปลั๊กอินอื่นแตะ) ให้สวมคืนทันที */
    private void verifyGear(Player p) {
        if (kit == null) return;
        PlayerInventory inv = p.getInventory();
        if (isEmpty(inv.getHelmet())) inv.setHelmet(kit.helmet.clone());
        // savedChest != null = กำลังสวมปีกชั่วคราวตอนร่อน ไม่ต้องคืนเกราะ
        if (savedChest == null) {
            ItemStack c = inv.getChestplate();
            if (isEmpty(c) || c.getType() == Material.ELYTRA) inv.setChestplate(kit.chest.clone());
        }
        if (isEmpty(inv.getLeggings())) inv.setLeggings(kit.legs.clone());
        if (isEmpty(inv.getBoots())) inv.setBoots(kit.boots.clone());
        ItemStack main = mainItem();
        if (!frozen && main != null && isEmpty(inv.getItem(S_SWORD))) inv.setItem(S_SWORD, main.clone());
        if (!frozen && !totemBot && !shieldUp && isEmpty(inv.getItemInOffHand())) {
            inv.setItemInOffHand(kit.shield.clone());
        }
    }

    /** กันติดสโลว์: ล้างสโลว์เนส และเคลียร์สถานะ "ยกโล่ค้าง" ที่ไม่ได้ตั้งใจ */
    private void cleanse(Player p) {
        if (!mgr.plugin().getConfig().getBoolean("cleanse-slowness", true)) return;
        if (p.hasPotionEffect(PotionEffectType.SLOWNESS)) p.removePotionEffect(PotionEffectType.SLOWNESS);
        if (!shieldUp && !eating && combo == Combo.NONE && p.isBlocking()) clearActive(p);
    }

    private void selectSlot(Player p, int s) {
        if (eating) return;
        if (p.getInventory().getHeldItemSlot() != s) p.getInventory().setHeldItemSlot(s);
    }

    private void updateConsumables(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.setItem(S_GAPPLE, gapples > 0 ? Gear.gapple(spec, gapples) : null);
        inv.setItem(S_PEARL, pearls > 0 ? new ItemStack(Material.ENDER_PEARL, Math.min(16, pearls)) : null);
        if (spec.crystal) inv.setItem(S_CRYSTAL, crystals > 0 ? new ItemStack(Material.END_CRYSTAL, Math.min(64, crystals)) : null);
        if (spec.cart) inv.setItem(S_CART, carts > 0 ? new ItemStack(Material.TNT_MINECART, Math.min(64, carts)) : null);
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
        strafeDir = -strafeDir; // เปลี่ยนทิศหลบหลังโดนตี
        mgr.alert(attacker);    // เรียกพวกมาช่วย
    }

    Player currentTarget() {
        return target;
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
        Material mh = enemy.getInventory().getItemInMainHand().getType();
        boolean ranged = spec.shield >= 2 && dist > 4 && dist < 40 && enemy.isHandRaised()
                && (mh == Material.BOW || mh == Material.CROSSBOW || mh == Material.TRIDENT);
        if (maceDive || ranged) {
            want = true;
        } else if (dist < spec.reach + 1.8) {
            want = spec.shield >= 2 ? enemy.getCooledAttackStrength(0f) >= 0.8f : rnd.nextDouble() < 0.5;
        }
        if (now < shieldDownUntil) want = false;
        // ศัตรูเลือดเหลือน้อย → เลิกตั้งการ์ด รีบจบเกม (ยกเว้นกำลังโดนค้อน/ยิงธนู)
        if (!maceDive && !ranged && spec.shield < 3 && enemy.getHealth() <= 6) want = false;

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
        if (!equipped || p.getEntityId() != equipEntityId) {
            equip(p); // ครั้งแรก หรือ NPC ถูกสร้างใหม่ (เช่น โหลดสกิน) → สวมเกียร์ใหม่
            equipped = true;
            equipEntityId = p.getEntityId();
            nextGearCheck = now + 10;
            if (frozen) setFrozen(true);
            return;
        }
        if (now >= nextGearCheck) {
            nextGearCheck = now + 10;
            verifyGear(p);
        }
        cleanse(p);
        if (frozen) return;

        hazards(p, now);
        Player enemy = mgr.pickTarget(this, p);
        target = enemy;
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
        Location el = e.getLocation();
        double y = el.getY();
        if (lastEnemy == e && now > lastEnemyTick) {
            double dt = (double) (now - lastEnemyTick);
            enemyVy = (y - lastEnemyY) / dt;
            enemyVx = 0.5 * enemyVx + 0.5 * ((el.getX() - lastEnemyX) / dt);
            enemyVz = 0.5 * enemyVz + 0.5 * ((el.getZ() - lastEnemyZ) / dt);
        } else {
            enemyVy = 0;
            enemyVx = 0;
            enemyVz = 0;
        }
        lastEnemy = e;
        lastEnemyY = y;
        lastEnemyX = el.getX();
        lastEnemyZ = el.getZ();
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

        // ---- ท่าไม้ตายของค้อน (ต้องยืนบนพื้นและไม่ได้กินแอปเปิลอยู่)
        if (ground && !eating && spec.mace && spec.wind) {
            // (1) ขวานเพิ่งทุบโล่แตก → ต่อค้อน Density + Wind Burst ทันที (ศัตรูยกโล่ไม่ได้ ~5 วิ)
            if (postAxeUntil > 0) {
                boolean go = now <= postAxeUntil && p.getHealth() > 6 && !enemy.isDead();
                postAxeUntil = 0;
                if (go) {
                    startRise(p, enemy, now);
                    return;
                }
            }
            // (2) เพิร์ลวาร์ปมาถึงตัวศัตรูแล้ว → Wind Charge ลอยตัวทุบจากฟ้า
            if (pearlSmashUntil > 0) {
                boolean go = now <= pearlSmashUntil && p.getHealth() > 6 && !enemy.isDead();
                pearlSmashUntil = 0;
                if (go) {
                    startRise(p, enemy, now);
                    return;
                }
            }
        }
        if (ground && !eating && p.getHealth() > 8) {
            boolean macer = spec.mace && spec.wind;
            // ศัตรูอยู่ต่ำกว่า (ยืนในหลุม/ร่อง) เดินเข้าไปตีไม่ได้ → ต้องบินทุบจากข้างบนเสมอ
            boolean pit = macer && el.getY() <= bl.getY() - 1.5 && dist <= 16;
            boolean cdOk = now >= nextComboAt || (pit && now - comboStart >= 24);
            if (cdOk) {
                // ปีกบินสลับเกราะทุบ (วนซ้ำ) — ไกลพอถึงจะบิน
                if (spec.elytra && kit.elytra != null && dist >= 20 && dist <= 80 && rnd.nextDouble() < 0.6) {
                    startGlide(p, enemy, now);
                    return;
                }
                // เพิร์ล + Wind Charge: ปาเพิร์ลวาร์ปไปหาศัตรูแล้วลอยตัวทุบ
                if (macer && spec.canPearl() && pearls > 0 && now >= nextPearlAt && dist >= 9 && dist <= 40
                        && spec.comboChance >= 0.5 && rnd.nextDouble() < spec.comboChance * 0.5) {
                    if (throwPearl(p, el.clone(), now)) {
                        pearlForSmash = true;
                        pearlThrownAt = now;
                        nextComboAt = now + 40;
                        return;
                    }
                }
                if (spec.elytra && kit.elytra != null && dist >= 20 && dist <= 80) {
                    startGlide(p, enemy, now);
                    return;
                }
                // ลอยตัวทุบ: ไม่ใช้ถ้าศัตรูยกโล่ (ให้ขวานตัดโล่ก่อน), หรือศัตรูใกล้ตาย (ตีธรรมดาจบเร็วกว่า)
                boolean smart = !enemy.isBlocking() && (enemy.getHealth() > 8 || dist > 5 || pit);
                double chance = pit ? 1.0 : spec.comboChance;
                if (macer && smart && dist >= 1.5 && dist <= 22 && rnd.nextDouble() < chance) {
                    startRise(p, enemy, now);
                    return;
                }
            }
        }

        // ---- CUTPVP: จุดรถระเบิดที่วางไว้ / หนีรัศมีระเบิด
        if (!pendingCarts.isEmpty() && now >= cartIgniteAt) {
            igniteCarts(p, now);
        }
        if (now < cartFleeUntil && cartFleeFrom != null && dist > 3.0 && !eating) {
            npc.faceLocation(enemy.getEyeLocation());
            navAway(p, cartFleeFrom);
            shieldLogic(p, enemy, dist, now);
            return;
        }

        // ---- ปาเพิร์ลเข้าประชิด
        double pearlMin = spec.shield >= 3 ? 14 : 18; // ระดับสูงไล่ตามคนวิ่งหนีเก่งกว่า
        if (spec.canPearl() && pearls > 0 && now >= nextPearlAt && ground && dist >= pearlMin && dist <= 45 && !eating) {
            if (throwPearl(p, el.clone(), now)) {
                if (spec.mace && spec.wind) {
                    pearlForSmash = true; // วาร์ปถึงแล้วลอยตัวทุบต่อ
                    pearlThrownAt = now;
                }
                return;
            }
        }

        // ---- CPVP: วางคริสตัล / CUTPVP: วางรถระเบิด + ยิงธนูไฟ
        if (!eating && ground) {
            if (spec.crystal && tryCrystal(p, enemy, dist, now)) return;
            if (spec.cart && tryCart(p, enemy, dist, now)) return;
        }
        if (spec.bow && !eating && tryShoot(p, enemy, dist, now)) return;

        // ---- ใยแมงมุม / ลาวา+น้ำ (easy, medium)
        if (!eating && ground && (spec.webCd > 0 || spec.lavaCd > 0) && mgr.trapsOn()) {
            if (tryTraps(p, enemy, dist, now)) return;
        }

        // ---- เดิน / หัน
        npc.faceLocation(enemy.getEyeLocation());
        // CPVP ระดับกลางขึ้นไป: ถ้าคริสตัลพร้อม ยืนห่างในระยะวางคริสตัล (ไม่เบียดจนระเบิดใส่ตัวเอง)
        boolean crystalMode = spec.crystal && crystals > 0 && spec.crystalSkill >= 0.5 && !eating
                && now >= nextCrystalAt - 2 && p.getHealth() > 6;
        if (crystalMode) {
            Vector away = bl.toVector().subtract(el.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5);
            away.normalize().multiply(Math.max(2.5, spec.crystalRange - 0.4));
            navTo(mgr.safeGround(el.clone().add(away)));
        } else if (dist > 5 && mgr.activeCount() >= 3) {
            navTo(flankPoint(el)); // ทัพรุมเป็นวงล้อม ไม่เดินเรียงแถวเดียว
        } else {
            navToEntity(enemy);
        }
        if (crystalMode) stuckCount = 0; else checkStuck(p, enemy, dist, now); // ยืนห่างตามแผนไม่ใช่ติดขัด
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
        Weapon w = (spec.axeOnly || (spec.axe && enemy.isBlocking())) ? Weapon.AXE : Weapon.SWORD;
        selectSlot(p, slotFor(w));
        int cd = (w == Weapon.AXE ? 20 : spec.maceMain ? 30 : 12) + (spec.shield == 1 ? 4 : 0);
        boolean ready = now - lastAttack >= cd;
        double vy = p.getVelocity().getY();
        boolean falling = !ground && vy < -0.03;

        if (!spec.jumpCrit || spec.critChance <= 0) {
            if (dist <= spec.reach && ready) doHit(p, enemy, w, false, now);
            return;
        }

        // ---- โดดตี: กระโดดให้จังหวะตกลงมาพอดีตอนคูลดาวน์ครบ (คริติคอล x1.5)
        if (jumpPending && now - jumpAt > 24) jumpPending = false;
        if (jumpPending) {
            if (dist <= spec.reach && ready && falling) {
                doHit(p, enemy, w, true, now);
                jumpPending = false;
            } else if (dist <= spec.reach && ready && now - jumpAt > 14) {
                doHit(p, enemy, w, false, now); // ลงพื้นแล้วคูลดาวน์เพิ่งครบ ตีธรรมดาอย่าเสียจังหวะ
                jumpPending = false;
            } else if (ground && now - jumpAt > 4) {
                jumpPending = false; // ลงพื้นก่อนได้ตี
            }
        } else if (ground && critPlan && dist <= spec.reach + 0.9 && now - lastAttack >= cd - 6) {
            Vector v = p.getVelocity();
            v.setY(0.42);
            p.setVelocity(v);
            jumpPending = true;
            jumpAt = now;
        } else if (dist <= spec.reach && ready && (ground || falling)) {
            // ไม่ได้วางแผนโดด (หรือกำลังตกอยู่แล้ว) ก็ตีเลย
            doHit(p, enemy, w, falling, now);
        }
    }

    private int slotFor(Weapon w) {
        return (w == Weapon.AXE && !spec.axeOnly) ? S_AXE : S_SWORD;
    }

    private static double baseDamage(Material m) {
        switch (m) {
            case NETHERITE_SWORD: return 8;
            case DIAMOND_SWORD: return 7;
            case IRON_SWORD: return 6;
            case NETHERITE_AXE: return 10;
            case MACE: return 6;
            default: return 5;
        }
    }

    private void doHit(Player p, Player t, Weapon w, boolean crit, long now) {
        if (shieldUp) lowerShield(p);
        shieldDownUntil = now + 8;
        lastAttack = now;
        critPlan = rnd.nextDouble() < spec.critChance; // วางแผนโดดตีรอบถัดไป
        p.swingMainHand();
        if (rnd.nextDouble() < spec.miss) {
            lastAttack = now - 3;
            return;
        }
        double dmg;
        boolean shielded = t.isBlocking();
        boolean brokeShield = false;
        if (w == Weapon.AXE) {
            dmg = baseDamage(Material.NETHERITE_AXE) + 0.5 * (spec.axeSharp + 1);
            if (shielded) {
                t.setCooldown(Material.SHIELD, 100);
                clearActive(t);
                t.getWorld().playSound(t.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
                shielded = false;
                brokeShield = true;
            }
        } else {
            dmg = baseDamage(spec.swordMat) + 0.5 * (spec.sharp + 1);
        }
        if (crit) dmg *= 1.5;
        t.damage(dmg, p);
        // ขวานทุบโล่แตก → ต่อด้วยค้อน Density + Wind Burst ทันที
        if (brokeShield && spec.mace && spec.wind && !spec.axeOnly
                && rnd.nextDouble() < Math.min(1.0, spec.comboChance + 0.35)) {
            postAxeUntil = now + 30;
        }
        if (spec.fireAspect && w == Weapon.SWORD) t.setFireTicks(Math.max(t.getFireTicks(), 80));
        // W-tap: แรงสะบัดเพิ่มตอนตีโดน (ระดับยาก) ไม่เพิ่มถ้าเป้ายกโล่กันอยู่
        if (spec.kb > 0 && !shielded && t.isValid() && !t.isDead()) {
            Vector dir = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (dir.lengthSquared() > 0.01) {
                dir.normalize().multiply(spec.kb);
                t.setVelocity(t.getVelocity().add(dir));
            }
        }
        t.getWorld().playSound(t.getLocation(), crit ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1f);
    }

    // ---------------------------------------------------------------- smarter movement

    /** จุดล้อมศัตรู: แต่ละตัวได้มุมของตัวเอง (golden angle) เลยกระจายรอบเป้า */
    private Location flankPoint(Location el) {
        double ang = slot * 2.399963;
        Location l = el.clone().add(Math.cos(ang) * 3.0, 0, Math.sin(ang) * 3.0);
        return mgr.safeGround(l);
    }

    /** ติดขัด (กำแพง/หลุม/ใย) → กระโดด → เพิร์ล → วาร์ปไปหาเป้า */
    private void checkStuck(Player p, Player e, double dist, long now) {
        if (now - lastStuckCheck < 20) return;
        lastStuckCheck = now;
        Location cur = p.getLocation();
        if (lastPos != null && lastPos.getWorld() == cur.getWorld() && lastPos.distanceSquared(cur) < 0.25
                && dist > 3.5 && !eating) {
            stuckCount++;
            Vector v = p.getVelocity();
            v.setY(0.45);
            p.setVelocity(v);
            navLast = null;
            navEntity = null;
            if (stuckCount >= 3 && spec.canPearl() && pearls > 0 && dist >= 8 && now >= nextPearlAt
                    && throwPearl(p, e.getLocation().clone(), now)) {
                stuckCount = 0;
            } else if (stuckCount >= 6) {
                Vector d = cur.toVector().subtract(e.getLocation().toVector()).setY(0);
                if (d.lengthSquared() < 0.01) d = new Vector(1, 0, 0);
                d.normalize().multiply(4.5);
                Location dest = mgr.safeGround(e.getLocation().clone().add(d));
                npc.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
                stuckCount = 0;
                navLast = null;
                navEntity = null;
            }
        } else {
            stuckCount = 0;
        }
        lastPos = cur.clone();
    }

    // ---------------------------------------------------------------- hazards + traps (cobweb / lava / water)

    private static boolean same(Block a, Block b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ() && a.getWorld() == b.getWorld();
    }

    /** ตัดใยแมงมุมตัวเอง / ขึ้นจากลาวา / ดับไฟด้วยน้ำ / วางน้ำดับลาวาที่ค้างรอ */
    private void hazards(Player p, long now) {
        Traps tr = mgr.traps();
        Block fb = p.getLocation().getBlock();
        Block hb = fb.getRelative(BlockFace.UP);

        boolean webbed = fb.getType() == Material.COBWEB || hb.getType() == Material.COBWEB;
        if (webbed) {
            if (webSince == 0) {
                webSince = now;
            } else if (now - webSince >= 4) {
                cutWeb(tr, fb);
                cutWeb(tr, hb);
                webSince = 0;
                p.swingMainHand();
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_COBWEB_BREAK, 1f, 1f);
            }
        } else {
            webSince = 0;
        }

        if (fb.getType() == Material.LAVA) {
            Vector v = p.getVelocity();
            v.setY(Math.max(v.getY(), 0.42));
            p.setVelocity(v);
        }

        if (tr != null && spec.lavaCd > 0 && p.getFireTicks() > 0 && now >= nextExtAt && fb.getType().isAir()) {
            if (tr.set(fb, Material.WATER, now + mgr.trapTicks("trap-water-seconds", 3))) {
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
                p.swingMainHand();
            }
            nextExtAt = now + 100;
        }

        if (pendingWaterLoc != null && now >= pendingWaterAt) {
            Block lava = pendingWaterLoc.getBlock();
            boolean inside = target != null && target.isValid() && same(target.getLocation().getBlock(), lava);
            if (inside && now < pendingWaterAt + 20) {
                return; // ศัตรูยังยืนในลาวา ถ้าเทน้ำตอนนี้จะกลายเป็นออบซิเดียนทับตัวเขา รอก่อน
            }
            if (!inside && tr != null) {
                Block above = lava.getRelative(BlockFace.UP);
                if (tr.set(above, Material.WATER, now + mgr.trapTicks("trap-water-seconds", 3))) {
                    above.getWorld().playSound(above.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
                }
            }
            pendingWaterLoc = null;
        }
    }

    private void cutWeb(Traps tr, Block b) {
        if (b.getType() != Material.COBWEB) return;
        if (tr == null || !tr.release(b)) b.setType(Material.AIR);
    }

    private boolean tryTraps(Player p, Player e, double dist, long now) {
        Traps tr = mgr.traps();
        if (tr == null) return false;
        Location el = e.getLocation();
        if (spec.webCd > 0 && now >= nextWebAt && dist >= 2.2 && dist <= 6.5
                && el.getBlock().getType() != Material.COBWEB && rnd.nextDouble() < 0.35) {
            nextWebAt = now + spec.webCd + rnd.nextInt(60);
            if (placeWeb(p, e, tr, now)) return true;
        }
        if (spec.lavaCd > 0 && now >= nextLavaAt && dist >= 3.6 && dist <= 7 && pendingWaterLoc == null
                && rnd.nextDouble() < 0.25) {
            nextLavaAt = now + spec.lavaCd + rnd.nextInt(100);
            if (placeLava(p, e, tr, now)) return true;
        }
        return false;
    }

    private int put(Traps tr, Block b, Material m, long exp, Player self) {
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        if (c.getWorld() != self.getWorld() || c.distanceSquared(self.getLocation().add(0, 0.9, 0)) < 1.5 * 1.5) return 0;
        return tr.set(b, m, exp) ? 1 : 0;
    }

    /** วางใยแมงมุมที่เท้า+หัวศัตรู และล้อม 3 ด้าน (ด้านที่บอทยืนเว้นไว้ให้ตีได้) */
    private boolean placeWeb(Player p, Player e, Traps tr, long now) {
        long exp = now + mgr.trapTicks("trap-web-seconds", 8);
        Location el = e.getLocation();
        Block feet = el.getBlock();
        Vector toBot = p.getLocation().toVector().subtract(el.toVector()).setY(0);
        if (toBot.lengthSquared() > 0.01) toBot.normalize();
        int n = put(tr, feet, Material.COBWEB, exp, p);
        n += put(tr, feet.getRelative(BlockFace.UP), Material.COBWEB, exp, p);
        if (spec.webRing > 0) {
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (BlockFace f : faces) {
                Vector d = new Vector(f.getModX(), 0, f.getModZ());
                if (d.dot(toBot) > 0.5) continue;
                n += put(tr, feet.getRelative(f), Material.COBWEB, exp, p);
            }
        }
        if (n == 0) return false;
        selectSlot(p, S_WEB);
        npc.faceLocation(e.getEyeLocation());
        p.swingMainHand();
        p.getWorld().playSound(el, Sound.BLOCK_COBWEB_PLACE, 1f, 1f);
        selectSlot(p, S_SWORD);
        return true;
    }

    /** เทลาวาที่เท้าศัตรู แล้วอีกแป๊บเทน้ำจากด้านบนดับเป็นออบซิเดียน (ทุกอย่างคืนค่าเดิมเอง) */
    private boolean placeLava(Player p, Player e, Traps tr, long now) {
        Block feet = e.getLocation().getBlock();
        if (!feet.getRelative(BlockFace.DOWN).getType().isSolid()) return false;
        Location c = feet.getLocation().add(0.5, 0.5, 0.5);
        if (c.getWorld() != p.getWorld() || c.distanceSquared(p.getLocation()) < 3.2 * 3.2) return false;
        long exp = now + mgr.trapTicks("trap-lava-seconds", 3);
        if (!tr.set(feet, Material.LAVA, exp)) return false;
        pendingWaterLoc = feet.getLocation();
        pendingWaterAt = now + 24;
        selectSlot(p, S_LAVA);
        npc.faceLocation(e.getEyeLocation());
        p.swingMainHand();
        p.getWorld().playSound(feet.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 1f);
        selectSlot(p, S_SWORD);
        return true;
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
        chain = 0;
        relaunch = 0;
        glideMode = false;
        jumpPending = false;
        postAxeUntil = 0;
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
        nextComboAt = now + spec.comboCd;
    }

    private void startGlide(Player p, Player e, long now) {
        combo = Combo.GLIDE;
        comboStart = now;
        chain = 0;
        relaunch = 0;
        glideMode = true;
        jumpPending = false;
        postAxeUntil = 0;
        peakY = p.getLocation().getY();
        lowerShield(p);
        cancelNav();
        swapToElytra(p);
        p.setVelocity(new Vector(0, spec.boost * 1.1, 0));
        windFx(p);
        selectSlot(p, S_WIND);
        nextComboAt = now + Math.min(80, spec.comboCd + 20);
    }

    /** สลับเกราะ → ปีก (จำเกราะเดิมไว้สลับกลับ) */
    private void swapToElytra(Player p) {
        if (savedChest == null) {
            ItemStack cur = p.getInventory().getChestplate();
            savedChest = (cur == null || cur.getType() == Material.ELYTRA) ? kit.chest.clone() : cur;
        }
        p.getInventory().setChestplate(kit.elytra);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 1f);
    }

    /** สลับปีก → เกราะ (กลางอากาศ) */
    private void swapToChest(Player p) {
        if (p.isGliding()) p.setGliding(false);
        if (savedChest != null) {
            p.getInventory().setChestplate(savedChest);
            savedChest = null;
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1f);
        }
    }

    /** เด้งตัวขึ้นด้วย Wind Burst แล้วทุบซ้ำ */
    private void riseAgain(Player p, long now, double mul) {
        combo = Combo.RISING;
        comboStart = now;
        peakY = p.getLocation().getY();
        p.setVelocity(new Vector(0, spec.boost * mul, 0));
        selectSlot(p, S_WIND);
        windFx(p);
        nextComboAt = now + 30;
    }

    /** ทุบเสร็จ → สลับกลับเป็นปีก บินขึ้นสูงใหม่แล้วสลับเกราะทุบซ้ำ */
    private void glideAgain(Player p, long now) {
        combo = Combo.GLIDE;
        comboStart = now;
        peakY = p.getLocation().getY();
        swapToElytra(p);
        p.setVelocity(new Vector(0, spec.boost * 1.15, 0));
        selectSlot(p, S_WIND);
        windFx(p);
        nextComboAt = now + 30;
    }

    private void tickCombo(Player p, Player e, long now) {
        Location bl = p.getLocation();
        long age = now - comboStart;
        peakY = Math.max(peakY, bl.getY());
        if (e == null || e.getWorld() != bl.getWorld() || age > 140 || e.isDead()) {
            endCombo(p);
            return;
        }
        Location eloc = e.getLocation();
        npc.faceLocation(e.getEyeLocation());
        boolean ground = onGround(p);
        Vector v = p.getVelocity();

        switch (combo) {
            case RISING -> {
                if (age >= 4) selectSlot(p, maceSlot());
                steer(p, eloc, 0.35, null);
                if (age >= 4 && (v.getY() <= 0.0 || age > 30)) combo = Combo.DIVE;
            }
            case GLIDE -> {
                if (age >= 6 && !p.isGliding()) {
                    p.setGliding(true);
                    bl.getWorld().playSound(bl, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                }
                if (age >= 6 && age % 8 == 0) bl.getWorld().spawnParticle(Particle.FIREWORK, bl, 6, 0.2, 0.2, 0.2, 0.02);
                double hd = Math.hypot(eloc.getX() - bl.getX(), eloc.getZ() - bl.getZ());
                boolean high = bl.getY() >= eloc.getY() + 6;
                if ((hd < 10 && high) || age > 100) {
                    swapToChest(p); // สลับกลับเป็นเกราะกลางอากาศ
                    combo = Combo.DIVE;
                    comboStart = now - 4;
                    selectSlot(p, maceSlot());
                } else if (age >= 6) {
                    Vector aim = eloc.toVector().add(new Vector(0, 9, 0)).subtract(bl.toVector());
                    if (aim.lengthSquared() > 0.01) p.setVelocity(aim.normalize().multiply(1.6));
                }
                if (ground && age > 12) endCombo(p);
            }
            case DIVE -> {
                steer(p, eloc, 0.75, v.getY() - 0.05);
                double fall = Math.max(0, peakY - bl.getY());
                p.setFallDistance((float) fall);
                Location chest = eloc.clone().add(0, 0.9, 0);
                double d2 = bl.clone().add(0, 0.9, 0).distanceSquared(chest);
                boolean landed = ground && age > 3;
                // ทุบเมื่อเข้าระยะ (ลงพื้นแล้วก็ให้ระยะกว้างขึ้นหน่อย เผื่อศัตรูยืนในหลุม/ข้างตัว)
                if (fall > 1.5 && (d2 <= 3.3 * 3.3 || (landed && d2 <= 4.3 * 4.3))) {
                    smash(p, e, fall, now);
                    // ทุบแล้วเป้ายังไม่ตาย → ต่อคอมโบ (ปีก: สลับปีกบินทุบวนซ้ำ / ปกติ: Wind Burst เด้งทุบซ้ำ)
                    if (chain < spec.chainMax && e.isValid() && !e.isDead() && e.getHealth() > 0) {
                        chain++;
                        relaunch = 0;
                        if (glideMode && kit.elytra != null) glideAgain(p, now);
                        else riseAgain(p, now, 1.15);
                    } else {
                        chain = 0;
                        endCombo(p);
                    }
                    return;
                }
                if (ground && age > 8) {
                    // ลงพื้นแล้วยังทุบไม่โดน (ศัตรูอยู่ในหลุม/ขยับหนี) → บินใหม่ ไม่ยอมแพ้ง่ายๆ
                    double flat = Math.hypot(eloc.getX() - bl.getX(), eloc.getZ() - bl.getZ());
                    if (spec.wind && relaunch < 3 && flat <= 9 && !e.isDead() && p.getHealth() > 6) {
                        relaunch++;
                        riseAgain(p, now, 1.1);
                    } else {
                        endCombo(p);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void endCombo(Player p) {
        if (combo == Combo.NONE) return;
        combo = Combo.NONE;
        glideMode = false;
        relaunch = 0;
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
        int dens = spec.density;
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

    // ---------------------------------------------------------------- CPVP: end crystals

    private static final class Cand {
        int x, y, z;
        boolean needObi;
        double score;
    }

    /** ตัวคูณดาเมจระเบิดที่เหลือหลังเกราะ+Protection ของตัวเอง (ประมาณ) */
    private double selfBlastFactor() {
        return (1.0 - 0.04 * Math.min(20, spec.prot * 4)) * 0.68;
    }

    private static boolean supported(World w, int x, int y, int z) {
        return w.getBlockAt(x, y - 1, z).getType().isSolid() || w.getBlockAt(x + 1, y, z).getType().isSolid()
                || w.getBlockAt(x - 1, y, z).getType().isSolid() || w.getBlockAt(x, y, z + 1).getType().isSolid()
                || w.getBlockAt(x, y, z - 1).getType().isSolid() || w.getBlockAt(x, y + 1, z).getType().isSolid();
    }

    /** กล่องคริสตัล (2x2x2) ชนตัวผู้เล่นไหม — ชนแล้ววางไม่ได้ */
    private static boolean crystalBlocked(double cx, double cy, double cz, Location l) {
        return Math.abs(l.getX() - cx) < 1.3 && Math.abs(l.getZ() - cz) < 1.3
                && l.getY() < cy + 2.0 && l.getY() + 1.8 > cy;
    }

    /** บล็อกออบซิเดียนที่จะวางทับตัวผู้เล่นไหม */
    private static boolean blockHits(int bx, int by, int bz, Location l) {
        return Math.abs(l.getX() - (bx + 0.5)) < 0.8 && Math.abs(l.getZ() - (bz + 0.5)) < 0.8
                && l.getY() < by + 1 && l.getY() + 1.8 > by;
    }

    private double exposure(World w, double cx, double cy, double cz, Location vl) {
        double[] hs = {0.3, 0.9, 1.5};
        int clear = 0;
        Location start = new Location(w, cx, cy, cz);
        for (double h : hs) {
            Vector dir = new Vector(vl.getX() - cx, vl.getY() + h - cy, vl.getZ() - cz);
            double len = dir.length();
            if (len < 0.05) {
                clear++;
                continue;
            }
            dir.multiply(1.0 / len);
            try {
                if (w.rayTraceBlocks(start, dir, len, FluidCollisionMode.NEVER, true) == null) clear++;
            } catch (Throwable t) {
                clear++;
            }
        }
        return clear / 3.0;
    }

    /** ดาเมจประมาณการจากคริสตัล (สูตรระเบิดของ Minecraft: power 6) */
    private double blast(World w, double cx, double cy, double cz, Location vl, double armorF, boolean ray) {
        double dx = vl.getX() - cx, dy = vl.getY() - cy, dz = vl.getZ() - cz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / 12.0;
        if (d >= 1) return 0;
        double imp = (1 - d) * (ray ? exposure(w, cx, cy, cz, vl) : 1.0);
        if (imp <= 0) return 0;
        return ((imp * imp + imp) / 2.0 * 84.0 + 1.0) * armorF;
    }

    /**
     * หาจุดวางคริสตัลที่ดีที่สุดรอบศัตรู (ออบซิเดียน/เบดร็อกที่มีอยู่ หรือวางออบซิเดียนชั่วคราวก่อน)
     * ให้คะแนน = ดาเมจศัตรู - น้ำหนัก*ดาเมจตัวเอง; เลือดตัวเองไม่พอจะไม่วางเลย
     */
    private boolean tryCrystal(Player p, Player enemy, double dist, long now) {
        if (crystals <= 0 || now < crystalBusyUntil || dist > 10) return false;
        boolean popped = spec.crystalSkill >= 0.75 && mgr.recentlyPopped(enemy);
        if (now < nextCrystalAt && !popped) return false;
        World w = p.getWorld();
        if (enemy.getWorld() != w) return false;
        Location eye = p.getEyeLocation();
        Location sl = p.getLocation();
        Location el = enemy.getLocation();
        boolean canObi = mgr.traps() != null;
        double range = spec.crystalRange;
        double hp = p.getHealth() + p.getAbsorptionAmount();
        double selfF = selfBlastFactor();
        final double enemyF = 0.33;
        int ex = el.getBlockX(), ey = el.getBlockY(), ez = el.getBlockZ();

        List<Cand> list = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    int bx = ex + dx, by = ey + dy, bz = ez + dz;
                    if (by < w.getMinHeight() || by + 2 >= w.getMaxHeight()) continue;
                    Material m = w.getBlockAt(bx, by, bz).getType();
                    boolean existing = m == Material.OBSIDIAN || m == Material.BEDROCK;
                    boolean need = false;
                    if (!existing) {
                        if (!canObi || !m.isAir() || !supported(w, bx, by, bz)) continue;
                        need = true;
                    }
                    if (!w.getBlockAt(bx, by + 1, bz).getType().isAir() || !w.getBlockAt(bx, by + 2, bz).getType().isAir()) continue;
                    double cx = bx + 0.5, cy = by + 1.0, cz = bz + 0.5;
                    double rx = cx - eye.getX(), ry = cy + 1.0 - eye.getY(), rz = cz - eye.getZ();
                    if (rx * rx + ry * ry + rz * rz > range * range) continue;
                    if (crystalBlocked(cx, cy, cz, el) || crystalBlocked(cx, cy, cz, sl)) continue;
                    if (need && (blockHits(bx, by, bz, el) || blockHits(bx, by, bz, sl))) continue;
                    double e0 = blast(w, cx, cy, cz, el, enemyF, false);
                    if (e0 < spec.crystalMinDmg) continue;
                    double s0 = blast(w, cx, cy, cz, sl, selfF, false);
                    Cand c = new Cand();
                    c.x = bx;
                    c.y = by;
                    c.z = bz;
                    c.needObi = need;
                    c.score = e0 - spec.crystalSelfW * s0 + (need ? 0 : 1.5);
                    list.add(c);
                }
            }
        }
        if (list.isEmpty()) {
            nextCrystalAt = now + (spec.crystalSkill >= 0.75 ? 4 : 12);
            return false;
        }
        list.sort((a, b) -> Double.compare(b.score, a.score));
        if (list.size() > 6) list = new ArrayList<>(list.subList(0, 6));

        // รอบละเอียด: เช็คสิ่งกำบัง (raycast) เฉพาะผู้เข้ารอบ
        List<Cand> fin = new ArrayList<>();
        for (Cand c : list) {
            double cx = c.x + 0.5, cy = c.y + 1.0, cz = c.z + 0.5;
            double eD = blast(w, cx, cy, cz, el, enemyF, true);
            double sD = blast(w, cx, cy, cz, sl, selfF, true);
            if (eD < spec.crystalMinDmg || sD > spec.crystalSelfTol * hp) continue;
            c.score = eD - spec.crystalSelfW * sD + (c.needObi ? 0 : 1.5);
            fin.add(c);
        }
        if (fin.isEmpty()) {
            nextCrystalAt = now + (spec.crystalSkill >= 0.75 ? 4 : 12);
            return false;
        }
        fin.sort((a, b) -> Double.compare(b.score, a.score));
        int start = rnd.nextDouble() < spec.crystalSkill ? 0 : rnd.nextInt(fin.size());
        for (int i = 0; i < Math.min(3, fin.size()); i++) {
            Cand c = fin.get((start + i) % fin.size());
            if (placeCrystal(p, c, popped, now)) return true;
        }
        nextCrystalAt = now + 8;
        return false;
    }

    private boolean placeCrystal(Player p, Cand c, boolean popped, long now) {
        World w = p.getWorld();
        Block b = w.getBlockAt(c.x, c.y, c.z);
        Traps tr = mgr.traps();
        boolean placedObi = false;
        if (c.needObi) {
            if (tr == null || !tr.set(b, Material.OBSIDIAN, now + mgr.trapTicks("crystal-obsidian-seconds", 8))) return false;
            placedObi = true;
        }
        Location cl = new Location(w, c.x + 0.5, c.y + 1.0, c.z + 0.5);
        EnderCrystal cr;
        try {
            cr = w.spawn(cl, EnderCrystal.class, e -> {
                e.setShowingBottom(false);
                mgr.tag(e);
            });
        } catch (Throwable t) {
            if (placedObi && tr != null) tr.release(b); // วางคริสตัลไม่สำเร็จ → คืนออบซิเดียน
            return false;
        }
        mgr.trackEntity(cr, now + 60);
        crystals--;
        updateConsumables(p);
        selectSlot(p, S_CRYSTAL);
        npc.faceLocation(cl.clone().add(0, 0.5, 0));
        p.swingMainHand();
        if (placedObi) w.playSound(cl, Sound.BLOCK_STONE_PLACE, 1f, 1f);
        int delay = Math.max(1, popped ? 1 : spec.crystalDelay);
        crystalBusyUntil = now + delay + 2;
        nextCrystalAt = now + spec.crystalCd;
        final EnderCrystal fcr = cr;
        Bukkit.getScheduler().runTaskLater(mgr.plugin(), () -> detonateCrystal(fcr), delay);
        return true;
    }

    /** ตีคริสตัล = ระเบิด power 6 โดยมีบอทเป็นต้นเหตุ (บล็อกไม่พังตาม config crystal-block-damage) */
    private void detonateCrystal(EnderCrystal cr) {
        if (!cr.isValid()) return;
        Location l = cr.getLocation();
        cr.remove();
        Player p = player();
        if (p == null || p.isDead() || !npc.isSpawned()) return;
        if (l.getWorld() != p.getWorld() || l.distanceSquared(p.getEyeLocation()) > 8.0 * 8.0) return;
        p.swingMainHand();
        boolean grief = mgr.plugin().getConfig().getBoolean("crystal-block-damage", false);
        l.getWorld().createExplosion(l, 6.0f, false, grief, p);
        if (!eating) selectSlot(p, S_SWORD);
    }

    // ---------------------------------------------------------------- CUTPVP: TNT minecart + flame bow

    /** วางราง + รถรางระเบิดรอบเท้าศัตรู แล้วนัดจุด (ไฟแช็ก หรือยิงธนูไฟถ้าระดับยาก) */
    private boolean tryCart(Player p, Player e, double dist, long now) {
        if (carts <= 0 || eating || now < nextCartAt || !pendingCarts.isEmpty() || dist < 2.5 || dist > 7.5) return false;
        if (rnd.nextDouble() > 0.45) return false;
        World w = p.getWorld();
        if (e.getWorld() != w) return false;
        Traps tr = mgr.traps();
        Location eye = p.getEyeLocation();
        Block feet = e.getLocation().getBlock();
        int[][] offs = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        int want = Math.min(spec.cartCount, carts);
        long exp = now + mgr.trapTicks("cart-rail-seconds", 10);
        List<ExplosiveMinecart> placed = new ArrayList<>();
        for (int[] o : offs) {
            if (placed.size() >= want) break;
            Block b = feet.getRelative(o[0], 0, o[1]);
            if (!b.getType().isAir() || !b.getRelative(BlockFace.DOWN).getType().isSolid()) continue;
            Location c = b.getLocation().add(0.5, 0.0, 0.5);
            if (c.distanceSquared(eye) > 5.6 * 5.6 || c.distanceSquared(p.getLocation()) < 2.0 * 2.0) continue;
            if (tr != null && !tr.set(b, Material.RAIL, exp, false)) continue;
            try {
                ExplosiveMinecart cart = w.spawn(c.clone().add(0, 0.0625, 0), ExplosiveMinecart.class, cm -> mgr.tag(cm));
                mgr.trackEntity(cart, exp);
                placed.add(cart);
            } catch (Throwable t) {
                if (tr != null) tr.release(b);
            }
        }
        if (placed.isEmpty()) {
            nextCartAt = now + 40;
            return false;
        }
        carts -= placed.size();
        updateConsumables(p);
        nextCartAt = now + spec.cartCd + rnd.nextInt(60);
        pendingCarts.clear();
        pendingCarts.addAll(placed);
        cartIgniteAt = now + (spec.cartArrowIgnite ? 2 : 8);
        selectSlot(p, S_CART);
        npc.faceLocation(placed.get(0).getLocation());
        p.swingMainHand();
        w.playSound(placed.get(0).getLocation(), Sound.BLOCK_METAL_PLACE, 1f, 1f);
        return true;
    }

    private void igniteCarts(Player p, long now) {
        pendingCarts.removeIf(c -> !c.isValid());
        if (pendingCarts.isEmpty()) return;
        Location eye = p.getEyeLocation();
        ExplosiveMinecart near = null;
        double nd = Double.MAX_VALUE;
        for (ExplosiveMinecart c : pendingCarts) {
            if (c.getWorld() != p.getWorld()) continue;
            double d = c.getLocation().distanceSquared(eye);
            if (d < nd) {
                nd = d;
                near = c;
            }
        }
        if (near == null) {
            pendingCarts.clear();
            return;
        }
        double d = Math.sqrt(nd);
        // ระดับยาก: ยิงธนูไฟใส่รถ = ระเบิดทันที ศัตรูไม่มีเวลาหนี
        if (spec.cartArrowIgnite && d >= 3.0 && d <= 30 && p.hasLineOfSight(near)
                && shootArrow(p, near.getLocation().add(0, 0.4, 0), 0)) {
            pendingCarts.clear();
            return;
        }
        if (d <= 4.6) {
            selectSlot(p, S_FLINT);
            npc.faceLocation(near.getLocation());
            p.swingMainHand();
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1f);
            for (ExplosiveMinecart c : pendingCarts) {
                if (c.getWorld() == p.getWorld() && c.getLocation().distanceSquared(eye) <= 5.2 * 5.2) {
                    igniteCart(p, c, spec.cartFuse);
                }
            }
            cartFleeFrom = near.getLocation();
            cartFleeUntil = now + spec.cartFuse + 6;
            pendingCarts.clear();
            return;
        }
        if (now - cartIgniteAt > 60) pendingCarts.clear();
    }

    /** จุดรถระเบิด (ไฟแช็ก) ตั้งฟิวส์เอง — ถ้า API ไม่รองรับ ใช้ระเบิดหน่วงเวลาแทน */
    private void igniteCart(Player p, ExplosiveMinecart c, int fuse) {
        try {
            ExplosiveMinecart.class.getMethod("ignite").invoke(c);
            ExplosiveMinecart.class.getMethod("setFuseTicks", int.class).invoke(c, fuse);
            return;
        } catch (Throwable ignored) {
        }
        Bukkit.getScheduler().runTaskLater(mgr.plugin(), () -> {
            if (!c.isValid()) return;
            Location l = c.getLocation();
            c.remove();
            Player bot = player();
            boolean grief = mgr.plugin().getConfig().getBoolean("cart-block-damage", false);
            if (bot != null && bot.getWorld() == l.getWorld()) l.getWorld().createExplosion(l, 4.0f, false, grief, bot);
        }, Math.max(1, fuse));
    }

    /** ยิงธนู/หน้าไม้ไฟใส่ศัตรูระยะไกล (เล็งนำเป้า + ชดเชยแรงโน้มถ่วง) */
    private boolean tryShoot(Player p, Player e, double dist, long now) {
        if (now < nextShotAt || dist < 8 || dist > 42 || !pendingCarts.isEmpty() || !onGround(p)) return false;
        if (e.getWorld() != p.getWorld() || !p.hasLineOfSight(e)) return false;
        double lead = Math.hypot(e.getLocation().getX() - p.getLocation().getX(), e.getLocation().getZ() - p.getLocation().getZ())
                / (3.0 * 0.9);
        Location dest = e.getLocation().add(enemyVx * lead, 1.0, enemyVz * lead);
        nextShotAt = now + spec.bowCd + (useCrossbow ? 6 : 0) + rnd.nextInt(10);
        return shootArrow(p, dest, spec.bowAcc);
    }

    private boolean shootArrow(Player p, Location dest, double spread) {
        Vector v = Ballistics.aimArrow(p.getEyeLocation(), dest, 3.0);
        if (v == null) return false;
        if (spread > 0) {
            double sp = Math.toRadians(spread) * 3.0;
            v.add(new Vector(rnd.nextGaussian() * sp, rnd.nextGaussian() * sp * 0.6, rnd.nextGaussian() * sp));
        }
        selectSlot(p, S_BOW);
        npc.faceLocation(dest);
        p.swingMainHand();
        Arrow a = p.launchProjectile(Arrow.class, v);
        a.setFireTicks(200);
        a.setVisualFire(true);
        a.setCritical(true);
        a.setDamage(spec.arrowDamage);
        a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
        return true;
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
        if (pearlForSmash) {
            pearlForSmash = false;
            if (mgr.now() - pearlThrownAt < 200) pearlSmashUntil = mgr.now() + 24;
        }
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
                pearlForSmash = false; // เพิร์ลหนี ไม่ใช่เพิร์ลเข้าหาแล้วทุบ
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
            // เนิฟ: เหลือผลสั้นลง ไม่อึดเกินไป
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 600, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 1));
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
