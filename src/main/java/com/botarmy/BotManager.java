package com.botarmy;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** จัดการกองทัพทั้งหมด: เสก, ลาดตระเวน, เลือกเป้า, จัดแถว */
public final class BotManager {

    public enum Mode { AUTO, PATROL, ATTACK }

    private final BotArmyPlugin plugin;
    private NPCRegistry registry;
    private final List<ArmyBot> bots = new ArrayList<>();
    private final Map<UUID, ArmyBot> byEntity = new HashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    private final Random rnd = new Random();

    private long now = 0;
    private Location home;
    private UUID owner;
    private Location patrolPoint;
    private long nextPatrolAt, pauseUntil;
    private List<Player> enemies = new ArrayList<>();
    private Player focus;
    private ArmyBot leader;
    private Mode mode = Mode.AUTO;
    private boolean frozenAll;
    private BukkitTask task;
    private Traps traps;
    private UUID killTarget;
    private Player alertTarget;
    private long alertUntil;
    private int active;

    /** เอนทิตีชั่วคราวที่บอทสร้าง (คริสตัล/รถระเบิด) — ลบทิ้งเองเมื่อหมดเวลา กันค้างในโลก */
    private record Temp(Entity e, long expire) {}

    private final List<Temp> temps = new ArrayList<>();
    private final Map<UUID, Long> popUntil = new HashMap<>();
    private NamespacedKey tagKey;

    BotManager(BotArmyPlugin plugin) {
        this.plugin = plugin;
    }

    /** ติดป้ายว่าเอนทิตีนี้เป็นของบอท */
    void tag(Entity e) {
        try {
            e.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
        } catch (Throwable ignored) {
        }
    }

    boolean isBotEntity(Entity e) {
        if (e == null || tagKey == null) return false;
        try {
            return e.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE);
        } catch (Throwable t) {
            return false;
        }
    }

    void trackEntity(Entity e, long expireAt) {
        if (e != null) temps.add(new Temp(e, expireAt));
    }

    /** ผู้เล่นเพิ่งแตกโทเทม → บอท CPVP ระดับสูงรีบวางคริสตัลซ้ำ */
    void notePop(Player p) {
        popUntil.put(p.getUniqueId(), now + 60);
    }

    boolean recentlyPopped(Player p) {
        Long u = popUntil.get(p.getUniqueId());
        return u != null && u > now;
    }

    private void tickTemps() {
        if (!popUntil.isEmpty() && now % 200 == 0) popUntil.values().removeIf(u -> u <= now);
        if (temps.isEmpty()) return;
        for (Iterator<Temp> it = temps.iterator(); it.hasNext(); ) {
            Temp t = it.next();
            if (!t.e().isValid()) {
                it.remove();
            } else if (now >= t.expire()) {
                t.e().remove();
                it.remove();
            }
        }
    }

    private void clearTemps() {
        for (Temp t : temps) {
            try {
                t.e().remove();
            } catch (Throwable ignored) {
            }
        }
        temps.clear();
        popUntil.clear();
    }

    BotArmyPlugin plugin() { return plugin; }
    long now() { return now; }
    ArmyBot leader() { return leader; }
    Location home() { return home; }
    Mode mode() { return mode; }
    boolean isFrozen() { return frozenAll; }
    int size() { return bots.size(); }
    int activeCount() { return active; }
    Traps traps() { return trapsOn() ? traps : null; }
    boolean trapsOn() { return traps != null && plugin.getConfig().getBoolean("traps-enabled", true); }
    long trapTicks(String key, int defSeconds) { return plugin.getConfig().getInt(key, defSeconds) * 20L; }

    void start() {
        registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        traps = new Traps();
        tagKey = new NamespacedKey(plugin, "botent");
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(), 20L, 2L);
    }

    void shutdown() {
        if (task != null) task.cancel();
        clear();
        clearTemps();
        if (traps != null) traps.clearAll();
        if (registry != null) registry.deregisterAll();
    }

    ArmyBot get(Entity e) {
        return e == null ? null : byEntity.get(e.getUniqueId());
    }

    // ------------------------------------------------------------ commands

    int spawn(Player owner, KitType kitType, Difficulty diff, boolean totem, boolean totem99, int count) {
        return spawn(owner, kitType, Spec.of(kitType, diff, totem, totem99), count);
    }

    int spawn(Player owner, KitType kitType, Spec spec, int count) {
        this.owner = owner.getUniqueId();
        home = owner.getLocation().clone();
        patrolPoint = null;
        float yaw = home.getYaw();
        Vector fwd = fwd(yaw), right = right(yaw);
        int cols = Math.max(1, plugin.getConfig().getInt("formation-columns", 5));
        boolean showTab = plugin.getConfig().getBoolean("show-in-tablist", false);
        List<String> skins = plugin.getConfig().getStringList("skins");
        int made = 0;

        for (int i = 0; i < count; i++) {
            int row = i / cols, col = i % cols;
            double r = (col - (cols - 1) / 2.0) * 1.9;
            double f = 4 + row * 2.0;
            Location loc = home.clone().add(fwd.clone().multiply(f)).add(right.clone().multiply(r));
            loc = safeGround(loc);
            loc.setYaw(yaw);
            loc.setPitch(0);

            String name = BotNames.generate(rnd, usedNames);
            NPC npc = registry.createNPC(EntityType.PLAYER, name);
            npc.setProtected(false);
            npc.data().set(NPC.Metadata.REMOVE_FROM_PLAYERLIST, !showTab);
            npc.getNavigator().getDefaultParameters()
                    .range(150f)
                    .speedModifier(spec.speed)
                    .distanceMargin(1.8);
            if (!skins.isEmpty()) {
                try {
                    npc.getOrAddTrait(SkinTrait.class).setSkinName(skins.get(rnd.nextInt(skins.size())));
                } catch (Throwable t) {
                    plugin.getLogger().warning("skin error: " + t.getMessage());
                }
            }

            ArmyBot b = new ArmyBot(this, npc, name, kitType, spec);
            if (!npc.spawn(loc)) {
                npc.destroy();
                continue;
            }
            b.hadSpawned = true;
            b.frozen = frozenAll;
            bots.add(b);
            if (b.uuid() != null) byEntity.put(b.uuid(), b);
            made++;
            announce("multiplayer.player.joined", name, made - 1);
        }
        return made;
    }

    int clear() {
        int n = bots.size();
        int i = 0;
        for (ArmyBot b : bots) {
            if (!b.leftAnnounced) announce("multiplayer.player.left", b.name, i++);
            b.leftAnnounced = true;
            b.destroy();
        }
        bots.clear();
        byEntity.clear();
        usedNames.clear();
        leader = null;
        frozenAll = false;
        killTarget = null;
        alertTarget = null;
        clearTemps();
        if (traps != null) traps.clearAll();
        return n;
    }

    /** แจ้ง "xxx joined the game" / "xxx left the game" เหมือนผู้เล่นจริง (ทยอยส่งทีละตัว) */
    private void announce(String key, String name, int index) {
        if (!plugin.isEnabled() || !plugin.getConfig().getBoolean("broadcast-join-leave", true)) return;
        Component msg = Component.translatable(key, NamedTextColor.YELLOW, Component.text(name));
        long delay = Math.min(index, 40) * 3L;
        if (delay == 0) {
            Bukkit.broadcast(msg);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.broadcast(msg), delay);
        }
    }

    boolean toggleFrozen() {
        frozenAll = !frozenAll;
        for (ArmyBot b : bots) b.setFrozen(frozenAll);
        return frozenAll;
    }

    void setMode(Mode m) { this.mode = m; }

    // ------------------------------------------------------------ kill squad

    /**
     * ตั้งเป้ารุม: ถ้าบอทมี 1-2 ตัว ไปรุมทั้งหมด, ถ้าเกิน 2 ตัว แบ่งครึ่งไปรุม (ที่เหลือทำงานตามปกติ)
     * @return จำนวนบอทที่ไปรุม
     */
    int setKillTarget(Player t) {
        List<ArmyBot> list = new ArrayList<>();
        for (ArmyBot b : bots) {
            b.killer = false;
            if (b.isSpawned() && !b.frozen) list.add(b);
        }
        killTarget = t.getUniqueId();
        int n = list.size();
        int c = 0;
        for (int i = 0; i < n; i++) {
            if (n <= 2 || i % 2 == 0) {
                list.get(i).killer = true;
                c++;
            }
        }
        return c;
    }

    void clearKill() {
        killTarget = null;
        for (ArmyBot b : bots) b.killer = false;
    }

    boolean hasKillTarget() { return killTarget != null; }

    private Player killTargetPlayer() {
        if (killTarget == null) return null;
        Player t = Bukkit.getPlayer(killTarget);
        if (t == null || t.isDead() || !t.isValid()) return null;
        GameMode gm = t.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return null;
        return t;
    }

    /** บอทตัวไหนโดนผู้เล่นตี ทั้งทัพช่วยกันหันมาจัดการคนนั้น (~8 วิ) */
    void alert(Player attacker) {
        if (attacker == null || mode == Mode.PATROL || !enemies.contains(attacker)) return;
        alertTarget = attacker;
        alertUntil = now + 160;
    }

    // ------------------------------------------------------------ main loop

    private void tick() {
        now += 2;
        byEntity.clear();
        if (traps != null) traps.tick(now);
        tickTemps();
        if (killTarget != null) {
            Player kt = Bukkit.getPlayer(killTarget);
            if (kt == null || kt.isDead()) {
                Player o = owner == null ? null : Bukkit.getPlayer(owner);
                if (o != null && kt != null) plugin.msg(o, "&a☠ รุมเสร็จแล้ว: &f" + kt.getName());
                clearKill();
            }
        }
        int respawn = plugin.getConfig().getInt("respawn-seconds", 0) * 20;

        for (Iterator<ArmyBot> it = bots.iterator(); it.hasNext(); ) {
            ArmyBot b = it.next();
            if (b.isSpawned()) {
                b.hadSpawned = true;
                b.respawnAt = 0;
                byEntity.put(b.uuid(), b);
            } else if (b.hadSpawned) {
                // บอทตาย: ประกาศ "left the game" แค่ครั้งเดียว (ไม่มีข้อความตายซ้อน)
                if (!b.leftAnnounced) {
                    b.leftAnnounced = true;
                    if (plugin.getConfig().getBoolean("announce-leave-on-death", true)) {
                        announce("multiplayer.player.left", b.name, 0);
                    }
                }
                if (respawn > 0 && home != null) {
                    if (b.respawnAt == 0) b.respawnAt = now + respawn;
                    if (now >= b.respawnAt) {
                        Location base = (leader != null && leader.player() != null) ? leader.player().getLocation() : home;
                        if (b.respawn(safeGround(base))) {
                            b.leftAnnounced = false;
                            announce("multiplayer.player.joined", b.name, 0);
                            byEntity.put(b.uuid(), b);
                        } else {
                            b.respawnAt = now + 100;
                        }
                    }
                } else {
                    b.destroy();
                    usedNames.remove(b.name.toLowerCase());
                    it.remove();
                }
            }
        }
        if (bots.isEmpty()) {
            leader = null;
            return;
        }

        collectEnemies();
        electLeader();
        computeFocus();

        for (ArmyBot b : new ArrayList<>(bots)) {
            try {
                b.tick(now);
            } catch (Throwable t) {
                if (now % 200 == 0) plugin.getLogger().warning("bot " + b.name + " error: " + t);
            }
        }
    }

    private void collectEnemies() {
        boolean targetOwner = plugin.getConfig().getBoolean("target-owner", true);
        List<Player> list = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            GameMode gm = pl.getGameMode();
            if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) continue;
            if (pl.isDead() || !pl.isValid()) continue;
            if (pl.hasMetadata("NPC")) continue; // NPC ของปลั๊กอินอื่น ไม่ใช่ผู้เล่นจริง
            if (pl.hasPermission("botarmy.ignore")) continue;
            if (!targetOwner && pl.getUniqueId().equals(owner)) continue;
            if (byEntity.containsKey(pl.getUniqueId())) continue;
            list.add(pl);
        }
        enemies = list;
    }

    private void electLeader() {
        leader = null;
        int idx = 0;
        active = 0;
        for (ArmyBot b : bots) {
            if (!b.isSpawned()) continue;
            active++;
            if (leader == null && !b.frozen) {
                leader = b;
                b.slot = 0;
            } else {
                b.slot = ++idx;
            }
        }
    }

    private void computeFocus() {
        focus = null;
        if (mode == Mode.PATROL) return;
        if (alertTarget != null) {
            if (now < alertUntil && alertTarget.isOnline() && !alertTarget.isDead() && enemies.contains(alertTarget)) {
                focus = alertTarget;
                return;
            }
            alertTarget = null;
        }
        double range = plugin.getConfig().getDouble("detect-range", 45);
        double best = mode == Mode.ATTACK ? Double.MAX_VALUE : range * range;
        for (ArmyBot b : bots) {
            Player p = b.player();
            if (p == null || b.frozen) continue;
            Location bl = p.getLocation();
            for (Player e : enemies) {
                if (e.getWorld() != bl.getWorld()) continue;
                double d = e.getLocation().distanceSquared(bl);
                if (d < best) {
                    best = d;
                    focus = e;
                }
            }
        }
    }

    /**
     * เลือกเป้าให้บอท (ฉลาดขึ้น):
     * 1) ทีมรุมตาม /bot kill  2) คนที่เพิ่งตีบอทตัวนี้ (ป้องกันตัว)
     * 3) ให้คะแนน = ระยะ - โบนัสคนเลือดน้อย - โบนัสเป้าเดิม (ไม่เปลี่ยนเป้าไปมา)  4) โฟกัสของทัพ
     */
    Player pickTarget(ArmyBot b, Player p) {
        if (b.frozen) return null;
        Location bl = p.getLocation();

        if (b.killer) {
            Player kt = killTargetPlayer();
            if (kt != null && kt.getWorld() == bl.getWorld()) return kt;
        }

        if (mode == Mode.PATROL) {
            Player la = b.lastAttacker();
            return (la != null && enemies.contains(la)) ? la : null;
        }

        Player la = b.lastAttacker();
        if (la != null && enemies.contains(la) && la.getWorld() == bl.getWorld()
                && la.getLocation().distanceSquared(bl) < 12 * 12) {
            return la;
        }

        double detect = plugin.getConfig().getDouble("detect-range", 45);
        Player cur = b.currentTarget();
        Player best = null;
        double bestScore = Double.MAX_VALUE, bestDist = 0;
        for (Player e : enemies) {
            if (e.getWorld() != bl.getWorld()) continue;
            double d = e.getLocation().distance(bl);
            double hp = Math.max(0, Math.min(1, e.getHealth() / maxHp(e)));
            double score = d - (1 - hp) * 6 - (e == cur ? 5 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = e;
                bestDist = d;
            }
        }
        if (focus != null && focus.getWorld() == bl.getWorld() && (best == null || bestDist > 10)) {
            return focus;
        }
        if (best == null) return null;
        return (mode == Mode.ATTACK || bestDist <= detect) ? best : null;
    }

    @SuppressWarnings("deprecation")
    private static double maxHp(Player p) {
        return Math.max(1, p.getMaxHealth());
    }

    // ------------------------------------------------------------ patrol / formation

    Location patrolPoint(Location from) {
        double radius = plugin.getConfig().getDouble("patrol-radius", 25);
        boolean pick = patrolPoint == null || patrolPoint.getWorld() != from.getWorld();
        if (!pick) {
            if (from.distanceSquared(patrolPoint) < 9) {
                if (pauseUntil == 0) pauseUntil = now + 40 + rnd.nextInt(60);
                else if (now >= pauseUntil) pick = true;
            } else if (now >= nextPatrolAt) {
                pick = true;
            }
        }
        if (pick) {
            Location base = home != null && home.getWorld() == from.getWorld() ? home : from;
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = 8 + rnd.nextDouble() * Math.max(1, radius - 8);
            Location l = base.clone().add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            l.setY(from.getY());
            patrolPoint = safeGround(l);
            nextPatrolAt = now + 400 + rnd.nextInt(300);
            pauseUntil = 0;
        }
        return patrolPoint;
    }

    Location formationPoint(ArmyBot b) {
        Player lp = leader == null ? null : leader.player();
        if (lp == null) return home;
        Location ll = lp.getLocation();
        int cols = Math.max(1, plugin.getConfig().getInt("formation-columns", 5));
        int idx = Math.max(0, b.slot - 1);
        int row = idx / cols + 1, col = idx % cols;
        double r = (col - (cols - 1) / 2.0) * 2.0;
        double back = row * 2.2;
        float yaw = ll.getYaw();
        return ll.clone().add(right(yaw).multiply(r)).subtract(fwd(yaw).multiply(back));
    }

    private static Vector fwd(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad));
    }

    private static Vector right(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.cos(rad), 0, -Math.sin(rad));
    }

    Location safeGround(Location l) {
        Location c = l.clone();
        World w = c.getWorld();
        if (w == null) return c;
        int x = c.getBlockX(), z = c.getBlockZ(), y = c.getBlockY();
        for (int i = 0; i < 8 && w.getBlockAt(x, y, z).getType().isSolid(); i++) y++;
        for (int i = 0; i < 10 && y > w.getMinHeight() && !w.getBlockAt(x, y - 1, z).getType().isSolid(); i++) y--;
        c.setY(y);
        return c;
    }
}
